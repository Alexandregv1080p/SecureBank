package com.securebank.notification.infrastructure.kafka;

import com.securebank.notification.application.NotificationService;
import com.securebank.outbox.domain.EventEnvelope;
import com.securebank.outbox.domain.EventTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê os eventos e delega ao caso de uso. Exceções sobem de propósito: o error handler do container faz o retry e,
 * esgotado, manda a mensagem ao {@code <tópico>.DLT}.
 */
@Component
@ConditionalOnProperty(name = "securebank.kafka.enabled", havingValue = "true", matchIfMissing = true)
class NotificationConsumer {

    private final NotificationService notifications;
    private final JsonMapper json;

    NotificationConsumer(NotificationService notifications, JsonMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @KafkaListener(id = "notifications", topics = {EventTopics.TRANSFERS, EventTopics.PAYMENTS, EventTopics.ACCOUNTS,
            EventTopics.USERS})
    void on(ConsumerRecord<String, String> record) {
        notifications.handle(json.readValue(record.value(), EventEnvelope.class));
    }
}
