package com.securebank;

import static com.securebank.TestUsers.bearer;
import static com.securebank.TestUsers.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.securebank.outbox.application.EventPublisher;
import com.securebank.outbox.application.OutboxRelay;
import com.securebank.outbox.domain.OutboxEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Fase 5: idempotência com replay, concorrência (versão otimista + retry) e Transactional Outbox. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, ConsistencyTest.Capture.class})
class ConsistencyTest {

    static class CapturingPublisher implements EventPublisher {
        final List<OutboxEvent> published = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void publish(OutboxEvent event) {
            if (failing) {
                throw new IllegalStateException("broker down");
            }
            published.add(event);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Capture {
        @Bean
        @Primary
        CapturingPublisher capturingPublisher() {
            return new CapturingPublisher();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxRelay relay;
    @Autowired CapturingPublisher publisher;

    @BeforeEach
    void drainOutbox() {
        publisher.failing = false;
        while (relay.publishPending() > 0) {
            // começa cada teste sem pendências de testes anteriores
        }
        publisher.published.clear();
    }

    // ---------- idempotência ----------

    @Test
    void depositReplayReturnsTheSameResponseAndCreditsOnce() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = openAccount(token);
        String key = "idem-" + UUID.randomUUID();

        MvcResult first = deposit(token, account, "100.00", key).andExpect(status().isCreated()).andReturn();
        deposit(token, account, "100.00", key).andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(read(first, "$.id")));

        assertThat(balance(token, account)).isEqualTo("100.00");
        assertThat(first.getResponse().getHeader("Idempotency-Replayed")).isNull();
    }

    @Test
    void theSameKeyWithADifferentRequestIsRejected() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = openAccount(token);
        String key = "idem-" + UUID.randomUUID();

        deposit(token, account, "100.00", key).andExpect(status().isCreated());
        deposit(token, account, "999.00", key).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(balance(token, account)).isEqualTo("100.00");
    }

    @Test
    void theKeyIsRequiredOnEveryMoneyOperation() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = openAccount(token);

        for (String path : List.of("/api/v1/accounts/" + account + "/deposits", "/api/v1/accounts/" + account + "/withdrawals",
                "/api/v1/transfers", "/api/v1/payments")) {
            mvc.perform(bearer(token, post(path)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
            mvc.perform(bearer(token, post(path)).header("Idempotency-Key", "short")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        }
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        String a = TestUsers.registerAndLogin(mvc).accessToken();
        String b = TestUsers.registerAndLogin(mvc).accessToken();
        String key = "shared-key-" + UUID.randomUUID();

        deposit(a, openAccount(a), "10.00", key).andExpect(status().isCreated());
        // a mesma chave de OUTRO usuário é independente (não é replay do pedido do primeiro)
        deposit(b, openAccount(b), "20.00", key).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount.amount").value("20.00"));
    }

    @Test
    void aDeterministicFailureIsReplayedEvenIfTheStateChangedMeanwhile() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = openAccount(token);
        String key = "idem-" + UUID.randomUUID();

        withdraw(token, account, "100.00", key).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        deposit(token, account, "1000.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());

        // mesma chave = mesmo resultado, ainda que agora houvesse saldo: o cliente precisa de uma chave nova
        withdraw(token, account, "100.00", key).andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Idempotency-Replayed", "true"));
        withdraw(token, account, "100.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());
        assertThat(balance(token, account)).isEqualTo("900.00");
    }

    @Test
    void simultaneousRequestsWithTheSameKeyExecuteExactlyOnce() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = openAccount(token);
        String key = "idem-" + UUID.randomUUID();

        List<Integer> codes = runConcurrently(6, i -> deposit(token, account, "100.00", key).andReturn().getResponse().getStatus());

        assertThat(codes).allMatch(c -> c == 201 || c == 409); // 409 = a 1ª ainda estava em andamento
        assertThat(codes).contains(201);
        assertThat(balance(token, account)).isEqualTo("100.00"); // creditou UMA vez
        deposit(token, account, "100.00", key).andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }

    // ---------- concorrência ----------

    @Test
    void concurrentDepositsAllSucceedBecauseConflictsAreRetried() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = openAccount(token);

        List<Integer> codes = runConcurrently(5, i -> deposit(token, account, "10.00", "idem-" + UUID.randomUUID())
                .andReturn().getResponse().getStatus());

        assertThat(codes).containsOnly(201);
        assertThat(balance(token, account)).isEqualTo("50.00"); // nenhum crédito se perdeu
        assertThat(jdbc.queryForObject("select count(*) from transactions where account_id = ?::uuid", Integer.class, account))
                .isEqualTo(5);
    }

    @Test
    void parallelTransfersCannotBlowThroughTheDailyLimit() throws Exception {
        TestUsers.Login sender = TestUsers.registerAndLogin(mvc);
        String from = openAccount(sender.accessToken());
        MvcResult to = openAccountResult(TestUsers.registerAndLogin(mvc).accessToken());
        deposit(sender.accessToken(), from, "50000.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());

        // limite padrão: 5.000 por operação e 10.000 por dia → cabem só duas de 4.000
        List<Integer> codes = runConcurrently(6, i -> transfer(sender.accessToken(), from, to, "4000.00",
                "idem-" + UUID.randomUUID()).andReturn().getResponse().getStatus());

        assertThat(codes.stream().filter(c -> c == 201).count()).isEqualTo(2);
        assertThat(codes.stream().filter(c -> c == 422).count()).isEqualTo(4); // LIMIT_EXCEEDED ao reler o consumo
        assertThat(balance(sender.accessToken(), from)).isEqualTo("42000.00");
    }

    @Test
    void transfersInOppositeDirectionsConserveMoneyAndNeverDeadlock() throws Exception {
        TestUsers.Login alice = TestUsers.registerAndLogin(mvc);
        TestUsers.Login bob = TestUsers.registerAndLogin(mvc);
        MvcResult aliceAccount = openAccountResult(alice.accessToken());
        MvcResult bobAccount = openAccountResult(bob.accessToken());
        String aliceId = read(aliceAccount, "$.id");
        String bobId = read(bobAccount, "$.id");
        deposit(alice.accessToken(), aliceId, "1000.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());
        deposit(bob.accessToken(), bobId, "1000.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());

        List<Integer> codes = runConcurrently(6, i -> i % 2 == 0
                ? transfer(alice.accessToken(), aliceId, bobAccount, "10.00", "idem-" + UUID.randomUUID()).andReturn().getResponse().getStatus()
                : transfer(bob.accessToken(), bobId, aliceAccount, "10.00", "idem-" + UUID.randomUUID()).andReturn().getResponse().getStatus());

        assertThat(codes).allMatch(c -> c == 201 || c == 409); // nunca 5xx (sem deadlock: não há lock de linha)
        BigDecimal total = new BigDecimal(balance(alice.accessToken(), aliceId)).add(new BigDecimal(balance(bob.accessToken(), bobId)));
        assertThat(total).isEqualByComparingTo("2000.00"); // dinheiro conservado
        assertThat(jdbc.queryForObject("select count(*) from transfers where status = 'COMPLETED' and source_account_id in (?::uuid, ?::uuid)",
                Integer.class, aliceId, bobId)).isEqualTo((int) codes.stream().filter(c -> c == 201).count());
    }

    // ---------- Transactional Outbox ----------

    @Test
    void theEventIsWrittenWithTheOperationAndNotWhenItFails() throws Exception {
        TestUsers.Login sender = TestUsers.registerAndLogin(mvc);
        String from = openAccount(sender.accessToken());
        MvcResult to = openAccountResult(TestUsers.registerAndLogin(mvc).accessToken());
        deposit(sender.accessToken(), from, "100.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());

        MvcResult ok = transfer(sender.accessToken(), from, to, "30.00", "idem-" + UUID.randomUUID())
                .andExpect(status().isCreated()).andReturn();
        transfer(sender.accessToken(), from, to, "500.00", "idem-" + UUID.randomUUID()).andExpect(status().isUnprocessableEntity());

        String transferId = read(ok, "$.id");
        String payload = jdbc.queryForObject("select payload from outbox_events where event_type = 'TransferCompleted'"
                + " and aggregate_id = ?", String.class, transferId);
        assertThat(payload).contains("\"amount\":\"30.00\"", "\"currency\":\"BRL\"", from).doesNotContain("@", "document");
        // a recusada não gerou TransferCompleted (o evento desfez junto), mas gerou TransferFailed
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'TransferCompleted'"
                + " and payload like ?", Integer.class, "%" + from + "%")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'TransferFailed'"
                + " and aggregate_id = ?", Integer.class, from)).isEqualTo(1);
    }

    @Test
    void theRelayPublishesInOrderOnceAndMarksThemPublished() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc); // gera UserLoggedIn
        String admin = TestUsers.adminToken(mvc);
        String account = openAccount(login.accessToken());
        mvc.perform(bearer(admin, post("/api/v1/admin/accounts/" + account + "/block"))).andExpect(status().isOk());

        int pending = jdbc.queryForObject("select count(*) from outbox_events where published_at is null", Integer.class);
        assertThat(pending).isGreaterThanOrEqualTo(3);

        int published = 0;
        for (int n; (n = relay.publishPending()) > 0; ) {
            published += n;
        }

        assertThat(published).isEqualTo(pending);
        List<Long> ids = publisher.published.stream().map(OutboxEvent::id).toList();
        assertThat(ids).isSorted().doesNotHaveDuplicates();
        assertThat(publisher.published.stream().map(OutboxEvent::eventType)).contains("UserLoggedIn", "AccountBlocked");
        assertThat(relay.publishPending()).isZero(); // nada é publicado duas vezes
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where published_at is null", Integer.class)).isZero();
    }

    @Test
    void aFailingBrokerKeepsEventsPendingAndTheyGoOutLater() throws Exception {
        TestUsers.registerAndLogin(mvc);
        int pending = jdbc.queryForObject("select count(*) from outbox_events where published_at is null", Integer.class);
        assertThat(pending).isPositive();

        publisher.failing = true;
        assertThat(relay.publishPending()).isZero(); // o lote para no primeiro erro (preserva a ordem)
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where published_at is null", Integer.class))
                .isEqualTo(pending);
        assertThat(jdbc.queryForObject("select max(attempts) from outbox_events where published_at is null", Integer.class))
                .isGreaterThanOrEqualTo(1);

        publisher.failing = false;
        int sent = 0;
        for (int n; (n = relay.publishPending()) > 0; ) {
            sent += n;
        }
        assertThat(sent).isEqualTo(pending);
    }

    // ---------- helpers ----------

    private <T> List<T> runConcurrently(int threads, ThrowingFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            Callable<T> call = () -> {
                gate.await();
                return task.apply(index);
            };
            futures.add(pool.submit(call));
        }
        gate.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    @FunctionalInterface
    private interface ThrowingFunction<T> {
        T apply(int index) throws Exception;
    }

    private MvcResult openAccountResult(String token) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn();
    }

    private String openAccount(String token) throws Exception {
        return read(openAccountResult(token), "$.id");
    }

    private ResultActions deposit(String token, String account, String amount, String key) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
    }

    private ResultActions withdraw(String token, String account, String amount, String key) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/accounts/" + account + "/withdrawals")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
    }

    private ResultActions transfer(String token, String from, MvcResult destination, String amount, String key) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/transfers")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\":\"%s\",\"destinationBranch\":\"%s\","
                                + "\"destinationAccountNumber\":\"%s\",\"amount\":\"%s\"}", from,
                        read(destination, "$.branch"), read(destination, "$.accountNumber"), amount)));
    }

    private String balance(String token, String account) throws Exception {
        return read(mvc.perform(bearer(token, get("/api/v1/accounts/" + account + "/balance"))).andReturn(),
                "$.balance.amount");
    }
}
