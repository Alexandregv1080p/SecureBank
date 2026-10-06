package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
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

/** Cobranças Pix (QR dinâmico) pela API, com Postgres real: valor fixo, validade, uso único e dados mascarados. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PixChargeApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private record Person(String token, String account) {}

    // ---------- criar, ver, pagar ----------

    @Test
    void aChargeCanBeCreatedViewedByThePayerAndPaidExactlyOnce() throws Exception {
        Person receiver = person();
        Person payer = person();
        deposit(payer, "500.00");

        String body = create(receiver, "75.00", "Pedido 42", 60).andExpect(status().isCreated())
                .andExpect(jsonPath("$.txid").value(matchesPattern("[A-Za-z0-9]{32}")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.amount.amount").value("75.00"))
                .andReturn().getResponse().getContentAsString();
        String txid = JsonPath.read(body, "$.txid");
        assertThat((String) JsonPath.read(body, "$.location")).endsWith("/charges/" + txid);

        // o pagador vê o que precisa para decidir, com nome e CPF mascarados
        mvc.perform(as(payer, get("/api/v1/pix/charges/" + txid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount.amount").value("75.00"))
                .andExpect(jsonPath("$.description").value("Pedido 42"))
                .andExpect(jsonPath("$.receiverName").value("Cliente T***"))
                .andExpect(jsonPath("$.receiverDocument").value(matchesPattern("\\*\\*\\*\\.\\d{3}\\.\\*\\*\\*-\\*\\*")))
                .andExpect(jsonPath("$.own").value(false))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(as(receiver, get("/api/v1/pix/charges/" + txid))).andExpect(jsonPath("$.own").value(true));

        pay(payer, txid, payer.account(), key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount.amount").value("75.00"))
                .andExpect(jsonPath("$.message").value("Pedido 42"))
                .andExpect(jsonPath("$.direction").value("SENT"));

        assertThat(balance(payer)).isEqualTo("425.00");
        assertThat(balance(receiver)).isEqualTo("75.00");
        mvc.perform(as(receiver, get("/api/v1/pix/charges")))
                .andExpect(jsonPath("$.items[0].status").value("PAID")).andExpect(jsonPath("$.items[0].paidAt").exists());
        mvc.perform(as(receiver, get("/api/v1/accounts/" + receiver.account() + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("PIX_IN"));
        mvc.perform(as(payer, get("/api/v1/pix/transfers"))).andExpect(jsonPath("$.items[0].message").value("Pedido 42"));

        // uso único: pagar de novo (com outra chave de idempotência) é recusado e nada se move
        pay(payer, txid, payer.account(), key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIX_CHARGE_NOT_PAYABLE"));
        assertThat(balance(payer)).isEqualTo("425.00");
        assertThat(jdbc.queryForObject("select count(*) from pix_transfers where charge_txid = ?", Integer.class, txid)).isEqualTo(1);
    }

    @Test
    void payingWithTheSameIdempotencyKeyReplaysInsteadOfFailing() throws Exception {
        Person receiver = person();
        Person payer = person();
        deposit(payer, "100.00");
        String txid = createTxid(receiver, "40.00");
        String key = key();

        pay(payer, txid, payer.account(), key).andExpect(status().isCreated());
        pay(payer, txid, payer.account(), key).andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(balance(payer)).isEqualTo("60.00");
    }

    @Test
    void aChargeThatIsNotPaidKeepsWaitingEvenAfterAFailedAttempt() throws Exception {
        Person receiver = person();
        Person poor = person();
        deposit(poor, "10.00");
        String txid = createTxid(receiver, "50.00");

        pay(poor, txid, poor.account(), key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mvc.perform(as(receiver, get("/api/v1/pix/charges"))).andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
        assertThat(balance(poor)).isEqualTo("10.00");
    }

    @Test
    void chargePaymentsRespectThePixLimit() throws Exception {
        Person receiver = person();
        Person payer = person();
        deposit(payer, "20000.00");
        String txid = createTxid(receiver, "5000.01");

        pay(payer, txid, payer.account(), key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
    }

    // ---------- validade ----------

    @Test
    void anExpiredChargeShowsAsExpiredAndCannotBePaid() throws Exception {
        Person receiver = person();
        Person payer = person();
        deposit(payer, "100.00");
        String txid = createTxid(receiver, "30.00");
        // um dia atrás (e não 1 minuto): o relógio da VM do Docker pode estar alguns minutos adiantado em relação ao da aplicação
        jdbc.update("update pix_charges set expires_at = now() - interval '1 day' where txid = ?", txid);

        mvc.perform(as(payer, get("/api/v1/pix/charges/" + txid))).andExpect(jsonPath("$.status").value("EXPIRED"));
        pay(payer, txid, payer.account(), key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIX_CHARGE_EXPIRED"));
        mvc.perform(as(receiver, get("/api/v1/pix/charges"))).andExpect(jsonPath("$.items[0].status").value("EXPIRED"));
        assertThat(balance(payer)).isEqualTo("100.00");
    }

    // ---------- cancelar ----------

    @Test
    void theOwnerCanCancelAnActiveChargeAndNoOneElseCan() throws Exception {
        Person receiver = person();
        Person payer = person();
        deposit(payer, "100.00");
        String txid = createTxid(receiver, "30.00");

        mvc.perform(as(payer, delete("/api/v1/pix/charges/" + txid))).andExpect(status().isNotFound()); // não é dono
        mvc.perform(as(receiver, delete("/api/v1/pix/charges/" + txid))).andExpect(status().isNoContent());

        mvc.perform(as(payer, get("/api/v1/pix/charges/" + txid))).andExpect(jsonPath("$.status").value("CANCELED"));
        pay(payer, txid, payer.account(), key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIX_CHARGE_NOT_PAYABLE"));
        mvc.perform(as(receiver, delete("/api/v1/pix/charges/" + txid))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIX_CHARGE_NOT_CANCELABLE"));
    }

    @Test
    void aPaidChargeCannotBeCanceled() throws Exception {
        Person receiver = person();
        Person payer = person();
        deposit(payer, "100.00");
        String txid = createTxid(receiver, "30.00");
        pay(payer, txid, payer.account(), key()).andExpect(status().isCreated());

        mvc.perform(as(receiver, delete("/api/v1/pix/charges/" + txid))).andExpect(status().isUnprocessableEntity());
    }

    // ---------- concorrência ----------

    @Test
    void whenSeveralPayersRaceForTheSameChargeExactlyOneWins() throws Exception {
        Person receiver = person();
        String txid = createTxid(receiver, "100.00");
        List<Person> payers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Person p = person();
            deposit(p, "300.00");
            payers.add(p);
        }

        ExecutorService pool = Executors.newFixedThreadPool(payers.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Person p : payers) {
            results.add(pool.submit(() -> {
                go.await();
                return pay(p, txid, p.account(), key()).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            int code = f.get();
            if (code == 201) {
                created++;
            } else {
                assertThat(code).isIn(409, 422); // perdeu a corrida: conflito ou "não pagável"
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(balance(receiver)).isEqualTo("100.00"); // recebeu UMA vez
        assertThat(jdbc.queryForObject("select count(*) from pix_transfers where charge_txid = ?", Integer.class, txid)).isEqualTo(1);
        long debited = payers.stream().filter(p -> {
            try {
                return !balance(p).equals("300.00");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).count();
        assertThat(debited).isEqualTo(1); // só um pagador perdeu dinheiro
    }

    // ---------- validação e segurança ----------

    @Test
    void invalidChargesAreRejected() throws Exception {
        Person p = person();
        Person other = person();

        create(p, "0", null, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(p, "10.00", null, 0).andExpect(status().isBadRequest());
        create(p, "10.00", null, 10081).andExpect(status().isBadRequest());
        create(p, "10.00", "x".repeat(141), null).andExpect(status().isBadRequest());
        mvc.perform(as(other, post("/api/v1/pix/charges")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + p.account() + "\",\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound()); // conta de outra pessoa
    }

    @Test
    void unknownOrMalformedTxidsAreSimplyNotFound() throws Exception {
        Person p = person();

        for (String txid : new String[] {"a".repeat(32), "curto", "a".repeat(40), "x".repeat(26) + "!", "a".repeat(25) + "-"}) {
            mvc.perform(as(p, get("/api/v1/pix/charges/" + txid))).andExpect(status().isNotFound());
            pay(p, txid, p.account(), key()).andExpect(status().isNotFound());
        }
    }

    @Test
    void youCannotPayFromSomeoneElsesAccountNorFromTheReceivingAccount() throws Exception {
        Person receiver = person();
        Person victim = person();
        Person attacker = person();
        deposit(victim, "100.00");
        deposit(receiver, "100.00");
        String txid = createTxid(receiver, "10.00");

        pay(attacker, txid, victim.account(), key()).andExpect(status().isNotFound());
        pay(receiver, txid, receiver.account(), key()).andExpect(status().isBadRequest()); // paga a si mesmo na mesma conta

        assertThat(balance(victim)).isEqualTo("100.00");
        assertThat(balance(receiver)).isEqualTo("100.00");
    }

    @Test
    void payingRequiresAnIdempotencyKey() throws Exception {
        Person receiver = person();
        Person payer = person();
        String txid = createTxid(receiver, "10.00");

        mvc.perform(as(payer, post("/api/v1/pix/charges/" + txid + "/pay")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + payer.account() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void listingShowsOnlyYourOwnChargesNewestFirstAndPaged() throws Exception {
        Person me = person();
        Person other = person();
        createTxid(me, "1.00");
        createTxid(me, "2.00");
        createTxid(me, "3.00");
        createTxid(other, "9.00");

        mvc.perform(as(me, get("/api/v1/pix/charges?size=2")))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].amount.amount").value("3.00"));
        mvc.perform(as(me, get("/api/v1/pix/charges?size=2&page=1")))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].amount.amount").value("1.00"));
    }

    @Test
    void staffAndAnonymousUsersCannotUseCharges() throws Exception {
        mvc.perform(get("/api/v1/pix/charges")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pix/charges/" + "a".repeat(32))).andExpect(status().isUnauthorized());
        String admin = TestUsers.adminToken(mvc);
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/pix/charges"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/pix/charges/" + "a".repeat(32)))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(admin, post("/api/v1/pix/charges")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + UUID.randomUUID() + "\",\"amount\":\"1.00\"}")).andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private Person person() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = JsonPath.read(mvc.perform(TestUsers.bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return new Person(token, account);
    }

    private ResultActions create(Person p, String amount, String description, Integer minutes) throws Exception {
        StringBuilder json = new StringBuilder("{\"accountId\":\"" + p.account() + "\",\"amount\":\"" + amount + "\"");
        if (description != null) {
            json.append(",\"description\":\"").append(description).append('"');
        }
        if (minutes != null) {
            json.append(",\"expiresInMinutes\":").append(minutes);
        }
        return mvc.perform(as(p, post("/api/v1/pix/charges")).contentType(MediaType.APPLICATION_JSON).content(json.append('}').toString()));
    }

    private String createTxid(Person p, String amount) throws Exception {
        return JsonPath.read(create(p, amount, null, null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.txid");
    }

    private ResultActions pay(Person payer, String txid, String sourceAccount, String idempotencyKey) throws Exception {
        return mvc.perform(as(payer, post("/api/v1/pix/charges/" + txid + "/pay")).header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sourceAccountId\":\"" + sourceAccount + "\"}"));
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
