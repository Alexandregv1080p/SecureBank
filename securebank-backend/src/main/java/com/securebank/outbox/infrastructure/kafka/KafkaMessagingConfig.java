package com.securebank.outbox.infrastructure.kafka;

import com.securebank.outbox.domain.EventTopics;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

@Configuration
@ConditionalOnProperty(name = "securebank.kafka.enabled", havingValue = "true", matchIfMissing = true)
class KafkaMessagingConfig {

    private static final int PARTITIONS = 3;

    /** Tópicos criados na subida (idempotente). O DLT tem uma partição: é uma fila de triagem, não de volume. */
    @Bean
    org.springframework.kafka.core.KafkaAdmin.NewTopics eventTopics() {
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : EventTopics.ALL) {
            topics.add(TopicBuilder.name(topic).partitions(PARTITIONS).replicas(1).build());
            topics.add(TopicBuilder.name(topic + EventTopics.DLT_SUFFIX).partitions(1).replicas(1).build());
        }
        return new org.springframework.kafka.core.KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    /**
     * Falha de processamento: 3 novas tentativas com espera exponencial (500 ms, 1 s, 2 s) e, esgotadas, a mensagem vai
     * para {@code <tópico>.DLT} com a exceção nos headers — o consumidor segue adiante em vez de travar a partição.
     * Mensagem malformada não adianta repetir: vai direto para o DLT.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + EventTopics.DLT_SUFFIX, 0));
        ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(3);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(4000);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class,
                com.securebank.shared.domain.InvalidValueException.class);
        return handler;
    }
}
