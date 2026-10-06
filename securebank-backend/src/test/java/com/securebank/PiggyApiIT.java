package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Porquinhos pela API, com Postgres real: o dinheiro muda de lugar (conta ↔ porquinho) sem nunca sumir nem duplicar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PiggyApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    // ---------- criar, listar, ler ----------

    @Test
    void createsAndListsAPiggyWithItsGoalAndProgress() throws Exception {
        String token = customer();
        String account = openAccount(token);

        MvcResult created = mvc.perform(as(token, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"accountId":"%s","name":"  Viagem  ","goal":"1000.00"}""", account)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Viagem")) // aparado
                .andExpect(jsonPath("$.balance.amount").value("0.00"))
                .andExpect(jsonPath("$.goal.amount").value("1000.00"))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.goalReached").value(false))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String piggy = read(created, "$.id");

        mvc.perform(as(token, get("/api/v1/piggies")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(piggy));
        mvc.perform(as(token, get("/api/v1/piggies/" + piggy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account));
    }

    @Test
    void aPiggyWithoutAGoalHasNoProgress() throws Exception {
        String token = customer();
        String piggy = createPiggy(token, openAccount(token), "Reserva", null);

        mvc.perform(as(token, get("/api/v1/piggies/" + piggy)))
                .andExpect(jsonPath("$.goal").doesNotExist())
                .andExpect(jsonPath("$.progressPercent").doesNotExist());
    }

    @Test
    void invalidInputIsRejectedWithTheStandardError() throws Exception {
        String token = customer();
        String account = openAccount(token);

        mvc.perform(as(token, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"name\":\"\"}", account)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(token, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"name\":\"A\",\"goal\":\"0\"}", account)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(token, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"name\":\"%s\"}", account, "x".repeat(41))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCustomerCanHaveAtMost20ActivePiggies() throws Exception {
        String token = customer();
        String account = openAccount(token);
        for (int i = 0; i < 20; i++) {
            createPiggy(token, account, "P" + i, null);
        }

        mvc.perform(as(token, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"name\":\"21\"}", account)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIGGY_LIMIT_REACHED"));
    }

    // ---------- guardar e resgatar ----------

    @Test
    void savingMovesMoneyFromTheAccountIntoThePiggyAndShowsOnTheStatement() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "500.00");
        String piggy = createPiggy(token, account, "Viagem", "1000.00");

        save(token, piggy, "200.00", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance.amount").value("200.00"))
                .andExpect(jsonPath("$.progressPercent").value(20));

        assertThat(balance(token, account)).isEqualTo("300.00"); // saiu da conta
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("PIGGY_IN"))
                .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.items[0].amount.amount").value("200.00"))
                .andExpect(jsonPath("$.items[0].balanceAfter.amount").value("300.00"));
    }

    @Test
    void redeemingMovesMoneyBackAndRefusesMoreThanThePiggyHolds() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String piggy = createPiggy(token, account, "Reserva", null);
        save(token, piggy, "80.00", key()).andExpect(status().isCreated());

        redeem(token, piggy, "30.00", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance.amount").value("50.00"));
        assertThat(balance(token, account)).isEqualTo("50.00"); // 100 - 80 + 30

        redeem(token, piggy, "50.01", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PIGGY_FUNDS"));
        assertThat(balance(token, account)).isEqualTo("50.00"); // nada mudou
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("PIGGY_OUT"))
                .andExpect(jsonPath("$.items[0].direction").value("CREDIT"));
    }

    @Test
    void savingMoreThanTheAccountHoldsIsRefusedAndNothingMoves() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "10.00");
        String piggy = createPiggy(token, account, "Reserva", null);

        save(token, piggy, "10.01", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balance(token, account)).isEqualTo("10.00");
        mvc.perform(as(token, get("/api/v1/piggies/" + piggy))).andExpect(jsonPath("$.balance.amount").value("0.00"));
    }

    @Test
    void theSameIdempotencyKeyNeverSavesTwice() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String piggy = createPiggy(token, account, "Reserva", null);
        String key = key();

        save(token, piggy, "40.00", key).andExpect(status().isCreated());
        save(token, piggy, "40.00", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(balance(token, account)).isEqualTo("60.00"); // debitou UMA vez
        mvc.perform(as(token, get("/api/v1/piggies/" + piggy))).andExpect(jsonPath("$.balance.amount").value("40.00"));
        // mesma chave com valor diferente é recusada
        save(token, piggy, "41.00", key).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void moneyOperationsRequireAnIdempotencyKey() throws Exception {
        String token = customer();
        String piggy = createPiggy(token, openAccount(token), "Reserva", null);

        mvc.perform(as(token, post("/api/v1/piggies/" + piggy + "/deposits")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    // ---------- meta ----------

    @Test
    void reachingTheGoalRecordsOneGoalEventAndOnlyOnce() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "300.00");
        String piggy = createPiggy(token, account, "Meta", "100.00");

        save(token, piggy, "60.00", key()).andExpect(jsonPath("$.goalReached").value(false));
        save(token, piggy, "40.00", key())
                .andExpect(jsonPath("$.goalReached").value(true))
                .andExpect(jsonPath("$.progressPercent").value(100));
        save(token, piggy, "10.00", key()).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'PiggyGoalReached'"
                + " and aggregate_id = ?", Integer.class, piggy)).isEqualTo(1);
        // o payload não carrega o nome do porquinho (texto livre do usuário)
        String payload = jdbc.queryForObject("select payload from outbox_events where event_type = 'PiggyGoalReached'"
                + " and aggregate_id = ?", String.class, piggy);
        assertThat(payload).doesNotContain("Meta");
    }

    @Test
    void renamesChangesAndClearsTheGoal() throws Exception {
        String token = customer();
        String piggy = createPiggy(token, openAccount(token), "Velho", "100.00");

        mvc.perform(as(token, patch("/api/v1/piggies/" + piggy)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo\",\"goal\":\"250.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo"))
                .andExpect(jsonPath("$.goal.amount").value("250.00"));
        mvc.perform(as(token, patch("/api/v1/piggies/" + piggy)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearGoal\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo")) // não enviado = não muda
                .andExpect(jsonPath("$.goal").doesNotExist());
    }

    // ---------- fechar ----------

    @Test
    void closingReturnsWhatIsInsideToTheAccountAndHidesThePiggy() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String piggy = createPiggy(token, account, "Reserva", null);
        save(token, piggy, "70.00", key()).andExpect(status().isCreated());
        assertThat(balance(token, account)).isEqualTo("30.00");

        mvc.perform(as(token, delete("/api/v1/piggies/" + piggy))).andExpect(status().isNoContent());

        assertThat(balance(token, account)).isEqualTo("100.00"); // tudo voltou
        mvc.perform(as(token, get("/api/v1/piggies"))).andExpect(jsonPath("$", hasSize(0)));
        save(token, piggy, "1.00", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIGGY_CLOSED"));
    }

    @Test
    void closingAnEmptyPiggyJustClosesIt() throws Exception {
        String token = customer();
        String piggy = createPiggy(token, openAccount(token), "Vazio", null);

        mvc.perform(as(token, delete("/api/v1/piggies/" + piggy))).andExpect(status().isNoContent());

        mvc.perform(as(token, get("/api/v1/piggies"))).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- segurança ----------

    @Test
    void anotherCustomersPiggyLooksLikeItDoesNotExist() throws Exception {
        String owner = customer();
        String account = openAccount(owner);
        deposit(owner, account, "50.00");
        String piggy = createPiggy(owner, account, "Meu", null);
        String intruder = customer();

        mvc.perform(as(intruder, get("/api/v1/piggies/" + piggy))).andExpect(status().isNotFound());
        save(intruder, piggy, "1.00", key()).andExpect(status().isNotFound());
        redeem(intruder, piggy, "1.00", key()).andExpect(status().isNotFound());
        mvc.perform(as(intruder, patch("/api/v1/piggies/" + piggy)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Roubado\"}")).andExpect(status().isNotFound());
        mvc.perform(as(intruder, delete("/api/v1/piggies/" + piggy))).andExpect(status().isNotFound());
        mvc.perform(as(intruder, get("/api/v1/piggies"))).andExpect(jsonPath("$", hasSize(0)));
        // e ninguém cria porquinho na conta alheia
        mvc.perform(as(intruder, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"name\":\"X\"}", account)))
                .andExpect(status().isNotFound());

        mvc.perform(as(owner, get("/api/v1/piggies/" + piggy))).andExpect(jsonPath("$.name").value("Meu"));
        assertThat(balance(owner, account)).isEqualTo("50.00");
    }

    @Test
    void staffAndAnonymousUsersCannotUsePiggies() throws Exception {
        mvc.perform(get("/api/v1/piggies")).andExpect(status().isUnauthorized());
        mvc.perform(as(TestUsers.adminToken(mvc), get("/api/v1/piggies"))).andExpect(status().isForbidden());
        mvc.perform(as(TestUsers.adminToken(mvc), post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + UUID.randomUUID() + "\",\"name\":\"X\"}")).andExpect(status().isForbidden());
    }

    @Test
    void theAccountBalanceAndPiggyBalanceNeverDriftApart() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String piggy = createPiggy(token, account, "Reserva", null);

        save(token, piggy, "33.33", key()).andExpect(status().isCreated());
        redeem(token, piggy, "10.10", key()).andExpect(status().isCreated());
        save(token, piggy, "0.01", key()).andExpect(status().isCreated());

        // o total (conta + porquinho) é sempre o que o cliente depositou
        String inAccount = balance(token, account);
        String inPiggy = JsonPath.read(mvc.perform(as(token, get("/api/v1/piggies/" + piggy))).andReturn()
                .getResponse().getContentAsString(), "$.balance.amount");
        assertThat(new java.math.BigDecimal(inAccount).add(new java.math.BigDecimal(inPiggy)))
                .isEqualByComparingTo("100.00");
    }

    // ---------- helpers ----------

    private ResultActions save(String token, String piggy, String amount, String key) throws Exception {
        return mvc.perform(as(token, post("/api/v1/piggies/" + piggy + "/deposits")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
    }

    private ResultActions redeem(String token, String piggy, String amount, String key) throws Exception {
        return mvc.perform(as(token, post("/api/v1/piggies/" + piggy + "/withdrawals")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
    }

    private String createPiggy(String token, String account, String name, String goal) throws Exception {
        String body = goal == null ? json("{\"accountId\":\"%s\",\"name\":\"%s\"}", account, name)
                : json("{\"accountId\":\"%s\",\"name\":\"%s\",\"goal\":\"%s\"}", account, name, goal);
        return read(mvc.perform(as(token, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private String customer() throws Exception {
        return TestUsers.registerAndLogin(mvc).accessToken();
    }

    private String openAccount(String token) throws Exception {
        return read(mvc.perform(as(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private void deposit(String token, String account, String amount) throws Exception {
        mvc.perform(as(token, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isCreated());
    }

    private String balance(String token, String account) throws Exception {
        return read(mvc.perform(as(token, get("/api/v1/accounts/" + account + "/balance"))).andReturn(), "$.balance.amount");
    }

    private static String key() {
        return "idem-" + UUID.randomUUID();
    }

    private static MockHttpServletRequestBuilder as(String accessToken, MockHttpServletRequestBuilder request) {
        return TestUsers.bearer(accessToken, request);
    }

    private static String json(String template, Object... args) {
        return String.format(template, args);
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
