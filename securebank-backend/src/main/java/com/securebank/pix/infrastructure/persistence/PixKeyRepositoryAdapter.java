package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.application.PixKeyRepository;
import com.securebank.pix.domain.PixKey;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.PixKeyId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class PixKeyRepositoryAdapter implements PixKeyRepository {

    private final EntityManager em;

    PixKeyRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<PixKey> findById(PixKeyId id) {
        return Optional.ofNullable(em.find(PixKeyEntity.class, id.value())).map(PixKeyEntity::toDomain);
    }

    @Override
    public Optional<PixKey> findByValue(String value) {
        return em.createQuery("select k from PixKeyEntity k where k.value = :value", PixKeyEntity.class)
                .setParameter("value", value).getResultStream().findFirst().map(PixKeyEntity::toDomain);
    }

    @Override
    public List<PixKey> findByCustomer(CustomerId customerId) {
        return em.createQuery("select k from PixKeyEntity k where k.customerId = :customer order by k.createdAt, k.id",
                        PixKeyEntity.class)
                .setParameter("customer", customerId.value())
                .getResultList().stream().map(PixKeyEntity::toDomain).toList();
    }

    @Override
    public long countByCustomer(CustomerId customerId) {
        return em.createQuery("select count(k) from PixKeyEntity k where k.customerId = :customer", Long.class)
                .setParameter("customer", customerId.value()).getSingleResult();
    }

    @Override
    public void save(PixKey key) {
        PixKeyEntity entity = new PixKeyEntity();
        entity.apply(key);
        em.persist(entity); // a chave é imutável: só se cria e se apaga
    }

    @Override
    public void delete(PixKeyId id) {
        em.createQuery("delete from PixKeyEntity k where k.id = :id").setParameter("id", id.value()).executeUpdate();
    }
}
