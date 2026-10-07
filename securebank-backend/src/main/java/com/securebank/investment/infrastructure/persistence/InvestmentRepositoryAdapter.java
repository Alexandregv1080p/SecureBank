package com.securebank.investment.infrastructure.persistence;

import com.securebank.investment.application.InvestmentProductRepository;
import com.securebank.investment.application.InvestmentRepository;
import com.securebank.investment.domain.Investment;
import com.securebank.investment.domain.InvestmentProduct;
import com.securebank.investment.domain.InvestmentStatus;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvestmentId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class InvestmentRepositoryAdapter implements InvestmentRepository, InvestmentProductRepository {

    private final EntityManager em;

    InvestmentRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Investment> findById(InvestmentId id) {
        return Optional.ofNullable(em.find(InvestmentEntity.class, id.value())).map(InvestmentEntity::toDomain);
    }

    @Override
    public List<Investment> findByCustomer(CustomerId customerId) {
        // ACTIVE vem antes de REDEEMED na ordem alfabética
        return em.createQuery("select i from InvestmentEntity i where i.customerId = :customer"
                        + " order by i.status, i.appliedAt desc, i.id", InvestmentEntity.class)
                .setParameter("customer", customerId.value())
                .setMaxResults(200)
                .getResultList().stream().map(InvestmentEntity::toDomain).toList();
    }

    @Override
    public long countActiveByCustomer(CustomerId customerId) {
        return em.createQuery("select count(i) from InvestmentEntity i where i.customerId = :customer"
                        + " and i.status = :status", Long.class)
                .setParameter("customer", customerId.value())
                .setParameter("status", InvestmentStatus.ACTIVE)
                .getSingleResult();
    }

    @Override
    public void save(Investment investment) {
        InvestmentEntity entity = em.find(InvestmentEntity.class, investment.id().value());
        if (entity == null) {
            entity = new InvestmentEntity();
            entity.apply(investment);
            em.persist(entity);
        } else {
            entity.apply(investment);
        }
    }

    @Override
    public List<InvestmentProduct> findActive() {
        return em.createQuery("select p from InvestmentProductEntity p where p.active = true"
                        + " order by p.kind, p.termDays nulls first, p.code", InvestmentProductEntity.class)
                .getResultList().stream().map(InvestmentProductEntity::toDomain).toList();
    }

    @Override
    public Optional<InvestmentProduct> findByCode(String code) {
        return Optional.ofNullable(em.find(InvestmentProductEntity.class, code)).map(InvestmentProductEntity::toDomain);
    }
}
