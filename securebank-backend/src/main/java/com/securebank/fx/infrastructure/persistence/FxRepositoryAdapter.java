package com.securebank.fx.infrastructure.persistence;

import com.securebank.fx.application.FxOperationRepository;
import com.securebank.fx.application.FxRateRepository;
import com.securebank.fx.application.FxWalletRepository;
import com.securebank.fx.domain.FxOperation;
import com.securebank.fx.domain.FxRate;
import com.securebank.fx.domain.FxWallet;
import com.securebank.fx.infrastructure.persistence.FxEntities.OperationEntity;
import com.securebank.fx.infrastructure.persistence.FxEntities.RateEntity;
import com.securebank.fx.infrastructure.persistence.FxEntities.WalletEntity;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class FxRepositoryAdapter implements FxRateRepository, FxWalletRepository, FxOperationRepository {

    private final EntityManager em;

    FxRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<FxRate> findAll() {
        return em.createQuery("select r from FxRateEntity r order by r.currency", RateEntity.class).getResultList()
                .stream().map(RateEntity::toDomain).toList();
    }

    @Override
    public Optional<FxRate> findByCurrency(String code) {
        return Optional.ofNullable(em.find(RateEntity.class, code)).map(RateEntity::toDomain);
    }

    @Override
    public void save(FxRate rate) {
        RateEntity entity = em.find(RateEntity.class, rate.currency().getCurrencyCode());
        entity.midRate = rate.mid();
        entity.spread = rate.spread();
        entity.updatedAt = rate.updatedAt();
    }

    @Override
    public Optional<FxWallet> find(CustomerId customerId, String currencyCode) {
        return em.createQuery("select w from FxWalletEntity w where w.customerId = :c and w.currency = :cur",
                        WalletEntity.class)
                .setParameter("c", customerId.value()).setParameter("cur", currencyCode)
                .getResultStream().findFirst().map(WalletEntity::toDomain);
    }

    @Override
    public List<FxWallet> findByCustomer(CustomerId customerId) {
        return em.createQuery("select w from FxWalletEntity w where w.customerId = :c order by w.currency",
                        WalletEntity.class)
                .setParameter("c", customerId.value()).getResultList().stream().map(WalletEntity::toDomain).toList();
    }

    @Override
    public void save(FxWallet wallet) {
        WalletEntity entity = em.find(WalletEntity.class, wallet.id());
        if (entity == null) {
            entity = new WalletEntity();
            entity.apply(wallet);
            em.persist(entity);
        } else {
            entity.apply(wallet);
        }
    }

    @Override
    public void save(FxOperation operation) {
        em.persist(OperationEntity.of(operation));
    }

    @Override
    public PageResult<FxOperation> findByCustomer(CustomerId customerId, int page, int size) {
        List<FxOperation> items = em.createQuery("select o from FxOperationEntity o where o.customerId = :c"
                        + " order by o.createdAt desc, o.id desc", OperationEntity.class)
                .setParameter("c", customerId.value()).setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(OperationEntity::toDomain).toList();
        long total = em.createQuery("select count(o) from FxOperationEntity o where o.customerId = :c", Long.class)
                .setParameter("c", customerId.value()).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }
}
