package com.securebank.outbox.application;

import com.securebank.outbox.domain.OutboxEvent;
import com.securebank.shared.application.BankTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica os eventos pendentes, em ordem. Cada lote roda numa transação que trava as linhas (SKIP LOCKED): várias
 * instâncias da API podem rodar o relay sem publicar o mesmo evento duas vezes ao mesmo tempo. Se a publicação falha,
 * o lote para (preserva a ordem) e o evento segue pendente. Se a JVM cai entre publicar e confirmar, o evento sai de
 * novo: entrega at-least-once.
 */
@Service
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH = 100;

    private final OutboxRepository events;
    private final EventPublisher publisher;
    private final BankTime time;

    public OutboxRelay(OutboxRepository events, EventPublisher publisher, BankTime time) {
        this.events = events;
        this.publisher = publisher;
        this.time = time;
    }

    /** Devolve quantos eventos foram publicados neste lote. */
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = events.lockPending(BATCH);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                publisher.publish(event);
            } catch (RuntimeException e) {
                log.warn("Outbox event {} ({}) not published, will retry: {}", event.id(), event.eventType(),
                        e.getMessage());
                events.markFailed(event.id());
                break;
            }
            events.markPublished(event.id(), time.now());
            published++;
        }
        return published;
    }
}
