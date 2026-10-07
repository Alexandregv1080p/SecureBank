package com.securebank.fx.domain;

import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/** Carteira em moeda estrangeira de um cliente (uma por moeda). O saldo nunca fica negativo. */
public final class FxWallet {

    private final UUID id;
    private final CustomerId customerId;
    private Money balance;
    private Instant updatedAt;

    private FxWallet(UUID id, CustomerId customerId, Money balance, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public static FxWallet open(CustomerId customerId, java.util.Currency currency, Instant now) {
        if (customerId == null || currency == null || now == null) {
            throw new InvalidValueException("Wallet requires customer, currency and time");
        }
        return new FxWallet(UUID.randomUUID(), customerId, Money.zero(currency), now);
    }

    public static FxWallet restore(UUID id, CustomerId customerId, Money balance, Instant updatedAt) {
        return new FxWallet(id, customerId, balance, updatedAt);
    }

    public void credit(Money amount, Instant now) {
        require(amount);
        balance = balance.plus(amount);
        updatedAt = now;
    }

    public void debit(Money amount, Instant now) {
        require(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientFxFundsException();
        }
        balance = balance.minus(amount);
        updatedAt = now;
    }

    private void require(Money amount) {
        balance.requireSameCurrency(amount);
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
    }

    public UUID id() { return id; }
    public CustomerId customerId() { return customerId; }
    public Money balance() { return balance; }
    public Instant updatedAt() { return updatedAt; }
}
