package com.securebank.outbox.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Formato do evento no Kafka (valor da mensagem). {@code eventId} é o id do outbox: único no sistema, é a chave de
 * deduplicação dos consumidores.
 */
public record EventEnvelope(long eventId, String eventType, String aggregateType, String aggregateId,
        Instant occurredAt, Map<String, Object> payload) {

    public static EventEnvelope of(OutboxEvent e) {
        return new EventEnvelope(e.id(), e.eventType(), e.aggregateType(), e.aggregateId(), e.occurredAt(), e.payload());
    }

    public String text(String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }
}
