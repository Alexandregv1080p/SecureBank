package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.securebank.pix.application.PixScheduleApplicationService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Pix agendado pela API, com Postgres real: agenda hoje, move o dinheiro só na data, e falha com motivo se o dia não deixar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PixScheduleApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PixScheduleApplicationService scheduler;

    private record Person(String token, String email, String account) {}

    private static LocalDate tomorrow() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
    }

    // ---------- agendar ----------

    @Test
    void schedulingMovesNoMoneyYet() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "200.00");
        registerKey(receiver);

        schedule(sender, receiver.email(), "80.00", "aluguel", tomorrow()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.amount.amount").value("80.00"))
                .andExpect(jsonPath("$.scheduledFor").value(tomorrow().toString()))
                .andExpect(jsonPath("$.destinationName").value("Cliente T***"))
                .andExpect(jsonPath("$.key").value(receiver.email().toLowerCase()));

        assertThat(balance(sender)).isEqualTo("200.00");
        assertThat(balance(receiver)).isEqualTo("0.00");
        mvc.perform(as(sender, get("/api/v1/pix/schedules"))).andExpect(jsonPath("$.items", hasSize(1)));
        assertThat(scheduler.executeDue()).isZero(); // ainda não venceu: nada a fazer
    }

    @Test
    void theKeyMustExistAtSchedulingTime() throws Exception {
        Person sender = person();

        schedule(sender, "ninguem@example.com", "10.00", null, tomorrow()).andExpect(status().isNotFound());
        mvc.perform(as(sender, get("/api/v1/pix/schedules"))).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void theDateMustBeInTheFutureButWithinAYear() throws Exception {
        Person sender = person();
        Person receiver = person();
        registerKey(receiver);
        LocalDate today = tomorrow().minusDays(1);

        schedule(sender, receiver.email(), "10.00", null, today).andExpect(status().isBadRequest());
        schedule(sender, receiver.email(), "10.00", null, today.minusDays(3)).andExpect(status().isBadRequest());
        schedule(sender, receiver.email(), "10.00", null, today.plusDays(366)).andExpect(status().isBadRequest());
        schedule(sender, receiver.email(), "10.00", null, today.plusDays(365)).andExpect(status().isCreated());
    }

    @Test
    void invalidRequestsAndMissingIdempotencyKeyAreRejected() throws Exception {
        Person sender = person();
        Person receiver = person();
        registerKey(receiver);

        mvc.perform(as(sender, post("/api/v1/pix/schedules")).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + sender.account() + "\",\"key\":\"" + receiver.email() + "\",\"amount\":\"0\",\"scheduledFor\":\"" + tomorrow() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(sender, post("/api/v1/pix/schedules")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + sender.account() + "\",\"key\":\"" + receiver.email() + "\",\"amount\":\"5.00\",\"scheduledFor\":\"" + tomorrow() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(as(sender, post("/api/v1/pix/schedules")).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + sender.account() + "\",\"key\":\"" + receiver.email() + "\",\"amount\":\"5.00\",\"scheduledFor\":\"amanhã\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCustomerHasAtMost20PendingSchedules() throws Exception {
        Person sender = person();
        Person receiver = person();
        registerKey(receiver);
        for (int i = 0; i < 20; i++) {
            schedule(sender, receiver.email(), "1.00", null, tomorrow()).andExpect(status().isCreated());
        }

        schedule(sender, receiver.email(), "1.00", null, tomorrow()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIX_SCHEDULE_LIMIT_REACHED"));
    }

    // ---------- executar na data ----------

    @Test
    void onTheDateTheMoneyMovesAndTheScheduleIsExecuted() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "200.00");
        registerKey(receiver);
        String id = scheduleId(sender, receiver.email(), "80.00", "aluguel");
        makeDue(id);

        assertThat(scheduler.executeDue()).isEqualTo(1);

        assertThat(balance(sender)).isEqualTo("120.00");
        assertThat(balance(receiver)).isEqualTo("80.00");
        mvc.perform(as(sender, get("/api/v1/pix/schedules")))
                .andExpect(jsonPath("$.items[0].status").value("EXECUTED")).andExpect(jsonPath("$.items[0].executedPixId").exists());
        mvc.perform(as(sender, get("/api/v1/pix/transfers")))
                .andExpect(jsonPath("$.items[0].direction").value("SENT")).andExpect(jsonPath("$.items[0].message").value("aluguel"));
        mvc.perform(as(receiver, get("/api/v1/accounts/" + receiver.account() + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("PIX_IN"));
        assertThat(scheduler.executeDue()).isZero(); // não repete
        assertThat(balance(sender)).isEqualTo("120.00");
    }

    @Test
    void anOverdueScheduleStillRunsWhenTheSchedulerWasDown() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "50.00");
        registerKey(receiver);
        String id = scheduleId(sender, receiver.email(), "10.00", null);
        jdbc.update("update pix_schedules set scheduled_for = current_date - 5 where id = ?::uuid", id);

        assertThat(scheduler.executeDue()).isEqualTo(1);

        assertThat(balance(receiver)).isEqualTo("10.00");
    }

    @Test
    void withoutEnoughMoneyOnTheDateItFailsWithTheReasonAndTheCustomerIsTold() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "10.00");
        registerKey(receiver);
        String id = scheduleId(sender, receiver.email(), "80.00", null);
        makeDue(id);

        assertThat(scheduler.executeDue()).isEqualTo(1);

        assertThat(balance(sender)).isEqualTo("10.00");
        mvc.perform(as(sender, get("/api/v1/pix/schedules")))
                .andExpect(jsonPath("$.items[0].status").value("FAILED")).andExpect(jsonPath("$.items[0].failureReason").value("INSUFFICIENT_FUNDS"));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'PixScheduleFailed' and aggregate_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where event = 'PIX_SCHEDULE_FAILED' and account_id = ?::uuid",
                Integer.class, sender.account())).isEqualTo(1);
        // nunca há segunda tentativa automática: mesmo com dinheiro depois, continua FAILED
        deposit(sender, "500.00");
        assertThat(scheduler.executeDue()).isZero();
        assertThat(balance(receiver)).isEqualTo("0.00");
    }

    @Test
    void ifTheKeyDisappearsBeforeTheDateItFails() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "50.00");
        String keyId = registerKey(receiver);
        String id = scheduleId(sender, receiver.email(), "10.00", null);
        mvc.perform(as(receiver, delete("/api/v1/pix/keys/" + keyId))).andExpect(status().isNoContent());
        makeDue(id);

        assertThat(scheduler.executeDue()).isEqualTo(1);

        mvc.perform(as(sender, get("/api/v1/pix/schedules")))
                .andExpect(jsonPath("$.items[0].status").value("FAILED")).andExpect(jsonPath("$.items[0].failureReason").value("NOT_FOUND"));
        assertThat(balance(sender)).isEqualTo("50.00");
    }

    @Test
    void thePixLimitOfTheDayAppliesOnTheDate() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "20000.00");
        registerKey(receiver);
        String id = scheduleId(sender, receiver.email(), "5000.00", null);
        makeDue(id);
        // hoje o cliente já gastou o limite diário do Pix (10.000)
        for (int i = 0; i < 2; i++) {
            mvc.perform(as(sender, post("/api/v1/pix/transfers")).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sourceAccountId\":\"" + sender.account() + "\",\"key\":\"" + receiver.email() + "\",\"amount\":\"5000.00\"}"))
                    .andExpect(status().isCreated());
        }

        assertThat(scheduler.executeDue()).isEqualTo(1);

        mvc.perform(as(sender, get("/api/v1/pix/schedules"))).andExpect(jsonPath("$.items[0].failureReason").value("LIMIT_EXCEEDED"));
    }

    @Test
    void twoSchedulerRunsAtTheSameTimeNeverSendTwice() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "100.00");
        registerKey(receiver);
        String id = scheduleId(sender, receiver.email(), "30.00", null);
        makeDue(id);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> a = pool.submit(() -> { go.await(); return scheduler.executeDue(); });
        Future<Integer> b = pool.submit(() -> { go.await(); return scheduler.executeDue(); });
        go.countDown();
        int total = a.get() + b.get();
        pool.shutdown();

        assertThat(total).isEqualTo(1);
        assertThat(balance(receiver)).isEqualTo("30.00"); // recebeu UMA vez
        assertThat(balance(sender)).isEqualTo("70.00");
    }

    // ---------- cancelar ----------

    @Test
    void aCanceledScheduleNeverRunsAndOnlyTheOwnerCanCancel() throws Exception {
        Person sender = person();
        Person receiver = person();
        Person stranger = person();
        deposit(sender, "100.00");
        registerKey(receiver);
        String id = scheduleId(sender, receiver.email(), "30.00", null);

        mvc.perform(as(stranger, delete("/api/v1/pix/schedules/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(sender, delete("/api/v1/pix/schedules/" + id))).andExpect(status().isNoContent());
        makeDue(id); // mesmo "vencido", cancelado não executa

        assertThat(scheduler.executeDue()).isZero();

        mvc.perform(as(sender, get("/api/v1/pix/schedules"))).andExpect(jsonPath("$.items[0].status").value("CANCELED"));
        assertThat(balance(sender)).isEqualTo("100.00");
        mvc.perform(as(sender, delete("/api/v1/pix/schedules/" + id))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIX_SCHEDULE_NOT_CANCELABLE"));
    }

    @Test
    void youCannotScheduleFromSomeoneElsesAccountAndListsAreIsolated() throws Exception {
        Person victim = person();
        Person attacker = person();
        Person receiver = person();
        registerKey(receiver);
        deposit(victim, "100.00");

        mvc.perform(as(attacker, post("/api/v1/pix/schedules")).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + victim.account() + "\",\"key\":\"" + receiver.email() + "\",\"amount\":\"50.00\",\"scheduledFor\":\"" + tomorrow() + "\"}"))
                .andExpect(status().isNotFound());
        scheduleId(victim, receiver.email(), "5.00", null);

        mvc.perform(as(attacker, get("/api/v1/pix/schedules"))).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void staffAndAnonymousUsersCannotUseSchedules() throws Exception {
        mvc.perform(get("/api/v1/pix/schedules")).andExpect(status().isUnauthorized());
        String admin = TestUsers.adminToken(mvc);
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/pix/schedules"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(admin, delete("/api/v1/pix/schedules/" + UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    /** Faz o agendamento "vencer" (a data de verdade só chega amanhã). */
    private void makeDue(String id) {
        jdbc.update("update pix_schedules set scheduled_for = current_date - 1 where id = ?::uuid", id);
    }

    private Person person() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        String account = JsonPath.read(mvc.perform(TestUsers.bearer(login.accessToken(), post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return new Person(login.accessToken(), login.email(), account);
    }

    private String registerKey(Person p) throws Exception {
        return JsonPath.read(mvc.perform(as(p, post("/api/v1/pix/keys")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + p.account() + "\",\"type\":\"EMAIL\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions schedule(Person from, String key, String amount, String message, LocalDate date) throws Exception {
        String msg = message == null ? "" : ",\"message\":\"" + message + "\"";
        return mvc.perform(as(from, post("/api/v1/pix/schedules")).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + from.account() + "\",\"key\":\"" + key + "\",\"amount\":\"" + amount + "\",\"scheduledFor\":\"" + date + "\"" + msg + "}"));
    }

    private String scheduleId(Person from, String key, String amount, String message) throws Exception {
        return JsonPath.read(schedule(from, key, amount, message, tomorrow()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void deposit(Person p, String amount) throws Exception {
        mvc.perform(as(p, post("/api/v1/accounts/" + p.account() + "/deposits")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}")).andExpect(status().isCreated());
    }

    private String balance(Person p) throws Exception {
        return JsonPath.read(mvc.perform(as(p, get("/api/v1/accounts/" + p.account() + "/balance"))).andReturn()
                .getResponse().getContentAsString(), "$.balance.amount");
    }

    private static String key() {
        return "idem-" + UUID.randomUUID();
    }

    private static MockHttpServletRequestBuilder as(Person p, MockHttpServletRequestBuilder request) {
        return TestUsers.bearer(p.token(), request);
    }
}
