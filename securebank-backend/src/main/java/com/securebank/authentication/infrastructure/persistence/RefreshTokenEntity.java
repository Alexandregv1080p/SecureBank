package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.domain.RefreshToken;
import com.securebank.shared.domain.SessionId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity {

    @Id UUID id;
    UUID sessionId;
    String tokenHash;
    Instant createdAt;
    Instant expiresAt;
    Instant usedAt;

    static RefreshTokenEntity of(RefreshToken token) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.id = token.id();
        entity.sessionId = token.sessionId().value();
        entity.tokenHash = token.tokenHash();
        entity.createdAt = token.createdAt();
        entity.expiresAt = token.expiresAt();
        entity.usedAt = token.usedAt();
        return entity;
    }

    RefreshToken toDomain() {
        return RefreshToken.restore(id, new SessionId(sessionId), tokenHash, createdAt, expiresAt, usedAt);
    }
}
