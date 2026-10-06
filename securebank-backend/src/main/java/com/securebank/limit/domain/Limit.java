package com.securebank.limit.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.time.Instant;

/**
 * Limite de uma conta para um tipo de operação: teto por operação e teto diário.
 * Quanto já foi usado hoje é calculado a partir dos lançamentos pela camada de aplicação
 * (o "dia" é o de America/Sao_Paulo) e informado em {@link #check}.
 */
public final class Limit {

    private final AccountId accountId;
    private final LimitType type;
    private Money perOperation;
    private Money daily;
    private Instant updatedAt;

    private Limit(AccountId accountId, LimitType type, Money perOperation, Money daily, Instant now) {
        this.accountId = accountId;
        this.type = type;
        this.perOperation = perOperation;
        this.daily = daily;
        this.updatedAt = now;
    }

    /** Política padrão (BRL) para contas novas; o ADMIN ajusta depois com {@link #change}. */
    public static Limit defaultFor(AccountId accountId, LimitType type, Instant now) {
        if (accountId == null || type == null || now == null) {
            throw new InvalidValueException("Limit requires account, type and time");
        }
        return switch (type) {
            case WITHDRAW -> new Limit(accountId, type, Money.brl("1000.00"), Money.brl("3000.00"), now);
            case TRANSFER -> new Limit(accountId, type, Money.brl("5000.00"), Money.brl("10000.00"), now);
            case PAYMENT -> new Limit(accountId, type, Money.brl("10000.00"), Money.brl("20000.00"), now);
            case PIX -> new Limit(accountId, type, Money.brl("5000.00"), Money.brl("10000.00"), now);
        };
    }

    /** Reconstitui um limite já persistido. */
    public static Limit restore(AccountId accountId, LimitType type, Money perOperation, Money daily,
            Instant updatedAt) {
        return new Limit(accountId, type, perOperation, daily, updatedAt);
    }

    public void change(Money perOperation, Money daily, Instant now) {
        if (perOperation == null || daily == null || !perOperation.isPositive()) {
            throw new InvalidValueException("Per-operation limit must be greater than zero");
        }
        this.perOperation.requireSameCurrency(perOperation);
        this.perOperation.requireSameCurrency(daily);
        if (daily.isLessThan(perOperation)) {
            throw new InvalidValueException("Daily limit must not be lower than the per-operation limit");
        }
        this.perOperation = perOperation;
        this.daily = daily;
        this.updatedAt = now;
    }

    /** @param usedToday soma das operações deste tipo já efetivadas hoje na conta */
    public void check(Money amount, Money usedToday) {
        if (amount.isGreaterThan(perOperation)) {
            throw new LimitExceededException(type, LimitExceededException.Scope.PER_OPERATION);
        }
        if (usedToday.plus(amount).isGreaterThan(daily)) {
            throw new LimitExceededException(type, LimitExceededException.Scope.DAILY);
        }
    }

    public AccountId accountId() { return accountId; }
    public LimitType type() { return type; }
    public Money perOperation() { return perOperation; }
    public Money daily() { return daily; }
    public Instant updatedAt() { return updatedAt; }
}
