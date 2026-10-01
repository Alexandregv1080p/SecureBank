package com.securebank.transfer.infrastructure.persistence;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import com.securebank.shared.domain.TransferId;
import com.securebank.transfer.domain.Transfer;
import com.securebank.transfer.domain.TransferStatus;
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
@Table(name = "transfers")
class TransferEntity {

    @Id UUID id;
    UUID sourceAccountId;
    UUID destinationAccountId;
    BigDecimal amount;
    String currency;
    String description;
    String idempotencyKey;
    @Enumerated(EnumType.STRING) TransferStatus status;
    UUID debitTransactionId;
    UUID creditTransactionId;
    String failureReason;
    Instant createdAt;
    Instant updatedAt;

    void apply(Transfer transfer) {
        id = transfer.id().value();
        sourceAccountId = transfer.sourceAccountId().value();
        destinationAccountId = transfer.destinationAccountId().value();
        amount = transfer.amount().amount();
        currency = transfer.amount().currency().getCurrencyCode();
        description = transfer.description();
        idempotencyKey = transfer.idempotencyKey().value();
        status = transfer.status();
        debitTransactionId = transfer.debitTransactionId() == null ? null : transfer.debitTransactionId().value();
        creditTransactionId = transfer.creditTransactionId() == null ? null : transfer.creditTransactionId().value();
        failureReason = transfer.failureReason();
        createdAt = transfer.createdAt();
        updatedAt = transfer.updatedAt();
    }

    Transfer toDomain() {
        return Transfer.restore(new TransferId(id), new AccountId(sourceAccountId), new AccountId(destinationAccountId),
                new Money(amount, Currency.getInstance(currency)), description, new IdempotencyKey(idempotencyKey),
                status, debitTransactionId == null ? null : new TransactionId(debitTransactionId),
                creditTransactionId == null ? null : new TransactionId(creditTransactionId), failureReason, createdAt,
                updatedAt);
    }
}
