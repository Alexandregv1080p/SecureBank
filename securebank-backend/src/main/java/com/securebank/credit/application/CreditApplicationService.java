package com.securebank.credit.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.card.application.CardApplicationService;
import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardKind;
import com.securebank.credit.application.CreditRepository.Totals;
import com.securebank.credit.domain.CreditLimits;
import com.securebank.credit.domain.CreditPolicy;
import com.securebank.credit.domain.CreditPurchase;
import com.securebank.credit.domain.CreditTerms;
import com.securebank.credit.domain.Invoice;
import com.securebank.credit.domain.InvoiceStatus;
import com.securebank.credit.domain.Refinance;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.DomainException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cartão de crédito. O cartão em si (número, CVV, bloquear, cancelar) é o mesmo do débito; aqui ficam o limite, as compras
 * parceladas, as faturas e o pagamento delas. Compras e pagamentos do mesmo cartão se enfileiram pela trava das condições,
 * então o limite não estoura em corrida. Cartão de outro dono e de débito dão 404 neste módulo.
 */
@Service
@Transactional
public class CreditApplicationService {

    /** Visão geral: limite, uso e a fatura do mês. */
    public record Summary(Card card, CreditTerms terms, Money used, Money available, Invoice current, boolean overdue) {}

    /** O parcelamento feito: a fatura já quitada e a "compra" parcelada que ficou no lugar. */
    public record Refinanced(Invoice invoice, CreditPurchase purchase, Refinance.Plan plan) {}

    /** O que o cliente pode escolher para parcelar uma fatura. */
    public record Offer(Invoice invoice, List<Refinance.Plan> options) {}

    public record Detail(Invoice invoice, List<CreditRepository.Item> items, List<CreditRepository.Charge> charges,
            List<CreditRepository.Payment> payments) {}

    private final CreditRepository credit;
    private final CreditBillingService billing;
    private final CardApplicationService cards;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public CreditApplicationService(CreditRepository credit, CreditBillingService billing, CardApplicationService cards, AccountApplicationService accountService,
            AccountRepository accounts, CustomerApplicationService customers, TransactionRepository transactions, BankTime time,
            AuditService audit, OutboxService outbox, TransactionalRetry retry) {
        this.credit = credit;
        this.billing = billing;
        this.cards = cards;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    /** Emite o cartão de crédito já com o limite inicial e o vencimento escolhido (5, 10, 15, 20 ou 25). */
    public Card issue(CustomerId requester, AccountId accountId, int dueDay) {
        if (!CreditTerms.DUE_DAYS.contains(dueDay)) {
            throw new InvalidValueException("The due day must be one of " + CreditTerms.DUE_DAYS);
        }
        Card card = cards.issue(requester, accountId, CardKind.CREDIT);
        credit.insert(CreditTerms.grant(card.id(), dueDay));
        return card;
    }

    /** Põe os encargos em dia antes de mostrar, então a fatura exibida é a de hoje. */
    public Summary summary(CustomerId requester, CardId id) {
        Card card = creditCard(requester, id);
        LocalDate today = time.today();
        CreditTerms terms = billing.accrueLocked(id, today);
        Map<YearMonth, Totals> totals = credit.totals(id, terms.limit().currency());
        Money used = used(totals, terms.limit().currency());
        YearMonth current = terms.invoiceFor(today);
        return new Summary(card, terms, used, CreditPolicy.available(terms, used), invoice(terms, current, totals, today),
                hasOverdue(terms, totals, today));
    }

    /** Faturas com parcela ou pagamento, da mais nova para a mais antiga, mais a fatura aberta de hoje (mesmo vazia). */
    public List<Invoice> invoices(CustomerId requester, CardId id) {
        creditCard(requester, id);
        LocalDate today = time.today();
        CreditTerms terms = billing.accrueLocked(id, today);
        Map<YearMonth, Totals> totals = credit.totals(id, terms.limit().currency());
        TreeMap<YearMonth, Invoice> byMonth = new TreeMap<>(Comparator.reverseOrder());
        totals.keySet().forEach(m -> byMonth.put(m, invoice(terms, m, totals, today)));
        YearMonth current = terms.invoiceFor(today);
        byMonth.computeIfAbsent(current, m -> invoice(terms, m, totals, today));
        return new ArrayList<>(byMonth.values());
    }

    public Detail invoiceDetail(CustomerId requester, CardId id, YearMonth month) {
        creditCard(requester, id);
        LocalDate today = time.today();
        CreditTerms terms = billing.accrueLocked(id, today);
        java.util.Currency currency = terms.limit().currency();
        Map<YearMonth, Totals> totals = credit.totals(id, currency);
        return new Detail(invoice(terms, month, totals, today), credit.items(id, month, currency), credit.charges(id, month, currency),
                credit.payments(id, month, currency));
    }

    @Transactional(readOnly = true)
    public PageResult<CreditPurchase> purchases(CustomerId requester, CardId id, int page, int size) {
        PageResult.validate(page, size);
        creditCard(requester, id);
        return credit.purchases(id, page, size, termsOf(id).limit().currency());
    }

    /** Compra à vista ou parcelada: só consome limite (o dinheiro sai da conta quando a fatura é paga). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreditPurchase purchase(CustomerId requester, CardId id, BigDecimal amount, String merchant, int installments) {
        AccountId accountId = null;
        try {
            Card known = cards.get(requester, id);
            accountId = known.accountId();
            return retry.execute(() -> doPurchase(requester, id, amount, merchant, installments));
        } catch (DomainException e) {
            failed(accountId, e.code());
            throw e;
        } catch (ApplicationException e) {
            failed(accountId, e.code());
            throw e;
        }
    }

    private void failed(AccountId accountId, String code) {
        if (accountId != null) {
            audit.recordIndependently(AuditEntry.of(AuditEvent.CREDIT_PURCHASE_FAILED).account(accountId).detail(code));
        }
    }

    private CreditPurchase doPurchase(CustomerId requester, CardId id, BigDecimal amount, String merchant, int installments) {
        customers.requireActive(requester);
        Card card = creditCard(requester, id);
        LocalDate today = time.today();
        card.ensureCanPurchase(today);
        CreditTerms terms = billing.accrueLocked(id, today);
        java.util.Currency currency = terms.limit().currency();
        Money total = new Money(amount, currency);
        CreditPurchase purchase = CreditPurchase.create(terms, total, merchant, installments, today, time.now());

        Map<YearMonth, Totals> totals = credit.totals(id, currency);
        CreditPolicy.ensureCanBuy(terms, used(totals, currency), total, hasOverdue(terms, totals, today));
        credit.save(purchase);
        audit.record(AuditEntry.of(AuditEvent.CREDIT_PURCHASE).account(card.accountId()).detail(card.id() + " | " + installments + "x"));
        outbox.record("CreditPurchaseCompleted", "Card", card.id().toString(), Map.of(
                "cardId", card.id().toString(), "accountId", card.accountId().toString(), "last4", card.last4(),
                "merchant", purchase.merchant(), "amount", total.amount().toPlainString(),
                "installments", String.valueOf(installments), "currency", currency.getCurrencyCode()));
        return purchase;
    }

    /** Paga (tudo ou parte de) uma fatura com o saldo da conta do cartão. */
    /** As opções de parcelamento de uma fatura fechada (parcela e total com juros). */
    public Offer refinanceOptions(CustomerId requester, CardId id, YearMonth month) {
        creditCard(requester, id);
        LocalDate today = time.today();
        CreditTerms terms = billing.accrueLocked(id, today);
        Invoice invoice = invoice(terms, month, credit.totals(id, terms.limit().currency()), today);
        Refinance.requireRefinanceable(invoice);
        return new Offer(invoice, Refinance.options(invoice.remaining()));
    }

    /** Parcela o que falta de uma fatura fechada: ela fica quitada e a dívida nova segue nas próximas faturas, com juros. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Refinanced refinance(CustomerId requester, CardId id, YearMonth month, int installments) {
        AccountId accountId = null;
        try {
            accountId = cards.get(requester, id).accountId();
            return retry.execute(() -> doRefinance(requester, id, month, installments));
        } catch (DomainException e) {
            failed(accountId, e.code());
            throw e;
        } catch (ApplicationException e) {
            failed(accountId, e.code());
            throw e;
        }
    }

    private Refinanced doRefinance(CustomerId requester, CardId id, YearMonth month, int installments) {
        customers.requireActive(requester);
        Card card = creditCard(requester, id);
        LocalDate today = time.today();
        CreditTerms terms = billing.accrueLocked(id, today);
        java.util.Currency currency = terms.limit().currency();
        Invoice invoice = invoice(terms, month, credit.totals(id, currency), today);
        Refinance.requireRefinanceable(invoice);
        Refinance.Plan plan = Refinance.plan(invoice.remaining(), installments);
        Instant now = time.now();
        CreditPurchase purchase = CreditPurchase.refinance(terms, plan, month, today, now);
        credit.savePayment(id, month, invoice.remaining(), null, "REFINANCE", now);
        credit.save(purchase);
        audit.record(AuditEntry.of(AuditEvent.CREDIT_INVOICE_REFINANCED).account(card.accountId())
                .detail(card.id() + " | " + month + " | " + installments + "x " + plan.installment().amount().toPlainString()));
        outbox.record("CreditInvoiceRefinanced", "Card", card.id().toString(), Map.of(
                "cardId", card.id().toString(), "accountId", card.accountId().toString(), "last4", card.last4(),
                "month", month.toString(), "installments", String.valueOf(installments),
                "installment", plan.installment().amount().toPlainString(), "total", plan.total().amount().toPlainString(),
                "currency", currency.getCurrencyCode()));
        return new Refinanced(invoice(terms, month, credit.totals(id, currency), today), purchase, plan);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Invoice payInvoice(CustomerId requester, CardId id, YearMonth month, BigDecimal amount) {
        AccountId accountId = null;
        try {
            accountId = cards.get(requester, id).accountId();
            return retry.execute(() -> doPay(requester, id, month, amount));
        } catch (DomainException e) {
            audit.recordIndependently(AuditEntry.of(AuditEvent.CREDIT_PURCHASE_FAILED).account(accountId).detail(e.code()));
            throw e;
        } catch (ApplicationException e) {
            if (accountId != null) {
                audit.recordIndependently(AuditEntry.of(AuditEvent.CREDIT_PURCHASE_FAILED).account(accountId).detail(e.code()));
            }
            throw e;
        }
    }

    private Invoice doPay(CustomerId requester, CardId id, YearMonth month, BigDecimal amount) {
        customers.requireActive(requester);
        Card card = creditCard(requester, id);
        LocalDate today = time.today();
        CreditTerms terms = billing.accrueLocked(id, today);
        java.util.Currency currency = terms.limit().currency();
        Money money = new Money(amount, currency);
        Invoice invoice = invoice(terms, month, credit.totals(id, currency), today);
        invoice.ensureCanPay(money);

        Account account = accountService.findOwned(requester, card.accountId());
        account.ensureCanDebit(money);
        Instant now = time.now();
        Transaction transaction = account.payCardInvoice(money, card.id() + " | " + month, now);
        accounts.save(account);
        transactions.save(transaction);
        credit.savePayment(id, month, money, transaction.id(), "CASH", now);
        audit.record(AuditEntry.of(AuditEvent.CREDIT_INVOICE_PAID).account(account.id()).transaction(transaction.id())
                .detail(card.id() + " | " + month));
        outbox.record("CreditInvoicePaid", "Card", card.id().toString(), Map.of(
                "cardId", card.id().toString(), "accountId", account.id().toString(), "last4", card.last4(),
                "month", month.toString(), "amount", money.amount().toPlainString(), "currency", currency.getCurrencyCode()));
        return invoice(terms, month, credit.totals(id, currency), today);
    }

    /** O cliente muda o limite do próprio cartão: diminui à vontade, aumenta até R$ 10.000,00 se está em dia. */
    public Summary changeLimit(CustomerId requester, CardId id, BigDecimal newLimit) {
        customers.requireActive(requester);
        Card card = creditCard(requester, id);
        LocalDate today = time.today();
        CreditTerms terms = billing.accrueLocked(id, today);
        java.util.Currency currency = terms.limit().currency();
        Money wanted = new Money(newLimit, currency);
        Map<YearMonth, Totals> totals = credit.totals(id, currency);
        boolean increase = wanted.isGreaterThan(terms.limit());
        if (increase) {
            card.ensureCanPurchase(today); // aumentar só com o cartão ativo e dentro da validade
        }
        CreditLimits.checkSelfService(terms, wanted, used(totals, currency), hasOverdue(terms, totals, today));
        applyLimit(card, terms, wanted, "customer");
        return summary(requester, id);
    }

    /** Decisão da equipe sobre o limite de um cartão (até R$ 100.000,00, nunca abaixo da dívida). */
    public Money changeLimitAsStaff(CardId id, BigDecimal newLimit) {
        Card card = cards.getAny(id);
        if (card.kind() != CardKind.CREDIT) {
            throw ApplicationException.notFound("Credit card");
        }
        CreditTerms terms = billing.accrueLocked(id, time.today());
        java.util.Currency currency = terms.limit().currency();
        Money wanted = new Money(newLimit, currency);
        CreditLimits.checkStaff(wanted, used(credit.totals(id, currency), currency));
        applyLimit(card, terms, wanted, "staff");
        return wanted;
    }

    private void applyLimit(Card card, CreditTerms terms, Money wanted, String by) {
        Money old = terms.limit();
        credit.saveLimit(terms.withLimit(wanted));
        audit.record(AuditEntry.of(AuditEvent.CREDIT_LIMIT_CHANGED).account(card.accountId())
                .detail(card.id() + " | " + old.amount().toPlainString() + " -> " + wanted.amount().toPlainString() + " | " + by));
        outbox.record("CreditLimitChanged", "Card", card.id().toString(), Map.of(
                "cardId", card.id().toString(), "accountId", card.accountId().toString(), "last4", card.last4(),
                "limit", wanted.amount().toPlainString(), "currency", wanted.currency().getCurrencyCode(), "by", by));
    }

    /** Cartões de crédito de um cliente, para a equipe: limite, uso e vencimento. */
    @Transactional(readOnly = true)
    public List<StaffView> creditCardsOf(CustomerId customer) {
        return cards.listOpenForStaff(customer).stream().filter(c -> c.kind() == CardKind.CREDIT).map(c -> {
            CreditTerms terms = termsOf(c.id());
            Money used = used(credit.totals(c.id(), terms.limit().currency()), terms.limit().currency());
            return new StaffView(c, terms, used, CreditPolicy.available(terms, used));
        }).toList();
    }

    public record StaffView(Card card, CreditTerms terms, Money used, Money available) {}

    private Card creditCard(CustomerId requester, CardId id) {
        Card card = cards.get(requester, id);
        if (card.kind() != CardKind.CREDIT) {
            throw ApplicationException.notFound("Credit card");
        }
        return card;
    }

    private CreditTerms termsOf(CardId id) {
        return credit.terms(id).orElseThrow(() -> ApplicationException.notFound("Credit card"));
    }

    private static Invoice invoice(CreditTerms terms, YearMonth month, Map<YearMonth, Totals> totals, LocalDate today) {
        java.util.Currency currency = terms.limit().currency();
        Totals t = totals.getOrDefault(month, new Totals(Money.zero(currency), Money.zero(currency), Money.zero(currency)));
        return Invoice.of(terms, month, t.principal(), t.charges(), t.paid(), today);
    }

    private static Money used(Map<YearMonth, Totals> totals, java.util.Currency currency) {
        return CreditRepository.sumOpen(totals, currency);
    }

    private static boolean hasOverdue(CreditTerms terms, Map<YearMonth, Totals> totals, LocalDate today) {
        return totals.keySet().stream().anyMatch(m -> invoice(terms, m, totals, today).blocksPurchases());
    }
}
