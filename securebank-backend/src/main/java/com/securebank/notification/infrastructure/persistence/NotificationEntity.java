package com.securebank.notification.infrastructure.persistence;

import com.securebank.notification.domain.Notification;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
class NotificationEntity {

    @Id UUID id;
    UUID customerId;
    String type;
    String title;
    String body;
    long eventId;
    Instant createdAt;
    Instant readAt;

    static NotificationEntity of(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.id = n.id();
        e.customerId = n.customerId().value();
        e.type = n.type();
        e.title = n.title();
        e.body = n.body();
        e.eventId = n.eventId();
        e.createdAt = n.createdAt();
        e.readAt = n.readAt();
        return e;
    }

    Notification toDomain() {
        return new Notification(id, new CustomerId(customerId), type, title, body, eventId, createdAt, readAt);
    }
}
