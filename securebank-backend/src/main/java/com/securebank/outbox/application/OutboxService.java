package com.securebank.outbox.application;

import com.securebank.outbox.domain.OutboxEvent;
import com.securebank.shared.application.BankTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxRepository events;
    private final BankTime time;

    public OutboxService(OutboxRepository events, BankTime time) {
        this.events = events;
        this.time = time;
    }

    /** Na transação do chamador: o evento só existe se a operação foi confirmada (e some junto num rollback). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload) {
        events.save(OutboxEvent.create(eventType, aggregateType, aggregateId, payload, time.now()));
    }

    /** Para fatos que acontecem mesmo com a operação desfeita (ex.: TransferFailed). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(String eventType, String aggregateType, String aggregateId,
            Map<String, Object> payload) {
        events.save(OutboxEvent.create(eventType, aggregateType, aggregateId, payload, time.now()));
    }
}
