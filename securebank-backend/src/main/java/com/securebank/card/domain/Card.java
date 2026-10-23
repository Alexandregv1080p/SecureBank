package com.securebank.card.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Cartão de débito virtual de uma conta. A compra sai direto do saldo da conta (sem fatura, sem limite de crédito).
 * O número completo e o CVV nunca ficam nesta classe: só os 4 últimos dígitos e a validade; o número inteiro é guardado
 * cifrado e o CVV é derivado dele na hora de mostrar (ver CardSecrets).
 */
public final class Card {

    public static final int VALID_YEARS = 5;
    public static final int HOLDER_NAME_MAX = 40;

    private final CardId id;
    private final CustomerId customerId;
    private final AccountId accountId;
    private final String holderName;
    private final String last4;
    private final YearMonth expiry;
    private CardStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Card(CardId id, CustomerId customerId, AccountId accountId, String holderName, String last4, YearMonth expiry,
            CardStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountId = accountId;
        this.holderName = holderName;
        this.last4 = last4;
        this.expiry = expiry;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** @param today dia atual de America/Sao_Paulo: o cartão vale até o fim do mês, daqui a {@value #VALID_YEARS} anos */
    public static Card issue(CustomerId customerId, AccountId accountId, String customerName, String number, LocalDate today,
            Instant now) {
        if (customerId == null || accountId == null || today == null || now == null) {
            throw new InvalidValueException("Card requires customer, account and time");
        }
        return new Card(CardId.newId(), customerId, accountId, holderName(customerName), CardNumber.last4(number),
                YearMonth.from(today).plusYears(VALID_YEARS), CardStatus.ACTIVE, now, now);
    }

    /** Reconstitui um cartão já persistido. */
    public static Card restore(CardId id, CustomerId customerId, AccountId accountId, String holderName, String last4,
            YearMonth expiry, CardStatus status, Instant createdAt, Instant updatedAt) {
        return new Card(id, customerId, accountId, holderName, last4, expiry, status, createdAt, updatedAt);
    }

    /** Nome como no plástico: maiúsculas, sem acento, no máximo {@value #HOLDER_NAME_MAX} caracteres. */
    static String holderName(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            throw new InvalidValueException("Card holder name is required");
        }
        String plain = Normalizer.normalize(customerName.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String upper = plain.toUpperCase(Locale.ROOT);
        return upper.length() <= HOLDER_NAME_MAX ? upper : upper.substring(0, HOLDER_NAME_MAX).trim();
    }

    public boolean isExpired(LocalDate today) {
        return YearMonth.from(today).isAfter(expiry);
    }

    /** Compra só com o cartão ativo e dentro da validade. */
    public void ensureCanPurchase(LocalDate today) {
        if (status == CardStatus.BLOCKED) {
            throw new CardNotUsableException("it is blocked");
        }
        if (status == CardStatus.CANCELED) {
            throw new CardNotUsableException("it is canceled");
        }
        if (isExpired(today)) {
            throw new CardNotUsableException("it is expired");
        }
    }

    public void block(Instant now) {
        transitionTo(CardStatus.BLOCKED, now);
    }

    public void unblock(Instant now) {
        transitionTo(CardStatus.ACTIVE, now);
    }

    /** Cancelar é definitivo: um cartão cancelado não volta. */
    public void cancel(Instant now) {
        transitionTo(CardStatus.CANCELED, now);
    }

    private void transitionTo(CardStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Card", status, target);
        }
        status = target;
        updatedAt = now;
    }

    public CardId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public AccountId accountId() { return accountId; }
    public String holderName() { return holderName; }
    public String last4() { return last4; }
    public YearMonth expiry() { return expiry; }
    public CardStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
