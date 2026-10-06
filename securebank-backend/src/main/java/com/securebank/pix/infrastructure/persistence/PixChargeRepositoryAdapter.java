package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.application.PixChargeRepository;
import com.securebank.pix.domain.PixCharge;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class PixChargeRepositoryAdapter implements PixChargeRepository {

    private final EntityManager em;

    PixChargeRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<PixCharge> findByTxid(String txid) {
        return Optional.ofNullable(em.find(PixChargeEntity.class, txid)).map(PixChargeEntity::toDomain);
    }

    @Override
    public PageResult<PixCharge> findByCustomer(CustomerId customerId, int page, int size) {
        List<PixCharge> items = em.createQuery("select c from PixChargeEntity c where c.customerId = :customer"
                        + " order by c.createdAt desc, c.txid desc", PixChargeEntity.class)
                .setParameter("customer", customerId.value())
                .setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(PixChargeEntity::toDomain).toList();
        long total = em.createQuery("select count(c) from PixChargeEntity c where c.customerId = :customer", Long.class)
                .setParameter("customer", customerId.value()).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    @Override
    public void save(PixCharge charge) {
        PixChargeEntity entity = em.find(PixChargeEntity.class, charge.txid());
        if (entity == null) {
            entity = new PixChargeEntity();
            entity.apply(charge);
            em.persist(entity);
        } else {
            entity.apply(charge);
        }
    }
}
