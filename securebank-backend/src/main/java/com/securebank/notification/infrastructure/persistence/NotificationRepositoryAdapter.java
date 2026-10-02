package com.securebank.notification.infrastructure.persistence;

import com.securebank.notification.application.NotificationRepository;
import com.securebank.notification.domain.Notification;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepositoryAdapter implements NotificationRepository {

    private final EntityManager em;

    NotificationRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(Notification notification) {
        em.persist(NotificationEntity.of(notification));
    }

    @Override
    public PageResult<Notification> findByCustomer(CustomerId customerId, int page, int size) {
        List<Notification> items = em.createQuery("select n from NotificationEntity n where n.customerId = :c"
                        + " order by n.createdAt desc, n.id desc", NotificationEntity.class)
                .setParameter("c", customerId.value()).setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(NotificationEntity::toDomain).toList();
        long total = em.createQuery("select count(n) from NotificationEntity n where n.customerId = :c", Long.class)
                .setParameter("c", customerId.value()).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    @Override
    public boolean markRead(UUID id, CustomerId customerId, Instant now) {
        return em.createQuery("update NotificationEntity n set n.readAt = coalesce(n.readAt, :now)"
                        + " where n.id = :id and n.customerId = :c")
                .setParameter("now", now).setParameter("id", id).setParameter("c", customerId.value())
                .executeUpdate() == 1;
    }
}
