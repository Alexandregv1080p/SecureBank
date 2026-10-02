package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.application.MfaDeviceRepository;
import com.securebank.authentication.domain.MfaDevice;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class MfaDeviceRepositoryAdapter implements MfaDeviceRepository {

    private final EntityManager em;

    MfaDeviceRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<MfaDevice> findByUser(UserId userId) {
        return em.createQuery("select d from MfaDeviceEntity d where d.userId = :user", MfaDeviceEntity.class)
                .setParameter("user", userId.value()).getResultStream().findFirst().map(MfaDeviceEntity::toDomain);
    }

    @Override
    public void save(MfaDevice device) {
        MfaDeviceEntity entity = em.find(MfaDeviceEntity.class, device.id());
        if (entity == null) {
            entity = new MfaDeviceEntity();
            entity.apply(device);
            em.persist(entity);
        } else {
            entity.apply(device);
        }
    }

    /** Apaga e já sincroniza: um cadastro novo logo depois não pode bater na UNIQUE(user_id). */
    @Override
    public void deleteByUser(UserId userId) {
        em.createQuery("select d from MfaDeviceEntity d where d.userId = :user", MfaDeviceEntity.class)
                .setParameter("user", userId.value()).getResultStream().findFirst().ifPresent(entity -> {
                    em.remove(entity);
                    em.flush();
                });
    }
}
