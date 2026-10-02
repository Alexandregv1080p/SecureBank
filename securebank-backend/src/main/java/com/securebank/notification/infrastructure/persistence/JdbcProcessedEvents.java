package com.securebank.notification.infrastructure.persistence;

import com.securebank.notification.application.ProcessedEvents;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** INSERT ... ON CONFLICT DO NOTHING na transação do consumidor: a chave primária decide quem processou primeiro. */
@Component
class JdbcProcessedEvents implements ProcessedEvents {

    private final JdbcTemplate jdbc;

    JdbcProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(String consumer, long eventId, Instant now) {
        return jdbc.update("insert into processed_events (consumer, event_id, processed_at) values (?, ?, ?)"
                + " on conflict do nothing", consumer, eventId, Timestamp.from(now)) == 1;
    }
}
