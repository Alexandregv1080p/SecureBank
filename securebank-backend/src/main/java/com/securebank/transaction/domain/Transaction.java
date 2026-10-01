package com.securebank.transaction.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import java.time.Instant;

/**
 * Lançamento financeiro numa conta. Imutável salvo pelo status, e nunca é apagado:
 * um erro é corrigido com REVERSED + lançamento de estorno, preservando a rastreabilidade (seção 30).
 */
public final class Transaction {

    private final TransactionId id;
    private final AccountId accountId;
    private final TransactionType type;
    private final TransactionDirection direction;
    private final Money amount;
    private final Money balanceAfter;
    private TransactionStatus status;
    private final String reference;
    private final Instant createdAt;

    private Transaction(TransactionId id, TransactionType type, TransactionDirection direction, AccountId accountId,
            Money amount, Money balanceAfter, TransactionStatus status, String reference, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.status = status;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    /** Lançamento já efetivado no saldo — é o que a Account produz ao debitar/creditar. */
    public static Transaction completed(AccountId accountId, TransactionType type, TransactionDirection direction,
            Money amount, Money balanceAfter, String reference, Instant now) {
        if (accountId == null || type == null || direction == null || amount == null || balanceAfter == null
                || now == null) {
            throw new InvalidValueException("Transaction requires account, type, direction, amounts and time");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Transaction amount must be greater than zero");
        }
        if (reference != null && reference.isBlank()) {
            throw new InvalidValueException("Reference must not be blank");
        }
        return new Transaction(TransactionId.newId(), type, direction, accountId, amount, balanceAfter,
                TransactionStatus.COMPLETED, reference, now);
    }

    /** Reconstitui um lançamento já persistido. */
    public static Transaction restore(TransactionId id, AccountId accountId, TransactionType type,
            TransactionDirection direction, Money amount, Money balanceAfter, TransactionStatus status,
            String reference, Instant createdAt) {
        return new Transaction(id, type, direction, accountId, amount, balanceAfter, status, reference, createdAt);
    }

    public void markProcessing() {
        transitionTo(TransactionStatus.PROCESSING);
    }

    public void complete() {
        transitionTo(TransactionStatus.COMPLETED);
    }

    public void fail() {
        transitionTo(TransactionStatus.FAILED);
    }

    public void reverse() {
        transitionTo(TransactionStatus.REVERSED);
    }

    private void transitionTo(TransactionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Transaction", status, target);
        }
        this.status = target;
    }

    public TransactionId id() { return id; }
    public AccountId accountId() { return accountId; }
    public TransactionType type() { return type; }
    public TransactionDirection direction() { return direction; }
    public Money amount() { return amount; }
    public Money balanceAfter() { return balanceAfter; }
    public TransactionStatus status() { return status; }
    public String reference() { return reference; }
    public Instant createdAt() { return createdAt; }
}
