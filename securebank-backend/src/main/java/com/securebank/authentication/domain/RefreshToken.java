package com.securebank.authentication.domain;

import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.SessionId;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Registro de um refresh token. O valor entregue ao cliente é aleatório (256 bits) e só o hash SHA-256 é guardado:
 * um vazamento do banco não revela tokens utilizáveis. Cada token vale uma única vez (rotação).
 */
public final class RefreshToken {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Token novo: o valor em claro existe só nesta chamada. */
    public record Issued(RefreshToken record, String rawValue) {}

    private final UUID id;
    private final SessionId sessionId;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant usedAt;

    private RefreshToken(UUID id, SessionId sessionId, String tokenHash, Instant createdAt, Instant expiresAt,
            Instant usedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    public static Issued issue(SessionId sessionId, Instant now, Instant expiresAt) {
        if (sessionId == null || now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new InvalidValueException("Refresh token requires a session and a future expiry");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(new RefreshToken(UUID.randomUUID(), sessionId, hash(raw), now, expiresAt, null), raw);
    }

    public static RefreshToken restore(UUID id, SessionId sessionId, String tokenHash, Instant createdAt,
            Instant expiresAt, Instant usedAt) {
        return new RefreshToken(id, sessionId, tokenHash, createdAt, expiresAt, usedAt);
    }

    public static String hash(String rawValue) {
        return Digests.sha256Url(rawValue);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean wasUsed() {
        return usedAt != null;
    }

    public UUID id() { return id; }
    public SessionId sessionId() { return sessionId; }
    public String tokenHash() { return tokenHash; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant usedAt() { return usedAt; }
}
