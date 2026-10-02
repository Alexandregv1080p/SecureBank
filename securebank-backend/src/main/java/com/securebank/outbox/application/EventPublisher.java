package com.securebank.outbox.application;

import com.securebank.outbox.domain.OutboxEvent;

/** Porta de saída dos eventos (Kafka na Fase 6). A entrega é at-least-once: consumidores precisam ser idempotentes. */
public interface EventPublisher {

    /** Lança exceção se não conseguiu publicar — o evento fica pendente e será tentado de novo. */
    void publish(OutboxEvent event);
}
