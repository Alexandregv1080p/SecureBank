package com.securebank.notification.infrastructure.web;

import com.securebank.notification.application.NotificationService;
import com.securebank.notification.domain.Notification;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.infrastructure.web.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    record NotificationResponse(UUID id, String type, String title, String body, Instant createdAt, boolean read) {

        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.id(), n.type(), n.title(), n.body(), n.createdAt(), n.readAt() != null);
        }
    }

    record NotificationPage(List<NotificationResponse> items, int page, int size, long totalElements) {}

    private final NotificationService notifications;
    private final CurrentUser current;

    NotificationController(NotificationService notifications, CurrentUser current) {
        this.notifications = notifications;
        this.current = current;
    }

    @GetMapping
    NotificationPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = notifications.list(current.customerId(), page, size);
        return new NotificationPage(result.items().stream().map(NotificationResponse::of).toList(), result.page(),
                result.size(), result.totalElements());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@PathVariable String id) {
        try {
            notifications.markRead(current.customerId(), UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("Invalid notification id");
        }
    }
}
