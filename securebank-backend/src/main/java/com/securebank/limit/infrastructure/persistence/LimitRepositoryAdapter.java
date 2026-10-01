package com.securebank.limit.infrastructure.persistence;

import com.securebank.limit.application.LimitRepository;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.AccountId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LimitRepositoryAdapter implements LimitRepository {

    private final EntityManager em;

    LimitRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Limit> find(AccountId accountId, LimitType type) {
        return Optional.ofNullable(em.find(LimitEntity.class, new LimitEntity.Key(accountId.value(), type)))
                .map(LimitEntity::toDomain);
    }

    @Override
    public List<Limit> findAll(AccountId accountId) {
        return em.createQuery("select l from LimitEntity l where l.accountId = :account", LimitEntity.class)
                .setParameter("account", accountId.value())
                .getResultList().stream().map(LimitEntity::toDomain).toList();
    }

    @Override
    public void save(Limit limit) {
        LimitEntity entity = em.find(LimitEntity.class, new LimitEntity.Key(limit.accountId().value(), limit.type()));
        if (entity == null) {
            entity = new LimitEntity();
            entity.apply(limit);
            em.persist(entity);
        } else {
            entity.apply(limit);
        }
    }
}
