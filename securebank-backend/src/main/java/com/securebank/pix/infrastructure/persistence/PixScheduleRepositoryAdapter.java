package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.application.PixScheduleRepository;
import com.securebank.pix.domain.PixSchedule;
import com.securebank.pix.domain.PixScheduleStatus;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.PixScheduleId;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PixScheduleRepositoryAdapter implements PixScheduleRepository {

    private final EntityManager em;

    PixScheduleRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<PixSchedule> findById(PixScheduleId id) {
        return Optional.ofNullable(em.find(PixScheduleEntity.class, id.value())).map(PixScheduleEntity::toDomain);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<PixSchedule> lockDue(PixScheduleId id, LocalDate today) {
        List<PixScheduleEntity> rows = em.createNativeQuery("select * from pix_schedules where id = :id"
                        + " and status = 'SCHEDULED' and scheduled_for <= :today for update skip locked", PixScheduleEntity.class)
                .setParameter("id", id.value()).setParameter("today", today).getResultList();
        return rows.stream().findFirst().map(PixScheduleEntity::toDomain);
    }

    @Override
    public List<PixScheduleId> findDueIds(LocalDate today, int limit) {
        return em.createQuery("select s.id from PixScheduleEntity s where s.status = :status and s.scheduledFor <= :today"
                        + " order by s.scheduledFor, s.createdAt", UUID.class)
                .setParameter("status", PixScheduleStatus.SCHEDULED).setParameter("today", today)
                .setMaxResults(limit).getResultList().stream().map(PixScheduleId::new).toList();
    }

    @Override
    public PageResult<PixSchedule> findByCustomer(CustomerId customerId, int page, int size) {
        List<PixSchedule> items = em.createQuery("select s from PixScheduleEntity s where s.customerId = :customer"
                        + " order by s.scheduledFor desc, s.createdAt desc, s.id desc", PixScheduleEntity.class)
                .setParameter("customer", customerId.value())
                .setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(PixScheduleEntity::toDomain).toList();
        long total = em.createQuery("select count(s) from PixScheduleEntity s where s.customerId = :customer", Long.class)
                .setParameter("customer", customerId.value()).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    @Override
    public long countPendingByCustomer(CustomerId customerId) {
        return em.createQuery("select count(s) from PixScheduleEntity s where s.customerId = :customer"
                        + " and s.status = :status", Long.class)
                .setParameter("customer", customerId.value()).setParameter("status", PixScheduleStatus.SCHEDULED)
                .getSingleResult();
    }

    @Override
    public void save(PixSchedule schedule) {
        PixScheduleEntity entity = em.find(PixScheduleEntity.class, schedule.id().value());
        if (entity == null) {
            entity = new PixScheduleEntity();
            entity.apply(schedule);
            em.persist(entity);
        } else {
            entity.apply(schedule);
        }
    }
}
