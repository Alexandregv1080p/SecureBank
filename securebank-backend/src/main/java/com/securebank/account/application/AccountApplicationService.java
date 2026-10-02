package com.securebank.account.application;

import com.securebank.account.domain.Account;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.account.domain.AccountType;
import com.securebank.account.domain.Branch;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.limit.application.LimitRepository;
import com.securebank.limit.application.LimitUsage;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de conta. Todo método recebe o cliente que está agindo e só enxerga contas dele:
 * conta inexistente e conta de outro dono são indistinguíveis (404), então ids não podem ser sondados (IDOR).
 */
@Service
@Transactional
public class AccountApplicationService {

    public static final Branch BRANCH = new Branch("0001");
    private static final Instant FOREVER = Instant.parse("2999-01-01T00:00:00Z");

    private final AccountRepository accounts;
    private final AccountNumberGenerator numbers;
    private final CustomerApplicationService customers;
    private final LimitRepository limits;
    private final LimitUsage limitUsage;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final TransactionalRetry retry;
    private final OutboxService outbox;

    public AccountApplicationService(AccountRepository accounts, AccountNumberGenerator numbers,
            CustomerApplicationService customers, LimitRepository limits, LimitUsage limitUsage,
            TransactionRepository transactions, BankTime time, AuditService audit, TransactionalRetry retry, OutboxService outbox) {
        this.accounts = accounts;
        this.numbers = numbers;
        this.customers = customers;
        this.limits = limits;
        this.limitUsage = limitUsage;
        this.transactions = transactions;
        this.time = time;
        this.audit = audit;
        this.retry = retry;
        this.outbox = outbox;
    }

    public Account open(CustomerId requester, AccountType type) {
        customers.requireActive(requester);
        Instant now = time.now();
        Account account = Account.open(requester, numbers.next(), BRANCH, type, Money.BRL, now);
        accounts.save(account);
        for (LimitType limitType : LimitType.values()) {
            limits.save(Limit.defaultFor(account.id(), limitType, now));
        }
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> list(CustomerId requester) {
        return accounts.findByCustomer(requester);
    }

    /** Conta do cliente ou 404 — também usada pelos casos de uso de transferência e pagamento. */
    @Transactional(readOnly = true)
    public Account findOwned(CustomerId requester, AccountId id) {
        return accounts.findById(id)
                .filter(account -> account.isOwnedBy(requester))
                .orElseThrow(() -> ApplicationException.notFound("Account"));
    }

    /** Roda sem transação própria: abre uma por tentativa e repete em conflito de versão. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transaction deposit(CustomerId requester, AccountId id, BigDecimal amount) {
        return retry.execute(() -> doDeposit(requester, id, amount));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transaction withdraw(CustomerId requester, AccountId id, BigDecimal amount) {
        return retry.execute(() -> doWithdraw(requester, id, amount));
    }

    private Transaction doDeposit(CustomerId requester, AccountId id, BigDecimal amount) {
        customers.requireActive(requester);
        Account account = findOwned(requester, id);
        Transaction transaction = account.deposit(moneyOf(account, amount), null, time.now());
        accounts.save(account);
        transactions.save(transaction);
        return transaction;
    }

    private Transaction doWithdraw(CustomerId requester, AccountId id, BigDecimal amount) {
        customers.requireActive(requester);
        Account account = findOwned(requester, id);
        Money money = moneyOf(account, amount);

        limitUsage.of(account.id(), LimitType.WITHDRAW, money.currency()).check(money);
        Transaction transaction = account.withdraw(money, null, time.now());
        accounts.save(account);
        transactions.save(transaction);
        return transaction;
    }

    @Transactional(readOnly = true)
    public PageResult<Transaction> statement(CustomerId requester, AccountId id, LocalDate from, LocalDate to,
            int page, int size) {
        Account account = findOwned(requester, id);
        Instant start = from == null ? Instant.EPOCH : time.startOfDay(from);
        Instant end = to == null ? FOREVER : time.startOfDay(to.plusDays(1));
        if (!start.isBefore(end)) {
            throw new InvalidValueException("'from' must not be after 'to'");
        }
        PageResult.validate(page, size);
        return transactions.findStatement(account.id(), start, end, page, size);
    }

    /** Bloqueio administrativo (permissão MANAGE_ACCOUNTS): qualquer conta, não só as do chamador. */
    public Account block(AccountId id) {
        Account account = findAny(id);
        account.block(time.now());
        accounts.save(account);
        audit.record(AuditEntry.of(AuditEvent.ACCOUNT_BLOCKED).account(id));
        outbox.record("AccountBlocked", "Account", id.toString(), java.util.Map.of("accountId", id.toString()));
        return account;
    }

    public Account unblock(AccountId id) {
        Account account = findAny(id);
        account.unblock(time.now());
        accounts.save(account);
        audit.record(AuditEntry.of(AuditEvent.ACCOUNT_UNBLOCKED).account(id));
        outbox.record("AccountUnblocked", "Account", id.toString(), java.util.Map.of("accountId", id.toString()));
        return account;
    }

    private Account findAny(AccountId id) {
        return accounts.findById(id).orElseThrow(() -> ApplicationException.notFound("Account"));
    }

    @Transactional(readOnly = true)
    public List<LimitUsage.Status> limits(CustomerId requester, AccountId id) {
        Account account = findOwned(requester, id);
        return limitUsage.allOf(account.id(), account.balance().currency());
    }

    private static Money moneyOf(Account account, BigDecimal amount) {
        return new Money(amount, account.balance().currency());
    }
}
