package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.securebank.outbox.application.EventPublisher;
import com.securebank.outbox.domain.OutboxEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** O agendador do outbox publica sozinho, sem ninguém chamar o relay (o caminho de produção). */
@SpringBootTest(properties = {"securebank.outbox.enabled=true", "securebank.outbox.poll-interval-ms=200"})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, OutboxSchedulerIT.Capture.class})
class OutboxSchedulerIT {

    static class Collecting implements EventPublisher {
        final List<OutboxEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(OutboxEvent event) {
            events.add(event);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Capture {
        @Bean
        @Primary
        Collecting collecting() {
            return new Collecting();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Collecting publisher;

    @Test
    void pendingEventsArePublishedByTheScheduleAndMarkedAsPublished() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc); // gera UserLoggedIn

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(publisher.events).anyMatch(e -> e.eventType().equals("UserLoggedIn")
                    && login.userId().equals(e.aggregateId()));
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where published_at is null", Integer.class))
                    .isZero();
        });
    }
}
