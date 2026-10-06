package com.securebank.piggy.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PiggyId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;

/**
 * Porquinho: uma reserva com nome e meta opcional, guardada dentro de uma conta. O dinheiro SAI do saldo da conta
 * (lançamento PIGGY_IN) e entra no porquinho, e o resgate faz o caminho inverso (PIGGY_OUT): o total do cliente não muda,
 * só onde ele está. Dono das próprias invariantes: saldo nunca negativo, só movimenta se ativo, só na moeda da conta.
 */
public final class Piggy {

    public static final int NAME_MAX = 40;

    private final PiggyId id;
    private final CustomerId customerId;
    private final AccountId accountId;
    private String name;
    private Money goal; // opcional
    private Money balance;
    private PiggyStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Piggy(PiggyId id, CustomerId customerId, AccountId accountId, String name, Money goal, Money balance,
            PiggyStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountId = accountId;
        this.name = name;
        this.goal = goal;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Piggy create(CustomerId customerId, AccountId accountId, String name, Money goal, Currency currency,
            Instant now) {
        if (customerId == null || accountId == null || currency == null || now == null) {
            throw new InvalidValueException("Piggy requires customer, account, currency and time");
        }
        return new Piggy(PiggyId.newId(), customerId, accountId, validName(name), validGoal(goal, currency),
                Money.zero(currency), PiggyStatus.ACTIVE, now, now);
    }

    /** Reconstitui um porquinho já persistido. */
    public static Piggy restore(PiggyId id, CustomerId customerId, AccountId accountId, String name, Money goal,
            Money balance, PiggyStatus status, Instant createdAt, Instant updatedAt) {
        return new Piggy(id, customerId, accountId, name, goal, balance, status, createdAt, updatedAt);
    }

    /** @return true se este depósito foi o que fez o porquinho alcançar a meta (dispara o aviso uma vez só). */
    public boolean deposit(Money amount, Instant now) {
        ensureOpen();
        requirePositive(amount);
        balance.requireSameCurrency(amount);
        boolean reachedBefore = goalReached();
        balance = balance.plus(amount);
        updatedAt = now;
        return !reachedBefore && goalReached();
    }

    public void withdraw(Money amount, Instant now) {
        ensureOpen();
        requirePositive(amount);
        balance.requireSameCurrency(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientPiggyFundsException();
        }
        balance = balance.minus(amount);
        updatedAt = now;
    }

    public void rename(String newName, Instant now) {
        ensureOpen();
        this.name = validName(newName);
        this.updatedAt = now;
    }

    public void changeGoal(Money newGoal, Instant now) {
        ensureOpen();
        if (newGoal == null) {
            throw new InvalidValueException("Goal is required (use clearGoal to remove it)");
        }
        this.goal = validGoal(newGoal, balance.currency());
        this.updatedAt = now;
    }

    public void clearGoal(Instant now) {
        ensureOpen();
        this.goal = null;
        this.updatedAt = now;
    }

    /** Só fecha vazio: quem fecha com dinheiro dentro resgata antes (o serviço faz isso numa única transação). */
    public void close(Instant now) {
        ensureOpen();
        if (!balance.isZero()) {
            throw new PiggyHasBalanceException();
        }
        this.status = PiggyStatus.CLOSED;
        this.updatedAt = now;
    }

    public boolean goalReached() {
        return goal != null && !balance.isLessThan(goal);
    }

    /** 0 a 100 (arredondado para baixo, então 100 só quando a meta foi mesmo atingida); null sem meta. */
    public Integer progressPercent() {
        if (goal == null) {
            return null;
        }
        BigDecimal percent = balance.amount().multiply(BigDecimal.valueOf(100))
                .divide(goal.amount(), 0, RoundingMode.DOWN);
        return percent.min(BigDecimal.valueOf(100)).intValue();
    }

    /** Base da autorização por recurso (IDOR): só o dono enxerga o porquinho. */
    public boolean isOwnedBy(CustomerId customerId) {
        return this.customerId.equals(customerId);
    }

    private void ensureOpen() {
        if (status != PiggyStatus.ACTIVE) {
            throw new PiggyClosedException();
        }
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidValueException("Piggy name is required");
        }
        if (trimmed.length() > NAME_MAX) {
            throw new InvalidValueException("Piggy name must have at most " + NAME_MAX + " characters");
        }
        return trimmed;
    }

    private static Money validGoal(Money goal, Currency currency) {
        if (goal == null) {
            return null;
        }
        if (!goal.isPositive()) {
            throw new InvalidValueException("Goal must be greater than zero");
        }
        Money.zero(currency).requireSameCurrency(goal);
        return goal;
    }

    private static void requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
    }

    public PiggyId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public AccountId accountId() { return accountId; }
    public String name() { return name; }
    public Money goal() { return goal; }
    public Money balance() { return balance; }
    public PiggyStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
