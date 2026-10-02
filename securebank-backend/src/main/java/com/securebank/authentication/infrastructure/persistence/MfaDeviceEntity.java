package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.domain.MfaDevice;
import com.securebank.authentication.domain.MfaStatus;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_devices")
class MfaDeviceEntity {

    @Id UUID id;
    UUID userId;
    String secretCipher;
    @Enumerated(EnumType.STRING) MfaStatus status;
    long lastUsedStep;
    Instant createdAt;
    Instant confirmedAt;

    void apply(MfaDevice device) {
        id = device.id();
        userId = device.userId().value();
        secretCipher = device.secretCipher();
        status = device.status();
        lastUsedStep = device.lastUsedStep();
        createdAt = device.createdAt();
        confirmedAt = device.confirmedAt();
    }

    MfaDevice toDomain() {
        return MfaDevice.restore(id, new UserId(userId), secretCipher, status, lastUsedStep, createdAt, confirmedAt);
    }
}
