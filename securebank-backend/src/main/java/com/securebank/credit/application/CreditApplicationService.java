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
import com.securebank.credit.domain.CreditPolicy;
import com.securebank.credit.domain.CreditPurchase;
import com.securebank.credit.domain.CreditTerms;
import com.securebank.credit.domain.Invoice;
import com.securebank.credit.domain.InvoiceStatus;
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

    public record Detail(Invoice invoice, List<CreditRepository.Item> items, List<CreditRepository.Payment> payments) {}

    private final CreditRepository credit;
    private final CardApplicationService cards;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public CreditApplicationService(CreditRepository credit, CardApplicationService cards, AccountApplicationService accountService,
            AccountRepository accounts, CustomerApplicationService customers, TransactionRepository transactions, BankTime time,
            AuditService audit, OutboxService outbox, TransactionalRetry retry) {
        this.credit = credit;
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

    @Transactional(readOnly = true)
    public Summary summary(CustomerId requester, CardId id) {
        Card card = creditCard(requester, id);
        CreditTerms terms = termsOf(id);
        Map<YearMonth, Totals> totals = credit.totals(id, terms.limit().currency());
        LocalDate today = time.today();
        Money used = used(totals, terms.limit().currency());
        YearMonth current = terms.invoiceFor(today);
        return new Summary(card, terms, used, CreditPolicy.available(terms, used), invoice(terms, current, totals, today),
                hasOverdue(terms, totals, today));
    }

    /** Faturas com parcela ou pagamento, da mais nova para a mais antiga, mais a fatura aberta de hoje (mesmo vazia). */
    @Transactional(readOnly = true)
    public List<Invoice> invoices(CustomerId requester, CardId id) {
        creditCard(requester, id);
        CreditTerms terms = termsOf(id);
        Map<YearMonth, Totals> totals = credit.totals(id, terms.limit().currency());
        LocalDate today = time.today();
        TreeMap<YearMonth, Invoice> byMonth = new TreeMap<>(Comparator.reverseOrder());
        totals.keySet().forEach(m -> byMonth.put(m, invoice(terms, m, totals, today)));
        YearMonth current = terms.invoiceFor(today);
        byMonth.computeIfAbsent(current, m -> invoice(terms, m, totals, today));
        return new ArrayList<>(byMonth.values());
    }

    @Transactional(readOnly = true)
    public Detail invoiceDetail(CustomerId requester, CardId id, YearMonth month) {
        creditCard(requester, id);
        CreditTerms terms = termsOf(id);
        java.util.Currency currency = terms.limit().currency();
        Map<YearMonth, Totals> totals = credit.totals(id, currency);
        return new Detail(invoice(terms, month, totals, time.today()), credit.items(id, month, currency), credit.payments(id, month, currency));
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
        CreditTerms terms = credit.lockTerms(id).orElseThrow(() -> ApplicationException.notFound("Credit card"));
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
        CreditTerms terms = credit.lockTerms(id).orElseThrow(() -> ApplicationException.notFound("Credit card"));
        java.util.Currency currency = terms.limit().currency();
        LocalDate today = time.today();
        Money money = new Money(amount, currency);
        Invoice invoice = invoice(terms, month, credit.totals(id, currency), today);
        invoice.ensureCanPay(money);

        Account account = accountService.findOwned(requester, card.accountId());
        account.ensureCanDebit(money);
        Instant now = time.now();
        Transaction transaction = account.payCardInvoice(money, card.id() + " | " + month, now);
        accounts.save(account);
        transactions.save(transaction);
        credit.savePayment(id, month, money, transaction.id(), now);
        audit.record(AuditEntry.of(AuditEvent.CREDIT_INVOICE_PAID).account(account.id()).transaction(transaction.id())
                .detail(card.id() + " | " + month));
        outbox.record("CreditInvoicePaid", "Card", card.id().toString(), Map.of(
                "cardId", card.id().toString(), "accountId", account.id().toString(), "last4", card.last4(),
                "month", month.toString(), "amount", money.amount().toPlainString(), "currency", currency.getCurrencyCode()));
        return invoice(terms, month, credit.totals(id, currency), today);
    }

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
        Totals t = totals.getOrDefault(month, new Totals(Money.zero(currency), Money.zero(currency)));
        return Invoice.of(terms, month, t.total(), t.paid(), today);
    }

    private static Money used(Map<YearMonth, Totals> totals, java.util.Currency currency) {
        return CreditRepository.sumOpen(totals, currency);
    }

    private static boolean hasOverdue(CreditTerms terms, Map<YearMonth, Totals> totals, LocalDate today) {
        return totals.keySet().stream().anyMatch(m -> invoice(terms, m, totals, today).status() == InvoiceStatus.OVERDUE);
    }
}
