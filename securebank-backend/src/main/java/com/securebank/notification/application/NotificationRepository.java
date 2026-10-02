package com.securebank.notification.application;

import com.securebank.notification.domain.Notification;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;
import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository {

    void save(Notification notification);

    PageResult<Notification> findByCustomer(CustomerId customerId, int page, int size);

    /** Marca como lida só se pertencer ao cliente; devolve falso se não existe ou é de outro dono. */
    boolean markRead(UUID id, CustomerId customerId, Instant now);
}
