package com.securebank.piggy.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.outbox.application.OutboxService;
import com.securebank.piggy.domain.Piggy;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PiggyId;
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
 * Casos de uso do porquinho. Guardar e resgatar mexem no saldo da conta E no do porquinho na MESMA transação de banco
 * (com lançamento no extrato, auditoria e evento no outbox), então os dois nunca divergem. Porquinho inexistente e de
 * outro dono são indistinguíveis (404): ids não podem ser sondados.
 */
@Service
@Transactional
public class PiggyApplicationService {

    static final int MAX_ACTIVE_PER_CUSTOMER = 20;

    private final PiggyRepository piggies;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public PiggyApplicationService(PiggyRepository piggies, AccountApplicationService accountService,
            AccountRepository accounts, CustomerApplicationService customers, TransactionRepository transactions,
            BankTime time, AuditService audit, OutboxService outbox, TransactionalRetry retry) {
        this.piggies = piggies;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    public Piggy create(CustomerId requester, AccountId accountId, String name, BigDecimal goal) {
        customers.requireActive(requester);
        Account account = accountService.findOwned(requester, accountId);
        if (piggies.countActiveByCustomer(requester) >= MAX_ACTIVE_PER_CUSTOMER) {
            throw ApplicationException.unprocessable("PIGGY_LIMIT_REACHED",
                    "You can have at most " + MAX_ACTIVE_PER_CUSTOMER + " piggy banks");
        }
        var currency = account.balance().currency();
        Piggy piggy = Piggy.create(requester, account.id(), name, goal == null ? null : new Money(goal, currency),
                currency, time.now());
        piggies.save(piggy);
        audit.record(AuditEntry.of(AuditEvent.PIGGY_CREATED).account(account.id()).detail(piggy.id().toString()));
        return piggy;
    }

    @Transactional(readOnly = true)
    public List<Piggy> list(CustomerId requester) {
        return piggies.findActiveByCustomer(requester);
    }

    @Transactional(readOnly = true)
    public Piggy get(CustomerId requester, PiggyId id) {
        return findOwned(requester, id);
    }

    /** Muda o nome e/ou a meta; {@code clearGoal} remove a meta. Campos nulos não mudam. */
    public Piggy update(CustomerId requester, PiggyId id, String name, BigDecimal goal, boolean clearGoal) {
        customers.requireActive(requester);
        Piggy piggy = findOwned(requester, id);
        Instant now = time.now();
        if (name != null) {
            piggy.rename(name, now);
        }
        if (clearGoal) {
            piggy.clearGoal(now);
        } else if (goal != null) {
            piggy.changeGoal(new Money(goal, piggy.balance().currency()), now);
        }
        piggies.save(piggy);
        return piggy;
    }

    /** Roda sem transação própria: abre uma por tentativa e repete em conflito de versão. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Piggy deposit(CustomerId requester, PiggyId id, BigDecimal amount) {
        return retry.execute(() -> doDeposit(requester, id, amount));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Piggy withdraw(CustomerId requester, PiggyId id, BigDecimal amount) {
        return retry.execute(() -> doWithdraw(requester, id, amount));
    }

    /** Fecha o porquinho devolvendo o que ele tiver para a conta, tudo numa transação. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void close(CustomerId requester, PiggyId id) {
        retry.execute(() -> {
            doClose(requester, id);
            return null;
        });
    }

    private Piggy doDeposit(CustomerId requester, PiggyId id, BigDecimal amount) {
        customers.requireActive(requester);
        Piggy piggy = findOwned(requester, id);
        Account account = accountService.findOwned(requester, piggy.accountId());
        Money money = new Money(amount, account.balance().currency());
        Instant now = time.now();

        Transaction debit = account.saveToPiggy(money, reference(piggy), now); // exige conta ativa e saldo
        boolean reachedGoal = piggy.deposit(money, now);

        accounts.save(account);
        piggies.save(piggy);
        transactions.save(debit);
        audit.record(AuditEntry.of(AuditEvent.PIGGY_DEPOSIT).account(account.id()).transaction(debit.id())
                .detail(piggy.id().toString()));
        outbox.record("PiggyDeposited", "Piggy", piggy.id().toString(), payload(piggy, money));
        if (reachedGoal) {
            outbox.record("PiggyGoalReached", "Piggy", piggy.id().toString(), payload(piggy, piggy.balance()));
        }
        return piggy;
    }

    private Piggy doWithdraw(CustomerId requester, PiggyId id, BigDecimal amount) {
        customers.requireActive(requester);
        Piggy piggy = findOwned(requester, id);
        Account account = accountService.findOwned(requester, piggy.accountId());
        Money money = new Money(amount, account.balance().currency());
        Instant now = time.now();

        piggy.withdraw(money, now);
        Transaction credit = account.redeemFromPiggy(money, reference(piggy), now);

        accounts.save(account);
        piggies.save(piggy);
        transactions.save(credit);
        audit.record(AuditEntry.of(AuditEvent.PIGGY_WITHDRAW).account(account.id()).transaction(credit.id())
                .detail(piggy.id().toString()));
        outbox.record("PiggyWithdrawn", "Piggy", piggy.id().toString(), payload(piggy, money));
        return piggy;
    }

    private void doClose(CustomerId requester, PiggyId id) {
        customers.requireActive(requester);
        Piggy piggy = findOwned(requester, id);
        Instant now = time.now();
        Money remaining = piggy.balance();
        if (remaining.isPositive()) {
            Account account = accountService.findOwned(requester, piggy.accountId());
            piggy.withdraw(remaining, now);
            Transaction credit = account.redeemFromPiggy(remaining, reference(piggy), now);
            accounts.save(account);
            transactions.save(credit);
            outbox.record("PiggyWithdrawn", "Piggy", piggy.id().toString(), payload(piggy, remaining));
        }
        piggy.close(now);
        piggies.save(piggy);
        audit.record(AuditEntry.of(AuditEvent.PIGGY_CLOSED).account(piggy.accountId()).detail(piggy.id().toString()));
    }

    private Piggy findOwned(CustomerId requester, PiggyId id) {
        return piggies.findById(id).filter(p -> p.isOwnedBy(requester))
                .orElseThrow(() -> ApplicationException.notFound("Piggy"));
    }

    /** Referência do lançamento no extrato (cabe nos 64 caracteres da coluna). */
    private static String reference(Piggy piggy) {
        return "piggy:" + piggy.id();
    }

    /** Só ids e valores: nunca o nome do porquinho (texto livre do usuário). */
    private static Map<String, Object> payload(Piggy piggy, Money amount) {
        return Map.of("piggyId", piggy.id().toString(), "accountId", piggy.accountId().toString(),
                "amount", amount.amount().toPlainString(), "currency", amount.currency().getCurrencyCode());
    }
}
