package com.securebank.outbox.infrastructure;

import com.securebank.outbox.application.EventPublisher;
import com.securebank.outbox.application.OutboxRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
class OutboxConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxConfig.class);

    /** Com o Kafka desligado (securebank.kafka.enabled=false) os eventos só vão para o log. */
    @Bean
    @ConditionalOnProperty(name = "securebank.kafka.enabled", havingValue = "false")
    EventPublisher loggingEventPublisher() {
        return event -> log.info("event {} {}#{} id={}", event.eventType(), event.aggregateType(),
                event.aggregateId(), event.id());
    }

    @Bean
    @ConditionalOnProperty(name = "securebank.outbox.enabled", havingValue = "true", matchIfMissing = true)
    OutboxScheduler outboxScheduler(OutboxRelay relay) {
        return new OutboxScheduler(relay);
    }

    static class OutboxScheduler {

        private final OutboxRelay relay;

        OutboxScheduler(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${securebank.outbox.poll-interval-ms:1000}")
        void run() {
            try {
                while (relay.publishPending() > 0) {
                    // drena o backlog em lotes
                }
            } catch (RuntimeException e) {
                log.error("Outbox relay failed; will retry on the next tick", e);
            }
        }
    }
}
