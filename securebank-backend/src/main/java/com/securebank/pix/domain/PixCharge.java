package com.securebank.pix.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixTransferId;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Cobrança Pix (QR dinâmico): valor FIXO, validade e uso ÚNICO. O {@code txid} (32 caracteres aleatórios, ~190 bits) é o
 * identificador no QR; quem o conhece pode consultar e pagar a cobrança, então ele nunca é derivável nem sequencial.
 */
public final class PixCharge {

    public static final int DESCRIPTION_MAX = 140;
    public static final Duration MIN_TTL = Duration.ofMinutes(1);
    public static final Duration MAX_TTL = Duration.ofDays(7);
    public static final Duration DEFAULT_TTL = Duration.ofDays(1);

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Pattern TXID = Pattern.compile("[A-Za-z0-9]{26,35}");
    private static final Random SECURE = new SecureRandom();

    private final String txid;
    private final CustomerId customerId;
    private final AccountId accountId;
    private final Money amount;
    private final String description;
    private PixChargeStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant paidAt;
    private PixTransferId paidPixId;

    private PixCharge(String txid, CustomerId customerId, AccountId accountId, Money amount, String description,
            PixChargeStatus status, Instant expiresAt, Instant createdAt) {
        this.txid = txid;
        this.customerId = customerId;
        this.accountId = accountId;
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static PixCharge create(CustomerId customerId, AccountId accountId, Money amount, String description,
            Duration ttl, Instant now) {
        return create(customerId, accountId, amount, description, ttl, now, SECURE);
    }

    static PixCharge create(CustomerId customerId, AccountId accountId, Money amount, String description,
            Duration ttl, Instant now, Random random) {
        if (customerId == null || accountId == null || amount == null || now == null) {
            throw new InvalidValueException("Charge requires customer, account, amount and time");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        Duration validity = ttl == null ? DEFAULT_TTL : ttl;
        if (validity.compareTo(MIN_TTL) < 0 || validity.compareTo(MAX_TTL) > 0) {
            throw new InvalidValueException("Validity must be between 1 minute and 7 days");
        }
        String text = description == null || description.isBlank() ? null : description.trim();
        if (text != null && text.length() > DESCRIPTION_MAX) {
            throw new InvalidValueException("Description must have at most " + DESCRIPTION_MAX + " characters");
        }
        return new PixCharge(newTxid(random), customerId, accountId, amount, text, PixChargeStatus.ACTIVE,
                now.plus(validity), now);
    }

    public static PixCharge restore(String txid, CustomerId customerId, AccountId accountId, Money amount,
            String description, PixChargeStatus status, Instant expiresAt, Instant createdAt, Instant paidAt,
            PixTransferId paidPixId) {
        PixCharge charge = new PixCharge(txid, customerId, accountId, amount, description, status, expiresAt, createdAt);
        charge.paidAt = paidAt;
        charge.paidPixId = paidPixId;
        return charge;
    }

    /** Aceita só o formato do BR Code (26 a 35 alfanuméricos): o que vem de um QR é entrada não confiável. */
    public static boolean isValidTxid(String value) {
        return value != null && TXID.matcher(value).matches();
    }

    static String newTxid(Random random) {
        StringBuilder id = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }

    /** Estado em {@code now}: uma cobrança ativa cujo prazo passou conta como EXPIRED. */
    public PixChargeStatus statusAt(Instant now) {
        return status == PixChargeStatus.ACTIVE && !now.isBefore(expiresAt) ? PixChargeStatus.EXPIRED : status;
    }

    /** Pré-condição de pagamento: ativa e dentro do prazo. */
    public void ensurePayable(Instant now) {
        PixChargeStatus current = statusAt(now);
        if (current == PixChargeStatus.EXPIRED) {
            throw PixChargeException.expired();
        }
        if (current != PixChargeStatus.ACTIVE) {
            throw PixChargeException.notPayable(current);
        }
    }

    public void markPaid(PixTransferId pixId, Instant now) {
        ensurePayable(now);
        this.status = PixChargeStatus.PAID;
        this.paidAt = now;
        this.paidPixId = pixId;
    }

    public void cancel() {
        if (status != PixChargeStatus.ACTIVE) {
            throw PixChargeException.notCancelable();
        }
        this.status = PixChargeStatus.CANCELED;
    }

    public boolean isOwnedBy(CustomerId customerId) {
        return this.customerId.equals(customerId);
    }

    public String txid() { return txid; }
    public CustomerId customerId() { return customerId; }
    public AccountId accountId() { return accountId; }
    public Money amount() { return amount; }
    public String description() { return description; }
    public PixChargeStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }
    public Instant paidAt() { return paidAt; }
    public PixTransferId paidPixId() { return paidPixId; }
}
