package com.securebank.authentication.domain;

import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;

/**
 * Sessão de login (um dispositivo/navegador). Os refresh tokens pertencem a ela; revogá-la invalida todos eles
 * e, via denylist, os access tokens já emitidos. Tem vida máxima absoluta, independente de renovações.
 */
public final class Session {

    private static final int MAX_USER_AGENT = 200;

    private final SessionId id;
    private final UserId userId;
    private final boolean mfaVerified;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private SessionRevocation revocation;
    private final String ip;
    private final String userAgent;

    private Session(SessionId id, UserId userId, boolean mfaVerified, Instant createdAt, Instant lastUsedAt,
            Instant expiresAt, Instant revokedAt, SessionRevocation revocation, String ip, String userAgent) {
        this.id = id;
        this.userId = userId;
        this.mfaVerified = mfaVerified;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.revocation = revocation;
        this.ip = ip;
        this.userAgent = userAgent;
    }

    /** @param ip já mascarado (a sessão é mostrada ao próprio usuário e não deve guardar o IP completo) */
    public static Session start(UserId userId, boolean mfaVerified, String ip, String userAgent, Duration maxLifetime,
            Instant now) {
        if (userId == null || maxLifetime == null || maxLifetime.isNegative() || maxLifetime.isZero() || now == null) {
            throw new InvalidValueException("Session requires user, positive lifetime and time");
        }
        String agent = userAgent == null ? null
                : userAgent.length() > MAX_USER_AGENT ? userAgent.substring(0, MAX_USER_AGENT) : userAgent;
        return new Session(SessionId.newId(), userId, mfaVerified, now, now, now.plus(maxLifetime), null, null, ip,
                agent);
    }

    public static Session restore(SessionId id, UserId userId, boolean mfaVerified, Instant createdAt,
            Instant lastUsedAt, Instant expiresAt, Instant revokedAt, SessionRevocation revocation, String ip,
            String userAgent) {
        return new Session(id, userId, mfaVerified, createdAt, lastUsedAt, expiresAt, revokedAt, revocation, ip,
                userAgent);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }

    /** Idempotente: revogar de novo mantém o primeiro motivo. */
    public void revoke(SessionRevocation reason, Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revocation = reason;
        }
    }

    public SessionId id() { return id; }
    public UserId userId() { return userId; }
    public boolean mfaVerified() { return mfaVerified; }
    public Instant createdAt() { return createdAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public SessionRevocation revocation() { return revocation; }
    public String ip() { return ip; }
    public String userAgent() { return userAgent; }
}
