package com.securebank.payment.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PaymentId;
import com.securebank.shared.domain.TransactionId;
import java.time.Instant;

/** Pagamento de boleto/convênio a partir de uma conta. */
public final class Payment {

    private static final int MAX_DESCRIPTION = 140;

    private final PaymentId id;
    private final AccountId accountId;
    private final Money amount;
    private final String barcode;
    private final String description;
    private final IdempotencyKey idempotencyKey;
    private PaymentStatus status;
    private TransactionId transactionId;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Payment(AccountId accountId, Money amount, String barcode, String description, IdempotencyKey key,
            Instant now) {
        this.id = PaymentId.newId();
        this.accountId = accountId;
        this.amount = amount;
        this.barcode = barcode;
        this.description = description;
        this.idempotencyKey = key;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** @param barcode código de barras (44) ou linha digitável (47/48), só dígitos */
    public static Payment create(AccountId accountId, Money amount, String barcode, String description,
            IdempotencyKey key, Instant now) {
        if (accountId == null || amount == null || key == null || now == null) {
            throw new InvalidValueException("Payment requires account, amount, idempotency key and time");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        if (barcode == null || !barcode.matches("\\d{44}|\\d{47}|\\d{48}")) {
            throw new InvalidValueException("Barcode must have 44, 47 or 48 digits");
        }
        String text = description == null || description.isBlank() ? null : description.trim();
        if (text != null && text.length() > MAX_DESCRIPTION) {
            throw new InvalidValueException("Description must have at most " + MAX_DESCRIPTION + " characters");
        }
        return new Payment(accountId, amount, barcode, text, key, now);
    }

    public void markProcessing(Instant now) {
        transitionTo(PaymentStatus.PROCESSING, now);
    }

    public void complete(TransactionId transactionId, Instant now) {
        transitionTo(PaymentStatus.COMPLETED, now);
        this.transactionId = transactionId;
    }

    public void fail(String reason, Instant now) {
        transitionTo(PaymentStatus.FAILED, now);
        this.failureReason = reason;
    }

    private void transitionTo(PaymentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Payment", status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public PaymentId id() { return id; }
    public AccountId accountId() { return accountId; }
    public Money amount() { return amount; }
    public String barcode() { return barcode; }
    public String description() { return description; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public PaymentStatus status() { return status; }
    public TransactionId transactionId() { return transactionId; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
