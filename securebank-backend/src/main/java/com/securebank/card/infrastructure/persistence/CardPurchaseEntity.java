package com.securebank.card.infrastructure.persistence;

import com.securebank.card.domain.CardPurchase;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "card_purchases")
class CardPurchaseEntity {

    @Id UUID id;
    UUID cardId;
    UUID accountId;
    BigDecimal amount;
    String currency;
    String merchant;
    UUID transactionId;
    Instant createdAt;

    void apply(CardPurchase purchase) {
        id = purchase.id();
        cardId = purchase.cardId().value();
        accountId = purchase.accountId().value();
        amount = purchase.amount().amount();
        currency = purchase.amount().currency().getCurrencyCode();
        merchant = purchase.merchant();
        transactionId = purchase.transactionId().value();
        createdAt = purchase.createdAt();
    }

    CardPurchase toDomain() {
        return CardPurchase.restore(id, new CardId(cardId), new AccountId(accountId),
                new Money(amount, Currency.getInstance(currency)), merchant, new TransactionId(transactionId), createdAt);
    }
}
