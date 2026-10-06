package com.securebank.piggy.infrastructure.persistence;

import com.securebank.piggy.application.PiggyRepository;
import com.securebank.piggy.domain.Piggy;
import com.securebank.piggy.domain.PiggyStatus;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.PiggyId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class PiggyRepositoryAdapter implements PiggyRepository {

    private final EntityManager em;

    PiggyRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Piggy> findById(PiggyId id) {
        return Optional.ofNullable(em.find(PiggyEntity.class, id.value())).map(PiggyEntity::toDomain);
    }

    @Override
    public List<Piggy> findActiveByCustomer(CustomerId customerId) {
        return em.createQuery("select p from PiggyEntity p where p.customerId = :customer and p.status = :status"
                        + " order by p.createdAt, p.id", PiggyEntity.class)
                .setParameter("customer", customerId.value())
                .setParameter("status", PiggyStatus.ACTIVE)
                .getResultList().stream().map(PiggyEntity::toDomain).toList();
    }

    @Override
    public long countActiveByCustomer(CustomerId customerId) {
        return em.createQuery("select count(p) from PiggyEntity p where p.customerId = :customer"
                        + " and p.status = :status", Long.class)
                .setParameter("customer", customerId.value())
                .setParameter("status", PiggyStatus.ACTIVE)
                .getSingleResult();
    }

    @Override
    public void save(Piggy piggy) {
        PiggyEntity entity = em.find(PiggyEntity.class, piggy.id().value());
        if (entity == null) {
            entity = new PiggyEntity();
            entity.apply(piggy);
            em.persist(entity);
        } else {
            entity.apply(piggy);
        }
    }
}
