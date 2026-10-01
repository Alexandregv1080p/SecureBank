package com.securebank.transfer.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import com.securebank.shared.domain.TransferId;
import java.time.Instant;

/**
 * Pedido de transferência entre duas contas. A chave de idempotência acompanha o pedido para que um retry
 * devolva este mesmo registro (a checagem em si é da camada de aplicação, Fase 5).
 */
public final class Transfer {

    private static final int MAX_DESCRIPTION = 140;

    private final TransferId id;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private final Money amount;
    private final String description;
    private final IdempotencyKey idempotencyKey;
    private TransferStatus status;
    private TransactionId debitTransactionId;
    private TransactionId creditTransactionId;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Transfer(AccountId source, AccountId destination, Money amount, String description,
            IdempotencyKey key, Instant now) {
        this.id = TransferId.newId();
        this.sourceAccountId = source;
        this.destinationAccountId = destination;
        this.amount = amount;
        this.description = description;
        this.idempotencyKey = key;
        this.status = TransferStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Transfer request(AccountId source, AccountId destination, Money amount, String description,
            IdempotencyKey key, Instant now) {
        if (source == null || destination == null || amount == null || key == null || now == null) {
            throw new InvalidValueException("Transfer requires source, destination, amount, idempotency key and time");
        }
        if (source.equals(destination)) {
            throw new InvalidValueException("Source and destination accounts must be different");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        String text = description == null || description.isBlank() ? null : description.trim();
        if (text != null && text.length() > MAX_DESCRIPTION) {
            throw new InvalidValueException("Description must have at most " + MAX_DESCRIPTION + " characters");
        }
        return new Transfer(source, destination, amount, text, key, now);
    }

    public void ensurePending() {
        if (status != TransferStatus.PENDING) {
            throw new InvalidStateTransitionException("Transfer", status, TransferStatus.COMPLETED);
        }
    }

    public void complete(TransactionId debit, TransactionId credit, Instant now) {
        transitionTo(TransferStatus.COMPLETED, now);
        this.debitTransactionId = debit;
        this.creditTransactionId = credit;
    }

    public void fail(String reason, Instant now) {
        transitionTo(TransferStatus.FAILED, now);
        this.failureReason = reason;
    }

    private void transitionTo(TransferStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Transfer", status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public TransferId id() { return id; }
    public AccountId sourceAccountId() { return sourceAccountId; }
    public AccountId destinationAccountId() { return destinationAccountId; }
    public Money amount() { return amount; }
    public String description() { return description; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public TransferStatus status() { return status; }
    public TransactionId debitTransactionId() { return debitTransactionId; }
    public TransactionId creditTransactionId() { return creditTransactionId; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
