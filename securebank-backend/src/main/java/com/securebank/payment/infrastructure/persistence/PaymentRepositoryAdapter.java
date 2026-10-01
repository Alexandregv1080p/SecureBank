package com.securebank.payment.infrastructure.persistence;

import com.securebank.payment.application.PaymentRepository;
import com.securebank.payment.domain.Payment;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.PaymentId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PaymentRepositoryAdapter implements PaymentRepository {

    private final EntityManager em;

    PaymentRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return Optional.ofNullable(em.find(PaymentEntity.class, id.value())).map(PaymentEntity::toDomain);
    }

    @Override
    public boolean existsByAccountAndKey(AccountId accountId, IdempotencyKey key) {
        return em.createQuery("select count(p) from PaymentEntity p"
                        + " where p.accountId = :account and p.idempotencyKey = :key", Long.class)
                .setParameter("account", accountId.value())
                .setParameter("key", key.value())
                .getSingleResult() > 0;
    }

    @Override
    public PageResult<Payment> findByAccounts(List<AccountId> accountIds, int page, int size) {
        List<UUID> ids = accountIds.stream().map(AccountId::value).toList();
        List<Payment> items = em
                .createQuery("select p from PaymentEntity p where p.accountId in :ids"
                        + " order by p.createdAt desc, p.id desc", PaymentEntity.class)
                .setParameter("ids", ids)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream().map(PaymentEntity::toDomain).toList();
        long total = em.createQuery("select count(p) from PaymentEntity p where p.accountId in :ids", Long.class)
                .setParameter("ids", ids).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    @Override
    public void save(Payment payment) {
        PaymentEntity entity = em.find(PaymentEntity.class, payment.id().value());
        if (entity == null) {
            entity = new PaymentEntity();
            entity.apply(payment);
            em.persist(entity);
        } else {
            entity.apply(payment);
        }
    }
}
