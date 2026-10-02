package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.application.RefreshTokenRepository;
import com.securebank.authentication.domain.RefreshToken;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final EntityManager em;

    RefreshTokenRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return em.createQuery("select t from RefreshTokenEntity t where t.tokenHash = :hash", RefreshTokenEntity.class)
                .setParameter("hash", tokenHash).getResultStream().findFirst().map(RefreshTokenEntity::toDomain);
    }

    @Override
    public void save(RefreshToken token) {
        em.persist(RefreshTokenEntity.of(token));
    }

    /** UPDATE condicional: de duas requisições simultâneas com o mesmo token, só uma vê 1 linha afetada. */
    @Override
    public boolean markUsed(UUID tokenId, Instant now) {
        return em.createQuery("update RefreshTokenEntity t set t.usedAt = :now where t.id = :id and t.usedAt is null")
                .setParameter("now", now)
                .setParameter("id", tokenId)
                .executeUpdate() == 1;
    }
}
