package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.domain.Session;
import com.securebank.authentication.domain.SessionRevocation;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
class SessionEntity {

    @Id UUID id;
    UUID userId;
    boolean mfaVerified;
    Instant createdAt;
    Instant lastUsedAt;
    Instant expiresAt;
    Instant revokedAt;
    @Enumerated(EnumType.STRING) SessionRevocation revocation;
    String ip;
    String userAgent;

    void apply(Session session) {
        id = session.id().value();
        userId = session.userId().value();
        mfaVerified = session.mfaVerified();
        createdAt = session.createdAt();
        lastUsedAt = session.lastUsedAt();
        expiresAt = session.expiresAt();
        revokedAt = session.revokedAt();
        revocation = session.revocation();
        ip = session.ip();
        userAgent = session.userAgent();
    }

    Session toDomain() {
        return Session.restore(new SessionId(id), new UserId(userId), mfaVerified, createdAt, lastUsedAt, expiresAt,
                revokedAt, revocation, ip, userAgent);
    }
}
