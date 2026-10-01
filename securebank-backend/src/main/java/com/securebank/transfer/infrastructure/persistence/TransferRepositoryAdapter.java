package com.securebank.transfer.infrastructure.persistence;

import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.TransferId;
import com.securebank.transfer.application.TransferRepository;
import com.securebank.transfer.domain.Transfer;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class TransferRepositoryAdapter implements TransferRepository {

    private final EntityManager em;

    TransferRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Transfer> findById(TransferId id) {
        return Optional.ofNullable(em.find(TransferEntity.class, id.value())).map(TransferEntity::toDomain);
    }

    @Override
    public boolean existsBySourceAndKey(AccountId sourceAccountId, IdempotencyKey key) {
        return em.createQuery("select count(t) from TransferEntity t"
                        + " where t.sourceAccountId = :source and t.idempotencyKey = :key", Long.class)
                .setParameter("source", sourceAccountId.value())
                .setParameter("key", key.value())
                .getSingleResult() > 0;
    }

    @Override
    public PageResult<Transfer> findBySourceAccounts(List<AccountId> sourceAccountIds, int page, int size) {
        List<UUID> ids = sourceAccountIds.stream().map(AccountId::value).toList();
        List<Transfer> items = em
                .createQuery("select t from TransferEntity t where t.sourceAccountId in :ids"
                        + " order by t.createdAt desc, t.id desc", TransferEntity.class)
                .setParameter("ids", ids)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream().map(TransferEntity::toDomain).toList();
        long total = em.createQuery("select count(t) from TransferEntity t where t.sourceAccountId in :ids",
                Long.class).setParameter("ids", ids).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    @Override
    public void save(Transfer transfer) {
        TransferEntity entity = em.find(TransferEntity.class, transfer.id().value());
        if (entity == null) {
            entity = new TransferEntity();
            entity.apply(transfer);
            em.persist(entity);
        } else {
            entity.apply(transfer);
        }
    }
}
