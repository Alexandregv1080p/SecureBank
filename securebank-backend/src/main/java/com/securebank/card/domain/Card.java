package com.securebank.card.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Text;
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
    public static final int REASON_MIN = 3;
    public static final int REASON_MAX = 140;

    private final CardId id;
    private final CustomerId customerId;
    private final AccountId accountId;
    private final CardKind kind;
    private final String holderName;
    private final String last4;
    private final YearMonth expiry;
    private CardStatus status;
    /** Por que foi bloqueado ou cancelado, nas palavras do cliente; vazio quando o cartão está ativo. */
    private String statusReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Card(CardId id, CustomerId customerId, AccountId accountId, CardKind kind, String holderName, String last4, YearMonth expiry,
            CardStatus status, String statusReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountId = accountId;
        this.kind = kind;
        this.holderName = holderName;
        this.last4 = last4;
        this.expiry = expiry;
        this.status = status;
        this.statusReason = statusReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** @param today dia atual de America/Sao_Paulo: o cartão vale até o fim do mês, daqui a {@value #VALID_YEARS} anos */
    public static Card issue(CustomerId customerId, AccountId accountId, String customerName, String number, LocalDate today,
            Instant now) {
        return issue(customerId, accountId, CardKind.DEBIT, customerName, number, today, now);
    }

    public static Card issue(CustomerId customerId, AccountId accountId, CardKind kind, String customerName, String number,
            LocalDate today, Instant now) {
        if (customerId == null || kind == null || accountId == null || today == null || now == null) {
            throw new InvalidValueException("Card requires customer, account and time");
        }
        return new Card(CardId.newId(), customerId, accountId, kind, holderName(customerName), CardNumber.last4(number),
                YearMonth.from(today).plusYears(VALID_YEARS), CardStatus.ACTIVE, null, now, now);
    }

    /** Reconstitui um cartão já persistido. */
    public static Card restore(CardId id, CustomerId customerId, AccountId accountId, CardKind kind, String holderName, String last4,
            YearMonth expiry, CardStatus status, String statusReason, Instant createdAt, Instant updatedAt) {
        return new Card(id, customerId, accountId, kind, holderName, last4, expiry, status, statusReason, createdAt, updatedAt);
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

    /** Bloquear pede o motivo (de 3 a {@value #REASON_MAX} caracteres): fica no cartão e na auditoria. */
    public void block(String reason, Instant now) {
        transitionTo(CardStatus.BLOCKED, validReason(reason), now);
    }

    public void unblock(Instant now) {
        transitionTo(CardStatus.ACTIVE, null, now);
    }

    /** Cancelar é definitivo (um cartão cancelado não volta) e também pede o motivo. */
    public void cancel(String reason, Instant now) {
        transitionTo(CardStatus.CANCELED, validReason(reason), now);
    }

    /** Motivo sem quebra de linha nem caracteres de controle, de {@value #REASON_MIN} a {@value #REASON_MAX} caracteres. */
    public static String validReason(String reason) {
        String text = Text.optional(reason);
        if (text == null || text.length() < REASON_MIN) {
            throw new InvalidValueException("Tell us the reason (at least " + REASON_MIN + " characters)");
        }
        if (text.length() > REASON_MAX) {
            throw new InvalidValueException("The reason must have at most " + REASON_MAX + " characters");
        }
        return text;
    }

    private void transitionTo(CardStatus target, String reason, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Card", status, target);
        }
        status = target;
        statusReason = reason;
        updatedAt = now;
    }

    public CardId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public AccountId accountId() { return accountId; }
    public CardKind kind() { return kind; }
    public String holderName() { return holderName; }
    public String last4() { return last4; }
    public YearMonth expiry() { return expiry; }
    public CardStatus status() { return status; }
    public String statusReason() { return statusReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
