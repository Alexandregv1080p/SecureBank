package com.securebank.investment.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.investment.domain.Investment;
import com.securebank.investment.domain.InvestmentProduct;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvestmentId;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de investimento (renda fixa simulada). Aplicar e resgatar mexem no saldo da conta E na aplicação na MESMA
 * transação (com lançamento, auditoria e evento no outbox). Aplicação de outro dono é indistinguível de inexistente (404).
 */
@Service
@Transactional
public class InvestmentApplicationService {

    static final int MAX_ACTIVE_PER_CUSTOMER = 50;

    /** Aplicação com o valor que ela tem agora. */
    /** [paid]: quanto caiu na conta neste resgate (só na resposta do resgate; nulo ao consultar). */
    public record View(Investment investment, Investment.Valuation valuation, boolean canRedeem, Money paid) {}

    private final InvestmentRepository investments;
    private final InvestmentProductRepository products;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public InvestmentApplicationService(InvestmentRepository investments, InvestmentProductRepository products,
            AccountApplicationService accountService, AccountRepository accounts, CustomerApplicationService customers,
            TransactionRepository transactions, BankTime time, AuditService audit, OutboxService outbox,
            TransactionalRetry retry) {
        this.investments = investments;
        this.products = products;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    @Transactional(readOnly = true)
    public List<InvestmentProduct> products() {
        return products.findActive();
    }

    @Transactional(readOnly = true)
    public List<View> list(CustomerId requester) {
        Instant now = time.now();
        return investments.findByCustomer(requester).stream().map(i -> view(i, now, null)).toList();
    }

    @Transactional(readOnly = true)
    public View get(CustomerId requester, InvestmentId id) {
        return view(findOwned(requester, id), time.now(), null);
    }

    /** Roda sem transação própria: abre uma por tentativa e repete em conflito de versão. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public View apply(CustomerId requester, AccountId accountId, String productCode, BigDecimal amount) {
        return retry.execute(() -> doApply(requester, accountId, productCode, amount));
    }

    /** [amount] nulo resgata tudo; com valor, resgata esse LÍQUIDO (se for o total ou mais, resgata tudo). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public View redeem(CustomerId requester, InvestmentId id, BigDecimal amount) {
        return retry.execute(() -> doRedeem(requester, id, amount));
    }

    /** Avisa os vencimentos novos (uma vez cada). @return quantos foram avisados. */
    public int notifyMatured() {
        var matured = investments.claimMatured(time.now(), 100);
        for (var m : matured) {
            outbox.record("InvestmentMatured", "Investment", m.id().toString(),
                    Map.of("investmentId", m.id().toString(), "accountId", m.accountId().toString(),
                            "product", m.productName()));
        }
        return matured.size();
    }

    private View doApply(CustomerId requester, AccountId accountId, String productCode, BigDecimal amount) {
        customers.requireActive(requester);
        Account account = accountService.findOwned(requester, accountId);
        InvestmentProduct product = products.findByCode(productCode)
                .filter(InvestmentProduct::active)
                .orElseThrow(() -> ApplicationException.notFound("Product"));
        if (investments.countActiveByCustomer(requester) >= MAX_ACTIVE_PER_CUSTOMER) {
            throw ApplicationException.unprocessable("INVESTMENT_LIMIT_REACHED",
                    "You can have at most " + MAX_ACTIVE_PER_CUSTOMER + " active investments");
        }
        Money money = new Money(amount, account.balance().currency());
        Instant now = time.now();
        Investment investment = Investment.apply(requester, account.id(), product, money, now);

        Transaction debit = account.invest(money, reference(investment), now); // exige conta ativa e saldo
        accounts.save(account);
        investments.save(investment);
        transactions.save(debit);
        audit.record(AuditEntry.of(AuditEvent.INVESTMENT_APPLIED).account(account.id()).transaction(debit.id())
                .detail(investment.id().toString()));
        outbox.record("InvestmentApplied", "Investment", investment.id().toString(),
                payload(investment, money, product.name()));
        return view(investment, now, null);
    }

    private View doRedeem(CustomerId requester, InvestmentId id, BigDecimal amount) {
        customers.requireActive(requester);
        Investment investment = findOwned(requester, id);
        Account account = accountService.findOwned(requester, investment.accountId());
        Instant now = time.now();

        Money wanted = amount == null ? null : new Money(amount, account.balance().currency());
        Money net = investment.redeem(now, wanted); // recusa se já resgatada ou antes do vencimento
        Transaction credit = account.redeemInvestment(net, reference(investment), now);
        accounts.save(account);
        investments.save(investment);
        transactions.save(credit);
        audit.record(AuditEntry.of(AuditEvent.INVESTMENT_REDEEMED).account(account.id()).transaction(credit.id())
                .detail(investment.id().toString()));
        outbox.record("InvestmentRedeemed", "Investment", investment.id().toString(),
                payload(investment, net, investment.productName()));
        return view(investment, now, net);
    }

    private Investment findOwned(CustomerId requester, InvestmentId id) {
        return investments.findById(id).filter(i -> i.isOwnedBy(requester))
                .orElseThrow(() -> ApplicationException.notFound("Investment"));
    }

    private static View view(Investment i, Instant now, Money paid) {
        return new View(i, i.valuation(now), i.canRedeem(now), paid);
    }

    /** Referência do lançamento no extrato (cabe nos 64 caracteres da coluna). */
    private static String reference(Investment i) {
        return "invest:" + i.id();
    }

    /** Só ids, valores e o nome do PRODUTO (fixo do banco); nada de texto livre do usuário. */
    private static Map<String, Object> payload(Investment i, Money amount, String productName) {
        return Map.of("investmentId", i.id().toString(), "accountId", i.accountId().toString(),
                "amount", amount.amount().toPlainString(), "currency", amount.currency().getCurrencyCode(),
                "product", productName);
    }
}
