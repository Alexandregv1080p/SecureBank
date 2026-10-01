package com.securebank.payment.infrastructure.persistence;

import com.securebank.payment.domain.Payment;
import com.securebank.payment.domain.PaymentStatus;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PaymentId;
import com.securebank.shared.domain.TransactionId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id UUID id;
    UUID accountId;
    BigDecimal amount;
    String currency;
    String barcode;
    String description;
    String idempotencyKey;
    @Enumerated(EnumType.STRING) PaymentStatus status;
    UUID transactionId;
    String failureReason;
    Instant createdAt;
    Instant updatedAt;

    void apply(Payment payment) {
        id = payment.id().value();
        accountId = payment.accountId().value();
        amount = payment.amount().amount();
        currency = payment.amount().currency().getCurrencyCode();
        barcode = payment.barcode();
        description = payment.description();
        idempotencyKey = payment.idempotencyKey().value();
        status = payment.status();
        transactionId = payment.transactionId() == null ? null : payment.transactionId().value();
        failureReason = payment.failureReason();
        createdAt = payment.createdAt();
        updatedAt = payment.updatedAt();
    }

    Payment toDomain() {
        return Payment.restore(new PaymentId(id), new AccountId(accountId),
                new Money(amount, Currency.getInstance(currency)), barcode, description,
                new IdempotencyKey(idempotencyKey), status,
                transactionId == null ? null : new TransactionId(transactionId), failureReason, createdAt, updatedAt);
    }
}
