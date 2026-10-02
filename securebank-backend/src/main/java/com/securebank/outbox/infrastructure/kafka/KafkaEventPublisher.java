package com.securebank.outbox.infrastructure.kafka;

import com.securebank.outbox.application.EventPublisher;
import com.securebank.outbox.domain.EventEnvelope;
import com.securebank.outbox.domain.EventTopics;
import com.securebank.outbox.domain.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica o evento do outbox no Kafka e só devolve quando o broker confirmou (acks=all + produtor idempotente).
 * Qualquer falha vira exceção: o relay mantém o evento pendente e tenta de novo.
 */
@Component
@ConditionalOnProperty(name = "securebank.kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaEventPublisher implements EventPublisher {

    private static final long TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    KafkaEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publish(OutboxEvent event) {
        String value = json.writeValueAsString(EventEnvelope.of(event));
        ProducerRecord<String, String> record = new ProducerRecord<>(EventTopics.forEvent(event.eventType()), null,
                event.aggregateId(), value);
        record.headers().add("eventId", String.valueOf(event.id()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.eventType().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing event " + event.id(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish event " + event.id(), e);
        }
    }
}
