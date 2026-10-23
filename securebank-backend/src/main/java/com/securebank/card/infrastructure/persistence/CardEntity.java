package com.securebank.card.infrastructure.persistence;

import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardStatus;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "cards")
class CardEntity {

    @Id UUID id;
    UUID customerId;
    UUID accountId;
    String holderName;
    String last4;
    String numberEncrypted;
    String numberFingerprint;
    int expiryMonth;
    int expiryYear;
    @Enumerated(EnumType.STRING) CardStatus status;
    String statusReason;
    Instant createdAt;
    Instant updatedAt;
    /** Controle otimista: bloquear e cancelar ao mesmo tempo não se atropelam. */
    @Version Long version;

    void apply(Card card) {
        id = card.id().value();
        customerId = card.customerId().value();
        accountId = card.accountId().value();
        holderName = card.holderName();
        last4 = card.last4();
        expiryMonth = card.expiry().getMonthValue();
        expiryYear = card.expiry().getYear();
        status = card.status();
        statusReason = card.statusReason();
        createdAt = card.createdAt();
        updatedAt = card.updatedAt();
    }

    Card toDomain() {
        return Card.restore(new CardId(id), new CustomerId(customerId), new AccountId(accountId), holderName, last4,
                YearMonth.of(expiryYear, expiryMonth), status, statusReason, createdAt, updatedAt);
    }
}
