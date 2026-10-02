package com.securebank.outbox.infrastructure.persistence;

import com.securebank.outbox.application.OutboxRepository;
import com.securebank.outbox.domain.OutboxEvent;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class OutboxRepositoryAdapter implements OutboxRepository {

    private final EntityManager em;
    private final JsonMapper json;

    OutboxRepositoryAdapter(EntityManager em, JsonMapper json) {
        this.em = em;
        this.json = json;
    }

    @Override
    public void save(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.eventType = event.eventType();
        entity.aggregateType = event.aggregateType();
        entity.aggregateId = event.aggregateId();
        entity.payload = json.writeValueAsString(event.payload());
        entity.occurredAt = event.occurredAt();
        em.persist(entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OutboxEvent> lockPending(int limit) {
        List<OutboxEventEntity> rows = em.createNativeQuery("select * from outbox_events where published_at is null"
                        + " order by id limit :limit for update skip locked", OutboxEventEntity.class)
                .setParameter("limit", limit).getResultList();
        return rows.stream().map(e -> new OutboxEvent(e.id, e.eventType, e.aggregateType, e.aggregateId,
                json.readValue(e.payload, new TypeReference<Map<String, Object>>() {}), e.occurredAt,
                e.attempts)).toList();
    }

    @Override
    public void markPublished(long id, Instant now) {
        em.createQuery("update OutboxEventEntity e set e.publishedAt = :now where e.id = :id")
                .setParameter("now", now).setParameter("id", id).executeUpdate();
    }

    @Override
    public void markFailed(long id) {
        em.createQuery("update OutboxEventEntity e set e.attempts = e.attempts + 1 where e.id = :id")
                .setParameter("id", id).executeUpdate();
    }
}
