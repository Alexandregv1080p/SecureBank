package com.securebank;

import static com.securebank.TestUsers.bearer;
import static com.securebank.TestUsers.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.securebank.outbox.application.OutboxRelay;
import com.securebank.outbox.domain.EventEnvelope;
import com.securebank.outbox.domain.EventTopics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/** Kafka de verdade: publicação pelo outbox, consumidor idempotente, retry e Dead Letter Topic. */
@SpringBootTest(properties = {"securebank.kafka.enabled=true", "spring.autoconfigure.exclude="})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, KafkaMessagingTest.Broker.class})
class KafkaMessagingTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Broker {
        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.1.1");
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxRelay relay;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired KafkaContainer broker;
    @Autowired JsonMapper json;

    private void drainOutbox() {
        while (relay.publishPending() > 0) {
            // publica tudo que está pendente, em lotes
        }
    }

    private List<String> notificationTypes(String token) throws Exception {
        String body = mvc.perform(bearer(token, get("/api/v1/notifications?size=100"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].type");
    }

    @Test
    void aTransferBecomesNotificationsForBothCustomersThroughKafka() throws Exception {
        TestUsers.Login sender = TestUsers.registerAndLogin(mvc);
        TestUsers.Login receiver = TestUsers.registerAndLogin(mvc);
        String from = openAccount(sender.accessToken());
        MvcResult to = openAccountResult(receiver.accessToken());
        deposit(sender.accessToken(), from, "100.00");
        transfer(sender.accessToken(), from, to, "30.00");

        drainOutbox(); // outbox → Kafka (o consumidor da aplicação lê de lá)

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            assertThat(notificationTypes(sender.accessToken())).contains("TRANSFER_SENT", "NEW_LOGIN");
            assertThat(notificationTypes(receiver.accessToken())).contains("TRANSFER_RECEIVED", "NEW_LOGIN");
        });
        // cada cliente só vê os próprios avisos
        assertThat(notificationTypes(sender.accessToken())).doesNotContain("TRANSFER_RECEIVED");

        // marcar como lida (e não a de outro cliente)
        String body = mvc.perform(bearer(sender.accessToken(), get("/api/v1/notifications"))).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.items[0].id");
        mvc.perform(bearer(receiver.accessToken(), post("/api/v1/notifications/" + id + "/read"))).andExpect(status().isNotFound());
        mvc.perform(bearer(sender.accessToken(), post("/api/v1/notifications/" + id + "/read"))).andExpect(status().isNoContent());
    }

    @Test
    void redeliveringAnEventDoesNotDuplicateTheEffect() throws Exception {
        TestUsers.Login owner = TestUsers.registerAndLogin(mvc);
        String account = openAccount(owner.accessToken());
        String key = account; // mesma chave = mesma partição = processadas em ordem

        EventEnvelope blocked = envelope(1, "AccountBlocked", "Account", account, Map.of("accountId", account));
        send(EventTopics.ACCOUNTS, key, json.writeValueAsString(blocked));
        send(EventTopics.ACCOUNTS, key, json.writeValueAsString(blocked)); // reentrega do mesmo eventId
        send(EventTopics.ACCOUNTS, key, json.writeValueAsString(blocked));
        EventEnvelope sentinel = envelope(2, "AccountUnblocked", "Account", account, Map.of("accountId", account));
        send(EventTopics.ACCOUNTS, key, json.writeValueAsString(sentinel));

        // quando o sentinela (depois das duplicatas, mesma partição) aparece, as duplicatas já passaram
        await().atMost(Duration.ofSeconds(40)).untilAsserted(
                () -> assertThat(notificationTypes(owner.accessToken())).contains("ACCOUNT_UNBLOCKED"));
        assertThat(notificationTypes(owner.accessToken()).stream().filter("ACCOUNT_BLOCKED"::equals)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from processed_events where consumer = 'notifications'"
                + " and event_id = ?", Integer.class, blocked.eventId())).isEqualTo(1);
    }

    @Test
    void aPoisonMessageGoesToTheDeadLetterTopicAndTheConsumerKeepsGoing() throws Exception {
        TestUsers.Login owner = TestUsers.registerAndLogin(mvc);
        String account = openAccount(owner.accessToken());
        String poison = "{this is not json " + UUID.randomUUID();

        send(EventTopics.ACCOUNTS, account, poison);
        EventEnvelope valid = envelope(3, "AccountBlocked", "Account", account, Map.of("accountId", account));
        send(EventTopics.ACCOUNTS, account, json.writeValueAsString(valid));

        // a mensagem venenosa chegou ao DLT, com a causa nos headers...
        ConsumerRecord<String, String> dead = awaitDeadLetter(EventTopics.ACCOUNTS + EventTopics.DLT_SUFFIX, poison);
        assertThat(dead.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
        assertThat(dead.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
        // ...e a partição não travou: a mensagem válida seguinte foi processada
        await().atMost(Duration.ofSeconds(40)).untilAsserted(
                () -> assertThat(notificationTypes(owner.accessToken())).contains("ACCOUNT_BLOCKED"));
    }

    @Test
    void aMalformedPayloadOfAKnownEventIsDeadLetteredToo() throws Exception {
        // envelope válido, mas o payload não tem os ids esperados: erro de dados, não adianta repetir
        EventEnvelope broken = envelope(4, "TransferCompleted", "Transfer", "t-" + UUID.randomUUID(),
                Map.of("sourceAccountId", "not-a-uuid", "destinationAccountId", "also-bad", "amount", "1.00", "currency", "BRL"));
        String value = json.writeValueAsString(broken);

        send(EventTopics.TRANSFERS, broken.aggregateId(), value);

        assertThat(awaitDeadLetter(EventTopics.TRANSFERS + EventTopics.DLT_SUFFIX, value)).isNotNull();
        // nada ficou marcado como processado: a falha desfez a transação inteira
        assertThat(jdbc.queryForObject("select count(*) from processed_events where event_id = ?", Integer.class,
                broken.eventId())).isZero();
    }

    // ---------- helpers ----------

    private EventEnvelope envelope(int salt, String type, String aggregateType, String aggregateId, Map<String, Object> payload) {
        long eventId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 10 + salt;
        return new EventEnvelope(eventId, type, aggregateType, aggregateId, Instant.now(), payload);
    }

    private void send(String topic, String key, String value) throws Exception {
        kafka.send(topic, key, value).get();
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String dlt, String expectedValue) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            org.apache.kafka.common.TopicPartition partition = new org.apache.kafka.common.TopicPartition(dlt, 0);
            consumer.assign(List.of(partition)); // sem grupo: lê a partição única do DLT desde o início
            consumer.seekToBeginning(List.of(partition));
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (expectedValue.equals(record.value())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("Message not found in " + dlt);
    }

    private MvcResult openAccountResult(String token) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn();
    }

    private String openAccount(String token) throws Exception {
        return read(openAccountResult(token), "$.id");
    }

    private void deposit(String token, String account, String amount) throws Exception {
        mvc.perform(bearer(token, post("/api/v1/accounts/" + account + "/deposits"))
                .header("Idempotency-Key", "idem-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"" + amount + "\"}")).andExpect(status().isCreated());
    }

    private void transfer(String token, String from, MvcResult destination, String amount) throws Exception {
        mvc.perform(bearer(token, post("/api/v1/transfers")).header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\":\"%s\",\"destinationBranch\":\"%s\","
                                + "\"destinationAccountNumber\":\"%s\",\"amount\":\"%s\"}", from,
                        read(destination, "$.branch"), read(destination, "$.accountNumber"), amount)))
                .andExpect(status().isCreated());
    }
}
