package com.securebank.transaction.infrastructure.persistence;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionStatus;
import com.securebank.transaction.domain.TransactionType;
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
@Table(name = "transactions")
class TransactionEntity {

    @Id UUID id;
    UUID accountId;
    @Enumerated(EnumType.STRING) TransactionType type;
    @Enumerated(EnumType.STRING) TransactionDirection direction;
    BigDecimal amount;
    BigDecimal balanceAfter;
    String currency;
    @Enumerated(EnumType.STRING) TransactionStatus status;
    String reference;
    Instant createdAt;

    static TransactionEntity of(Transaction transaction) {
        TransactionEntity entity = new TransactionEntity();
        entity.id = transaction.id().value();
        entity.accountId = transaction.accountId().value();
        entity.type = transaction.type();
        entity.direction = transaction.direction();
        entity.amount = transaction.amount().amount();
        entity.balanceAfter = transaction.balanceAfter().amount();
        entity.currency = transaction.amount().currency().getCurrencyCode();
        entity.status = transaction.status();
        entity.reference = transaction.reference();
        entity.createdAt = transaction.createdAt();
        return entity;
    }

    Transaction toDomain() {
        Currency cur = Currency.getInstance(currency);
        return Transaction.restore(new TransactionId(id), new AccountId(accountId), type, direction,
                new Money(amount, cur), new Money(balanceAfter, cur), status, reference, createdAt);
    }
}
