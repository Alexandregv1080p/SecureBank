package com.securebank.fx.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.fx.domain.FxOperation;
import com.securebank.fx.domain.FxRate;
import com.securebank.fx.domain.FxRateChangedException;
import com.securebank.fx.domain.FxSide;
import com.securebank.fx.domain.FxWallet;
import com.securebank.limit.application.LimitUsage;
import com.securebank.limit.domain.LimitType;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Câmbio simulado. Comprar e vender mexem no saldo em reais da conta E na carteira em moeda estrangeira na MESMA
 * transação (com lançamento, recibo, auditoria e evento no outbox). A cotação é sempre a do servidor: o cliente manda a
 * que viu e, se mudou, a operação é recusada (FX_RATE_CHANGED) em vez de executar por um preço que ele não aceitou.
 * A compra consome o limite de câmbio da conta.
 */
@Service
@Transactional
public class FxApplicationService {

    /** Carteira (possivelmente zerada) de uma moeda suportada. */
    public record WalletView(Currency currency, Money balance) {}

    private final FxRateRepository rates;
    private final FxWalletRepository wallets;
    private final FxOperationRepository operations;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final TransactionRepository transactions;
    private final LimitUsage limitUsage;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public FxApplicationService(FxRateRepository rates, FxWalletRepository wallets, FxOperationRepository operations,
            AccountApplicationService accountService, AccountRepository accounts, CustomerApplicationService customers,
            TransactionRepository transactions, LimitUsage limitUsage, BankTime time, AuditService audit,
            OutboxService outbox, TransactionalRetry retry) {
        this.rates = rates;
        this.wallets = wallets;
        this.operations = operations;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.limitUsage = limitUsage;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    @Transactional(readOnly = true)
    public List<FxRate> rates() {
        return rates.findAll();
    }

    /** Uma carteira por moeda suportada (as que o cliente nunca usou aparecem zeradas). */
    @Transactional(readOnly = true)
    public List<WalletView> wallets(CustomerId requester) {
        var mine = wallets.findByCustomer(requester);
        return rates.findAll().stream().map(rate -> mine.stream()
                .filter(w -> w.balance().currency().equals(rate.currency())).findFirst()
                .map(w -> new WalletView(rate.currency(), w.balance()))
                .orElseGet(() -> new WalletView(rate.currency(), Money.zero(rate.currency())))).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<FxOperation> history(CustomerId requester, int page, int size) {
        PageResult.validate(page, size);
        return operations.findByCustomer(requester, page, size);
    }

    /** Roda sem transação própria: abre uma por tentativa e repete em conflito de versão. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FxOperation buy(CustomerId requester, AccountId accountId, String currency, BigDecimal amount,
            BigDecimal quotedRate) {
        return retry.execute(() -> doBuy(requester, accountId, currency, amount, quotedRate));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FxOperation sell(CustomerId requester, AccountId accountId, String currency, BigDecimal amount,
            BigDecimal quotedRate) {
        return retry.execute(() -> doSell(requester, accountId, currency, amount, quotedRate));
    }

    private FxOperation doBuy(CustomerId requester, AccountId accountId, String currency, BigDecimal amount,
            BigDecimal quotedRate) {
        customers.requireActive(requester);
        Account account = accountService.findOwned(requester, accountId);
        FxRate rate = rate(currency);
        if (quotedRate.compareTo(rate.ask()) != 0) {
            throw new FxRateChangedException();
        }
        Money foreign = new Money(amount, rate.currency());
        Money cost = rate.buyCost(foreign);
        Instant now = time.now();
        UUID operationId = UUID.randomUUID();

        limitUsage.of(account.id(), LimitType.FX, Money.BRL).check(cost);
        Transaction debit = account.buyForeignCurrency(cost, reference(operationId), now); // conta ativa e saldo
        FxWallet wallet = wallets.find(requester, rate.currency().getCurrencyCode())
                .orElseGet(() -> FxWallet.open(requester, rate.currency(), now));
        wallet.credit(foreign, now);

        return finish(requester, account, wallet, debit, new FxOperation(operationId, requester, account.id(),
                FxSide.BUY, foreign, rate.ask(), cost, now), AuditEvent.FX_BOUGHT, "FxBought");
    }

    private FxOperation doSell(CustomerId requester, AccountId accountId, String currency, BigDecimal amount,
            BigDecimal quotedRate) {
        customers.requireActive(requester);
        Account account = accountService.findOwned(requester, accountId);
        FxRate rate = rate(currency);
        if (quotedRate.compareTo(rate.bid()) != 0) {
            throw new FxRateChangedException();
        }
        Money foreign = new Money(amount, rate.currency());
        Money proceeds = rate.sellProceeds(foreign);
        Instant now = time.now();
        UUID operationId = UUID.randomUUID();

        FxWallet wallet = wallets.find(requester, rate.currency().getCurrencyCode())
                .orElseThrow(com.securebank.fx.domain.InsufficientFxFundsException::new);
        wallet.debit(foreign, now);
        Transaction credit = account.sellForeignCurrency(proceeds, reference(operationId), now);

        return finish(requester, account, wallet, credit, new FxOperation(operationId, requester, account.id(),
                FxSide.SELL, foreign, rate.bid(), proceeds, now), AuditEvent.FX_SOLD, "FxSold");
    }

    private FxOperation finish(CustomerId requester, Account account, FxWallet wallet, Transaction ledger,
            FxOperation op, AuditEvent event, String eventType) {
        accounts.save(account);
        wallets.save(wallet);
        transactions.save(ledger);
        operations.save(op);
        audit.record(AuditEntry.of(event).account(account.id()).transaction(ledger.id()).detail(op.id().toString()));
        outbox.record(eventType, "FxOperation", op.id().toString(),
                Map.of("operationId", op.id().toString(), "accountId", account.id().toString(),
                        "currency", op.foreignAmount().currency().getCurrencyCode(),
                        "foreignAmount", op.foreignAmount().amount().toPlainString(),
                        "brlAmount", op.brlAmount().amount().toPlainString()));
        return op;
    }

    private FxRate rate(String currency) {
        return rates.findByCurrency(currency == null ? "" : currency.toUpperCase())
                .orElseThrow(() -> ApplicationException.notFound("Currency"));
    }

    /** Referência do lançamento no extrato (cabe nos 64 caracteres da coluna). */
    private static String reference(UUID operationId) {
        return "fx:" + operationId;
    }
}
