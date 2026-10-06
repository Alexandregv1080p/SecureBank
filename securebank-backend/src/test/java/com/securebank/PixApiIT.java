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

/** Pix pela API, com Postgres real: chaves, consulta mascarada, envio, limite, histórico dos dois lados e segurança. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PixApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** Cliente com conta (e e-mail) prontos. */
    private record Person(String token, String email, String account) {}

    // ---------- chaves ----------

    @Test
    void registersKeysWhoseValuesComeFromTheCustomersOwnData() throws Exception {
        Person p = person();

        String email = registerKey(p, "EMAIL").andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(p.email().toLowerCase())).andReturn().getResponse().getContentAsString();
        registerKey(p, "CPF").andExpect(status().isCreated()).andExpect(jsonPath("$.key").value(matchesPattern("\\d{11}")));
        registerKey(p, "RANDOM").andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));

        assertThat(JsonPath.<String>read(email, "$.type")).isEqualTo("EMAIL");
        mvc.perform(as(p, get("/api/v1/pix/keys"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void aClientCannotChooseTheKeyValue() throws Exception {
        Person p = person();

        // o corpo não tem campo "value": se vier, é ignorado e a chave sai do cadastro (ninguém registra a chave de outro)
        mvc.perform(as(p, post("/api/v1/pix/keys")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + p.account() + "\",\"type\":\"EMAIL\",\"value\":\"outra@pessoa.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(p.email().toLowerCase()));
    }

    @Test
    void aKeyCanBelongToOnlyOneAccountInTheWholeBank() throws Exception {
        Person p = person();
        registerKey(p, "EMAIL").andExpect(status().isCreated());

        registerKey(p, "EMAIL").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PIX_KEY_IN_USE"));
    }

    @Test
    void aCustomerHasAtMostFiveKeys() throws Exception {
        Person p = person();
        for (int i = 0; i < 5; i++) {
            registerKey(p, "RANDOM").andExpect(status().isCreated());
        }

        registerKey(p, "EMAIL").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PIX_KEY_LIMIT_REACHED"));
    }

    @Test
    void keysCanOnlyBePointedAtYourOwnAccounts() throws Exception {
        Person owner = person();
        Person other = person();

        mvc.perform(as(other, post("/api/v1/pix/keys")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + owner.account() + "\",\"type\":\"RANDOM\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAKeyRemovesItAndOthersCannotDeleteYours() throws Exception {
        Person p = person();
        Person intruder = person();
        String keyId = JsonPath.read(registerKey(p, "EMAIL").andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(as(intruder, delete("/api/v1/pix/keys/" + keyId))).andExpect(status().isNotFound());
        mvc.perform(as(p, get("/api/v1/pix/keys"))).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(as(p, delete("/api/v1/pix/keys/" + keyId))).andExpect(status().isNoContent());

        mvc.perform(as(p, get("/api/v1/pix/keys"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(intruder, get("/api/v1/pix/keys/lookup").param("key", p.email()))).andExpect(status().isNotFound());
    }

    // ---------- consulta ----------

    @Test
    void lookupShowsOnlyTheMaskedNameAndDocument() throws Exception {
        Person receiver = person();
        Person sender = person();
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());

        mvc.perform(as(sender, get("/api/v1/pix/keys/lookup").param("key", receiver.email().toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.name").value("Cliente T***")) // "Cliente Teste": só a inicial do sobrenome
                .andExpect(jsonPath("$.document").value(matchesPattern("\\*\\*\\*\\.\\d{3}\\.\\*\\*\\*-\\*\\*")))
                .andExpect(jsonPath("$.bank").value("SecureBank"))
                .andExpect(jsonPath("$.ownAccount").value(false));
        mvc.perform(as(receiver, get("/api/v1/pix/keys/lookup").param("key", receiver.email())))
                .andExpect(jsonPath("$.ownAccount").value(true));
    }

    @Test
    void unknownOrMalformedKeysAreSimplyNotFound() throws Exception {
        Person p = person();

        for (String key : new String[] {"ninguem@example.com", UUID.randomUUID().toString(), "11144477735", "+5511000000000", "lixo", "123"}) {
            mvc.perform(as(p, get("/api/v1/pix/keys/lookup").param("key", key)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void aCpfCanBeLookedUpWithOrWithoutPunctuation() throws Exception {
        Person receiver = person();
        Person sender = person();
        String cpf = JsonPath.read(registerKey(receiver, "CPF").andReturn().getResponse().getContentAsString(), "$.key");
        String punctuated = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);

        mvc.perform(as(sender, get("/api/v1/pix/keys/lookup").param("key", cpf))).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CPF"));
        mvc.perform(as(sender, get("/api/v1/pix/keys/lookup").param("key", punctuated))).andExpect(status().isOk());
    }

    // ---------- envio ----------

    @Test
    void sendingMovesTheMoneyAndBothSidesSeeItInTheirStatementAndHistory() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "500.00");
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());

        MvcResult sent = send(sender, receiver.email(), "120.50", "almoço", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount.amount").value("120.50"))
                .andExpect(jsonPath("$.direction").value("SENT"))
                .andExpect(jsonPath("$.counterpartName").value("Cliente T***"))
                .andExpect(jsonPath("$.message").value("almoço"))
                .andExpect(jsonPath("$.endToEndId").value(matchesPattern("E\\d{20}[A-Za-z0-9]{11}")))
                .andReturn();
        String endToEnd = JsonPath.read(sent.getResponse().getContentAsString(), "$.endToEndId");

        assertThat(balance(sender)).isEqualTo("379.50");
        assertThat(balance(receiver)).isEqualTo("120.50");
        mvc.perform(as(sender, get("/api/v1/accounts/" + sender.account() + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("PIX_OUT")).andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.items[0].reference").value(endToEnd));
        mvc.perform(as(receiver, get("/api/v1/accounts/" + receiver.account() + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("PIX_IN")).andExpect(jsonPath("$.items[0].direction").value("CREDIT"));
        mvc.perform(as(sender, get("/api/v1/pix/transfers")))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].direction").value("SENT"))
                .andExpect(jsonPath("$.items[0].endToEndId").value(endToEnd));
        mvc.perform(as(receiver, get("/api/v1/pix/transfers")))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].direction").value("RECEIVED"))
                .andExpect(jsonPath("$.items[0].counterpartName").value("Cliente T***"));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'PixCompleted'"
                + " and payload like ?", Integer.class, "%" + sender.account() + "%")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aPixWithoutEnoughMoneyChangesNothingAndLeavesAnAuditTrail() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "10.00");
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());

        send(sender, receiver.email(), "10.01", null, key())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balance(sender)).isEqualTo("10.00");
        assertThat(balance(receiver)).isEqualTo("0.00");
        mvc.perform(as(sender, get("/api/v1/pix/transfers"))).andExpect(jsonPath("$.totalElements").value(0)); // só Pix concluído vira registro
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where event = 'PIX_FAILED' and account_id = ?::uuid",
                Integer.class, sender.account())).isEqualTo(1);
    }

    @Test
    void thePixLimitIsPerOperationAndPerDay() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "20000.00");
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());

        send(sender, receiver.email(), "5000.01", null, key())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
        send(sender, receiver.email(), "5000.00", null, key()).andExpect(status().isCreated());
        send(sender, receiver.email(), "5000.00", null, key()).andExpect(status().isCreated());
        send(sender, receiver.email(), "1.00", null, key())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED")); // 10.000 do dia

        assertThat(balance(sender)).isEqualTo("10000.00");
    }

    @Test
    void theSameIdempotencyKeyNeverSendsTwice() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "100.00");
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());
        String key = key();

        send(sender, receiver.email(), "40.00", null, key).andExpect(status().isCreated());
        send(sender, receiver.email(), "40.00", null, key)
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(balance(sender)).isEqualTo("60.00");
        assertThat(balance(receiver)).isEqualTo("40.00");
    }

    @Test
    void sendingRequiresAnIdempotencyKey() throws Exception {
        Person sender = person();
        Person receiver = person();
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());

        mvc.perform(as(sender, post("/api/v1/pix/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody(sender, receiver.email(), "1.00", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void sendingToYourOwnKeyOnTheSameAccountIsRefused() throws Exception {
        Person p = person();
        deposit(p, "50.00");
        registerKey(p, "EMAIL").andExpect(status().isCreated());

        send(p, p.email(), "10.00", null, key()).andExpect(status().isBadRequest());

        assertThat(balance(p)).isEqualTo("50.00");
    }

    @Test
    void sendingBetweenYourOwnTwoAccountsWorks() throws Exception {
        Person p = person();
        deposit(p, "50.00");
        String second = openAccount(p.token());
        mvc.perform(as(p, post("/api/v1/pix/keys")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + second + "\",\"type\":\"EMAIL\"}")).andExpect(status().isCreated());

        send(p, p.email(), "20.00", null, key()).andExpect(status().isCreated());

        assertThat(balance(p)).isEqualTo("30.00");
        assertThat(JsonPath.<String>read(mvc.perform(as(p, get("/api/v1/accounts/" + second + "/balance"))).andReturn()
                .getResponse().getContentAsString(), "$.balance.amount")).isEqualTo("20.00");
        mvc.perform(as(p, get("/api/v1/pix/transfers"))).andExpect(jsonPath("$.items[0].direction").value("SENT"));
    }

    @Test
    void sendingToAnUnknownKeyIsNotFoundAndNothingMoves() throws Exception {
        Person sender = person();
        deposit(sender, "50.00");

        send(sender, "ninguem@example.com", "10.00", null, key()).andExpect(status().isNotFound());

        assertThat(balance(sender)).isEqualTo("50.00");
    }

    @Test
    void youCannotSendFromSomeoneElsesAccount() throws Exception {
        Person victim = person();
        Person attacker = person();
        Person receiver = person();
        deposit(victim, "100.00");
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());

        mvc.perform(as(attacker, post("/api/v1/pix/transfers")).header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + victim.account() + "\",\"key\":\"" + receiver.email() + "\",\"amount\":\"50.00\"}"))
                .andExpect(status().isNotFound());

        assertThat(balance(victim)).isEqualTo("100.00");
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        Person p = person();
        mvc.perform(as(p, post("/api/v1/pix/transfers")).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + p.account() + "\",\"key\":\"\",\"amount\":\"0\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(p, post("/api/v1/pix/keys")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + p.account() + "\",\"type\":\"OUTRO\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void historyIsPagedAndNewestFirst() throws Exception {
        Person sender = person();
        Person receiver = person();
        deposit(sender, "100.00");
        registerKey(receiver, "EMAIL").andExpect(status().isCreated());
        for (String amount : new String[] {"1.00", "2.00", "3.00"}) {
            send(sender, receiver.email(), amount, null, key()).andExpect(status().isCreated());
        }

        mvc.perform(as(sender, get("/api/v1/pix/transfers?size=2&page=0")))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].amount.amount").value("3.00"));
        mvc.perform(as(sender, get("/api/v1/pix/transfers?size=2&page=1")))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].amount.amount").value("1.00"));
    }

    // ---------- segurança ----------

    @Test
    void staffAndAnonymousUsersCannotUsePix() throws Exception {
        mvc.perform(get("/api/v1/pix/keys")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pix/keys/lookup").param("key", "a@b.co")).andExpect(status().isUnauthorized());
        String admin = TestUsers.adminToken(mvc);
        mvc.perform(as(admin, get("/api/v1/pix/keys"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, get("/api/v1/pix/keys/lookup").param("key", "a@b.co"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, post("/api/v1/pix/transfers")).header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"" + UUID.randomUUID() + "\",\"key\":\"x\",\"amount\":\"1.00\"}")).andExpect(status().isForbidden());
    }

    @Test
    void everyAccountGetsAPixLimitIncludingExistingOnes() {
        // a migração V8 deu o limite Pix às contas que já existiam; contas novas ganham no momento da abertura
        Integer withoutPix = jdbc.queryForObject("select count(*) from accounts a where not exists"
                + " (select 1 from limits l where l.account_id = a.id and l.type = 'PIX')", Integer.class);
        assertThat(withoutPix).isZero();
    }

    // ---------- helpers ----------

    private Person person() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        return new Person(login.accessToken(), login.email(), openAccount(login.accessToken()));
    }

    private String openAccount(String token) throws Exception {
        return JsonPath.read(mvc.perform(as(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions registerKey(Person p, String type) throws Exception {
        return mvc.perform(as(p, post("/api/v1/pix/keys")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + p.account() + "\",\"type\":\"" + type + "\"}"));
    }

    private ResultActions send(Person from, String key, String amount, String message, String idempotencyKey) throws Exception {
        return mvc.perform(as(from, post("/api/v1/pix/transfers")).header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(sendBody(from, key, amount, message)));
    }

    private static String sendBody(Person from, String key, String amount, String message) {
        String msg = message == null ? "" : ",\"message\":\"" + message + "\"";
        return "{\"sourceAccountId\":\"" + from.account() + "\",\"key\":\"" + key + "\",\"amount\":\"" + amount + "\"" + msg + "}";
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

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return TestUsers.bearer(token, request);
    }
}
