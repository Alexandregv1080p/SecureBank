package com.securebank.audit.infrastructure.persistence;

import com.securebank.audit.application.AuditLogRepository;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.audit.domain.AuditLog;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final EntityManager em;

    AuditLogRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(AuditLog log) {
        em.persist(AuditLogEntity.of(log));
    }

    @Override
    public PageResult<AuditLog> search(AuditEvent event, UserId userId, int page, int size) {
        String where = " where (:event is null or a.event = :event) and (:user is null or a.userId = :user)";
        List<AuditLog> items = bind(em.createQuery("select a from AuditLogEntity a" + where
                + " order by a.occurredAt desc, a.id desc", AuditLogEntity.class), event, userId)
                .setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(AuditLogEntity::toDomain).toList();
        long total = bind(em.createQuery("select count(a) from AuditLogEntity a" + where, Long.class), event, userId)
                .getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    private static <T> TypedQuery<T> bind(TypedQuery<T> query, AuditEvent event, UserId userId) {
        return query.setParameter("event", event).setParameter("user", userId == null ? null : userId.value());
    }
}
