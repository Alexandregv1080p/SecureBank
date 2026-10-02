package com.securebank.outbox.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Evento de domínio a publicar. Nasce na mesma transação da operação que o causou; o payload leva ids e valores,
 * nunca dado pessoal (CPF, e-mail, telefone) nem segredo.
 */
public record OutboxEvent(Long id, String eventType, String aggregateType, String aggregateId,
        Map<String, Object> payload, Instant occurredAt, int attempts) {

    public static OutboxEvent create(String eventType, String aggregateType, String aggregateId,
            Map<String, Object> payload, Instant now) {
        return new OutboxEvent(null, eventType, aggregateType, aggregateId, Map.copyOf(payload), now, 0);
    }
}
