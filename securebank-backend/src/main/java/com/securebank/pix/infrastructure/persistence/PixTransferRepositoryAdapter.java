package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.application.PixTransferRepository;
import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PixTransferRepositoryAdapter implements PixTransferRepository {

    private final EntityManager em;

    PixTransferRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(PixTransfer pix) {
        PixTransferEntity entity = new PixTransferEntity();
        entity.apply(pix);
        em.persist(entity); // Pix concluído é imutável
    }

    @Override
    public PageResult<PixTransfer> findByAccounts(List<AccountId> accountIds, int page, int size) {
        List<UUID> ids = accountIds.stream().map(AccountId::value).toList();
        List<PixTransfer> items = em.createQuery("select p from PixTransferEntity p where p.sourceAccountId in :ids"
                        + " or p.destinationAccountId in :ids order by p.createdAt desc, p.id desc", PixTransferEntity.class)
                .setParameter("ids", ids)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream().map(PixTransferEntity::toDomain).toList();
        long total = em.createQuery("select count(p) from PixTransferEntity p where p.sourceAccountId in :ids"
                + " or p.destinationAccountId in :ids", Long.class).setParameter("ids", ids).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }
}
