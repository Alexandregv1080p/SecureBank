package com.securebank.outbox.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    String eventType;
    String aggregateType;
    String aggregateId;
    String payload;
    Instant occurredAt;
    Instant publishedAt;
    int attempts;
}
