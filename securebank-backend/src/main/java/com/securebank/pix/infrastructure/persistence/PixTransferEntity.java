package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixTransferId;
import com.securebank.shared.domain.TransactionId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "pix_transfers")
class PixTransferEntity {

    @Id UUID id;
    UUID sourceAccountId;
    UUID destinationAccountId;
    BigDecimal amount;
    String currency;
    String message;
    String destinationKey;
    String sourceName;
    String destinationName;
    String endToEndId;
    UUID debitTransactionId;
    UUID creditTransactionId;
    Instant createdAt;

    void apply(PixTransfer pix) {
        id = pix.id().value();
        sourceAccountId = pix.sourceAccountId().value();
        destinationAccountId = pix.destinationAccountId().value();
        amount = pix.amount().amount();
        currency = pix.amount().currency().getCurrencyCode();
        message = pix.message();
        destinationKey = pix.destinationKey();
        sourceName = pix.sourceName();
        destinationName = pix.destinationName();
        endToEndId = pix.endToEndId();
        debitTransactionId = pix.debitTransactionId() == null ? null : pix.debitTransactionId().value();
        creditTransactionId = pix.creditTransactionId() == null ? null : pix.creditTransactionId().value();
        createdAt = pix.createdAt();
    }

    PixTransfer toDomain() {
        return PixTransfer.restore(new PixTransferId(id), new AccountId(sourceAccountId),
                new AccountId(destinationAccountId), new Money(amount, Currency.getInstance(currency)), message,
                destinationKey, sourceName, destinationName, endToEndId,
                debitTransactionId == null ? null : new TransactionId(debitTransactionId),
                creditTransactionId == null ? null : new TransactionId(creditTransactionId), createdAt);
    }
}
