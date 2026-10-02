package com.securebank.authentication.domain;

import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/** Dispositivo TOTP de um usuário. O segredo fica cifrado ({@code secretCipher}); nasce PENDING até o 1º código válido. */
public final class MfaDevice {

    private final UUID id;
    private final UserId userId;
    private final String secretCipher;
    private MfaStatus status;
    private long lastUsedStep;
    private final Instant createdAt;
    private Instant confirmedAt;

    private MfaDevice(UUID id, UserId userId, String secretCipher, MfaStatus status, long lastUsedStep,
            Instant createdAt, Instant confirmedAt) {
        this.id = id;
        this.userId = userId;
        this.secretCipher = secretCipher;
        this.status = status;
        this.lastUsedStep = lastUsedStep;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
    }

    public static MfaDevice pending(UserId userId, String secretCipher, Instant now) {
        if (userId == null || secretCipher == null || secretCipher.isBlank() || now == null) {
            throw new InvalidValueException("MFA device requires user, secret and time");
        }
        return new MfaDevice(UUID.randomUUID(), userId, secretCipher, MfaStatus.PENDING, 0, now, null);
    }

    public static MfaDevice restore(UUID id, UserId userId, String secretCipher, MfaStatus status, long lastUsedStep,
            Instant createdAt, Instant confirmedAt) {
        return new MfaDevice(id, userId, secretCipher, status, lastUsedStep, createdAt, confirmedAt);
    }

    /** Primeiro código válido liga o MFA; o passo já fica consumido para não ser reaproveitado no login. */
    public void activate(long step, Instant now) {
        if (status != MfaStatus.PENDING) {
            throw new InvalidStateTransitionException("MfaDevice", status, MfaStatus.ACTIVE);
        }
        this.status = MfaStatus.ACTIVE;
        this.lastUsedStep = step;
        this.confirmedAt = now;
    }

    public void recordUse(long step) {
        if (step <= lastUsedStep) {
            throw new InvalidValueException("TOTP step was already used");
        }
        this.lastUsedStep = step;
    }

    public boolean isActive() {
        return status == MfaStatus.ACTIVE;
    }

    public UUID id() { return id; }
    public UserId userId() { return userId; }
    public String secretCipher() { return secretCipher; }
    public MfaStatus status() { return status; }
    public long lastUsedStep() { return lastUsedStep; }
    public Instant createdAt() { return createdAt; }
    public Instant confirmedAt() { return confirmedAt; }
}
