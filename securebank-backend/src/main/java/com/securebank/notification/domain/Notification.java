package com.securebank.notification.domain;

import com.securebank.shared.domain.CustomerId;
import java.time.Instant;
import java.util.UUID;

/** Aviso ao cliente gerado a partir de um evento. Texto sem dado sensível. */
public record Notification(UUID id, CustomerId customerId, String type, String title, String body, long eventId,
        Instant createdAt, Instant readAt) {

    public static Notification create(CustomerId customerId, String type, String title, String body, long eventId,
            Instant now) {
        return new Notification(UUID.randomUUID(), customerId, type, title, body, eventId, now, null);
    }
}
