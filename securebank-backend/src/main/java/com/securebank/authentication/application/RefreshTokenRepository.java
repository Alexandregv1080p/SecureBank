package com.securebank.authentication.application;

import com.securebank.authentication.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByHash(String tokenHash);

    void save(RefreshToken token);

    /**
     * Consome o token de forma atômica (UPDATE ... WHERE used_at IS NULL).
     * @return false se ele já havia sido usado — a assinatura de um token roubado sendo reapresentado
     */
    boolean markUsed(UUID tokenId, Instant now);
}
