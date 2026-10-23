package com.securebank.card.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.Text;
import com.securebank.shared.domain.TransactionId;
import java.time.Instant;
import java.util.UUID;

/** Uma compra no débito (simulada): estabelecimento e valor, ligada ao lançamento que saiu da conta. */
public final class CardPurchase {

    public static final int MERCHANT_MAX = 60;

    private final UUID id;
    private final CardId cardId;
    private final AccountId accountId;
    private final Money amount;
    private final String merchant;
    private final TransactionId transactionId;
    private final Instant createdAt;

    private CardPurchase(UUID id, CardId cardId, AccountId accountId, Money amount, String merchant,
            TransactionId transactionId, Instant createdAt) {
        this.id = id;
        this.cardId = cardId;
        this.accountId = accountId;
        this.amount = amount;
        this.merchant = merchant;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }

    public static CardPurchase create(Card card, Money amount, String merchant, TransactionId transactionId, Instant now) {
        if (card == null || amount == null || transactionId == null || now == null) {
            throw new InvalidValueException("Purchase requires card, amount, transaction and time");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        return new CardPurchase(UUID.randomUUID(), card.id(), card.accountId(), amount, validateMerchant(merchant),
                transactionId, now);
    }

    /** Estabelecimento obrigatório, sem quebra de linha nem controle, até {@value #MERCHANT_MAX} caracteres. */
    public static String validateMerchant(String merchant) {
        String name = Text.optional(merchant);
        if (name == null) {
            throw new InvalidValueException("Merchant is required");
        }
        if (name.length() > MERCHANT_MAX) {
            throw new InvalidValueException("Merchant must have at most " + MERCHANT_MAX + " characters");
        }
        return name;
    }

    public static CardPurchase restore(UUID id, CardId cardId, AccountId accountId, Money amount, String merchant,
            TransactionId transactionId, Instant createdAt) {
        return new CardPurchase(id, cardId, accountId, amount, merchant, transactionId, createdAt);
    }

    public UUID id() { return id; }
    public CardId cardId() { return cardId; }
    public AccountId accountId() { return accountId; }
    public Money amount() { return amount; }
    public String merchant() { return merchant; }
    public TransactionId transactionId() { return transactionId; }
    public Instant createdAt() { return createdAt; }
}
