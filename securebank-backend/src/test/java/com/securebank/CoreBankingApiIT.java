package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Jornada completa do Core Banking pela API, com Postgres real (Flyway + JPA + triggers). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CoreBankingApiIT {

    private static final String BARCODE = "34191790010104351004791020150008291070026000";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    // ---------- cliente ----------

    @Test
    void customerReadsAndUpdatesOwnProfileWithMaskedDocument() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        mvc.perform(as(login.accessToken(), get("/api/v1/customers/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(login.customerId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value(login.email()))
                .andExpect(jsonPath("$.document").value(matchesPattern("\\*\\*\\*\\.\\d{3}\\.\\*\\*\\*-\\*\\*")));

        mvc.perform(as(login.accessToken(), patch("/api/v1/customers/me")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+5521988887777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+5521988887777"))
                .andExpect(jsonPath("$.email").value(login.email())); // não enviado = não muda
    }

    @Test
    void registrationRejectsDuplicatesWithoutSayingWhichFieldExists() throws Exception {
        TestUsers.Login existing = TestUsers.registerAndLogin(mvc);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(TestUsers.registerBody(existing.email(), TestUsers.PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_EXISTS"));
    }

    @Test
    void invalidRegistrationDataIsRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"document\":\"\",\"email\":\"x\",\"phone\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isNotEmpty());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"Ana","document":"111.111.111-11","email":"a@b.co","phone":"+5511999990001","password":"%s"}""",
                                TestUsers.PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE")); // CPF inválido (regra de domínio)
    }

    // ---------- autenticação / erro padrão ----------

    @Test
    void requestWithoutTokenIs401InTheStandardErrorFormat() throws Exception {
        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v1/accounts"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().exists("X-Trace-Id"));

        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void errorsNeverLeakInternals() throws Exception {
        String c = customer();
        String body = mvc.perform(as(c, get("/api/v1/accounts/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Exception", "at com.", "org.springframework", "select ", "stackTrace");
        // trace id do cliente é aceito se tiver formato seguro, e ecoado
        mvc.perform(as(c, get("/api/v1/accounts")).header("X-Trace-Id", "trace-abc-12345678"))
                .andExpect(header().string("X-Trace-Id", "trace-abc-12345678"));
        // formato inseguro (log injection) é descartado
        mvc.perform(as(c, get("/api/v1/accounts")).header("X-Trace-Id", "bad\r\nvalue"))
                .andExpect(header().string("X-Trace-Id", not("bad\r\nvalue")));
    }

    // ---------- conta, depósito, saque, extrato ----------

    @Test
    void depositWithdrawBalanceAndStatement() throws Exception {
        String c = customer();
        MvcResult opened = mvc.perform(as(c, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CHECKING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance.amount").value("0.00"))
                .andExpect(jsonPath("$.balance.currency").value("BRL"))
                .andExpect(jsonPath("$.branch").value("0001"))
                .andExpect(jsonPath("$.accountNumber").value(matchesPattern("\\d{6,}-\\d")))
                .andReturn();
        String account = read(opened, "$.id");

        deposit(c, account, "1000.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.direction").value("CREDIT"))
                .andExpect(jsonPath("$.balanceAfter.amount").value("1000.00"));
        withdraw(c, account, "200.50")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("DEBIT"))
                .andExpect(jsonPath("$.balanceAfter.amount").value("799.50"));

        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/balance")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.amount").value("799.50"));

        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/statement")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].type").value("WITHDRAW")) // mais recente primeiro
                .andExpect(jsonPath("$.items[1].type").value("DEPOSIT"));

        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/statement?size=1&page=1")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/statement?from=2998-01-01")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/statement?size=1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/statement?from=ontem")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void insufficientFundsIs422AndLeavesTheBalanceAlone() throws Exception {
        String c = customer();
        String account = openAccount(c);
        deposit(c, account, "100.00").andExpect(status().isCreated());

        withdraw(c, account, "100.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        assertThat(balance(c, account)).isEqualTo("100.00");
    }

    @Test
    void amountsAreValidated() throws Exception {
        String c = customer();
        String account = openAccount(c);

        for (String bad : List.of("0", "-5", "10.001", "\"abc\"", "null")) {
            mvc.perform(as(c, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":" + bad + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(as(c, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(as(c, get("/api/v1/accounts/not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
        assertThat(balance(c, account)).isEqualTo("0.00");
    }

    // ---------- transferência ----------

    @Test
    void transferMovesMoneyAtomicallyAndAppearsInBothStatements() throws Exception {
        String alice = customer();
        String bob = customer();
        String from = openAccount(alice);
        MvcResult bobAccount = openAccountResult(bob);
        deposit(alice, from, "1000.00").andExpect(status().isCreated());

        MvcResult done = transfer(alice, from, bobAccount, "300.00", "idem-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount.amount").value("300.00"))
                .andExpect(jsonPath("$.debitTransactionId").isNotEmpty())
                .andExpect(jsonPath("$.creditTransactionId").isNotEmpty())
                .andReturn();
        String transferId = read(done, "$.id");

        assertThat(balance(alice, from)).isEqualTo("700.00");
        assertThat(balance(bob, read(bobAccount, "$.id"))).isEqualTo("300.00");

        mvc.perform(as(alice, get("/api/v1/accounts/" + from + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.items[0].reference").value(transferId));
        mvc.perform(as(bob, get("/api/v1/accounts/" + read(bobAccount, "$.id") + "/statement")))
                .andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].balanceAfter.amount").value("300.00"))
                .andExpect(jsonPath("$.items[0].reference").value(transferId));

        mvc.perform(as(alice, get("/api/v1/transfers/" + transferId))).andExpect(status().isOk());
        mvc.perform(as(alice, get("/api/v1/transfers"))).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void reusedIdempotencyKeyNeverDebitsTwice() throws Exception {
        String alice = customer();
        String from = openAccount(alice);
        MvcResult to = openAccountResult(customer());
        deposit(alice, from, "1000.00").andExpect(status().isCreated());
        String key = "idem-" + UUID.randomUUID();

        String first = transfer(alice, from, to, "100.00", key).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        // retry com a mesma chave: mesma resposta (replay), sem novo débito
        transfer(alice, from, to, "100.00", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value((String) com.jayway.jsonpath.JsonPath.read(first, "$.id")));

        assertThat(balance(alice, from)).isEqualTo("900.00");
    }

    @Test
    void failedTransferChangesNothing() throws Exception {
        String alice = customer();
        MvcResult own = openAccountResult(alice);
        String from = read(own, "$.id");
        MvcResult to = openAccountResult(customer());
        deposit(alice, from, "50.00").andExpect(status().isCreated());

        transfer(alice, from, to, "50.01", "idem-" + UUID.randomUUID())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        transfer(alice, from, own, "1.00", "idem-" + UUID.randomUUID()) // para a própria conta
                .andExpect(status().isBadRequest());

        assertThat(balance(alice, from)).isEqualTo("50.00");
        mvc.perform(as(alice, get("/api/v1/transfers"))).andExpect(jsonPath("$.totalElements").value(0)); // nada gravado
    }

    @Test
    void transferToUnknownAccountIs404AndMissingIdempotencyKeyIs400() throws Exception {
        String alice = customer();
        String from = openAccount(alice);
        deposit(alice, from, "50.00").andExpect(status().isCreated());

        mvc.perform(as(alice, post("/api/v1/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .content(json("""
                                {"sourceAccountId":"%s","destinationBranch":"0001","destinationAccountNumber":"999999-9","amount":"1.00"}""",
                                from)))
                .andExpect(status().isNotFound());
        mvc.perform(as(alice, post("/api/v1/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"sourceAccountId":"%s","destinationBranch":"0001","destinationAccountNumber":"999999-9","amount":"1.00"}""",
                                from)))
                .andExpect(status().isBadRequest());
    }

    // ---------- autorização por recurso (IDOR) ----------

    @Test
    void aCustomerCannotTouchAnotherCustomersAccountsOrTransfers() throws Exception {
        String alice = customer();
        String bob = customer();
        String aliceAccount = openAccount(alice);
        deposit(alice, aliceAccount, "500.00").andExpect(status().isCreated());
        MvcResult bobAccount = openAccountResult(bob);
        String transferId = read(transfer(alice, aliceAccount, bobAccount, "10.00", "idem-" + UUID.randomUUID())
                .andReturn(), "$.id");

        // conta de outro dono e conta inexistente são indistinguíveis: 404 com o mesmo formato
        for (String path : List.of("/api/v1/accounts/" + aliceAccount, "/api/v1/accounts/" + aliceAccount + "/balance",
                "/api/v1/accounts/" + aliceAccount + "/statement", "/api/v1/accounts/" + aliceAccount + "/limits",
                "/api/v1/transfers/" + transferId)) {
            mvc.perform(as(bob, get(path))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        deposit(bob, aliceAccount, "1.00").andExpect(status().isNotFound());
        withdraw(bob, aliceAccount, "1.00").andExpect(status().isNotFound());
        transfer(bob, aliceAccount, bobAccount, "1.00", "idem-" + UUID.randomUUID()).andExpect(status().isNotFound());

        mvc.perform(as(bob, get("/api/v1/accounts"))).andExpect(jsonPath("$", hasSize(1)));
        assertThat(balance(alice, aliceAccount)).isEqualTo("490.00");
    }

    // ---------- pagamento ----------

    @Test
    void paymentDebitsTheAccount() throws Exception {
        String c = customer();
        String account = openAccount(c);
        deposit(c, account, "500.00").andExpect(status().isCreated());
        String key = "idem-" + UUID.randomUUID();

        MvcResult paid = pay(c, account, "120.50", BARCODE, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andReturn();

        assertThat(balance(c, account)).isEqualTo("379.50");
        mvc.perform(as(c, get("/api/v1/payments/" + read(paid, "$.id")))).andExpect(status().isOk());
        mvc.perform(as(c, get("/api/v1/payments"))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("PAYMENT"));

        pay(c, account, "120.50", BARCODE, key).andExpect(status().isCreated()) // mesma chave: replay, sem novo débito
                .andExpect(header().string("Idempotency-Replayed", "true"));
        pay(c, account, "10.00", "123", "idem-" + UUID.randomUUID()).andExpect(status().isBadRequest()); // boleto inválido
        pay(c, account, "9999.00", BARCODE, "idem-" + UUID.randomUUID())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(balance(c, account)).isEqualTo("379.50");

        // pagamento de outro cliente: 404
        mvc.perform(as(customer(), get("/api/v1/payments/" + read(paid, "$.id")))).andExpect(status().isNotFound());
    }

    // ---------- limites ----------

    @Test
    void limitsAreEnforcedPerOperationAndPerDay() throws Exception {
        String c = customer();
        String account = openAccount(c);
        MvcResult to = openAccountResult(customer());
        deposit(c, account, "20000.00").andExpect(status().isCreated());

        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/limits")))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.type=='TRANSFER')].perOperation.amount").value("5000.00"))
                .andExpect(jsonPath("$[?(@.type=='TRANSFER')].usedToday.amount").value("0.00"));

        transfer(c, account, to, "5000.01", "idem-" + UUID.randomUUID())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));

        transfer(c, account, to, "5000.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());
        transfer(c, account, to, "5000.00", "idem-" + UUID.randomUUID()).andExpect(status().isCreated());
        transfer(c, account, to, "0.01", "idem-" + UUID.randomUUID()) // estoura o diário (10.000)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));

        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/limits")))
                .andExpect(jsonPath("$[?(@.type=='TRANSFER')].usedToday.amount").value("10000.00"))
                .andExpect(jsonPath("$[?(@.type=='TRANSFER')].remainingToday.amount").value("0.00"))
                .andExpect(jsonPath("$[?(@.type=='WITHDRAW')].usedToday.amount").value("0.00")); // limites independentes

        withdraw(c, account, "1000.01") // teto de saque por operação: 1.000
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
        assertThat(balance(c, account)).isEqualTo("10000.00");
    }

    // ---------- cliente bloqueado ----------

    @Test
    void blockedCustomerCannotMoveMoney() throws Exception {
        String c = customer();
        String account = openAccount(c);
        deposit(c, account, "100.00").andExpect(status().isCreated());

        String customerId = read(mvc.perform(as(c, get("/api/v1/customers/me"))).andReturn(), "$.id");
        jdbc.update("update customers set status = 'BLOCKED' where id = ?", UUID.fromString(customerId));

        deposit(c, account, "1.00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_ACTIVE"));
        withdraw(c, account, "1.00").andExpect(status().isUnprocessableEntity());
        mvc.perform(as(c, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"SAVINGS\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/balance"))).andExpect(status().isOk()); // consulta segue
    }

    // ---------- banco: livro-razão append-only ----------

    @Test
    void ledgerRowsCannotBeDeletedOrAlteredInTheDatabase() throws Exception {
        String c = customer();
        String account = openAccount(c);
        deposit(c, account, "100.00").andExpect(status().isCreated());
        UUID accountId = UUID.fromString(account);

        assertThatThrownBy(() -> jdbc.update("delete from transactions where account_id = ?", accountId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update transactions set amount = 1.00 where account_id = ?", accountId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update accounts set balance = -1 where id = ?", accountId))
                .isInstanceOf(DataAccessException.class); // CHECK balance >= 0

        // status é a única coluna mutável do lançamento (estorno)
        assertThat(jdbc.update("update transactions set status = 'REVERSED' where account_id = ?", accountId)).isEqualTo(1);
    }

    // ---------- concorrência ----------

    @Test
    void concurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
        String c = customer();
        String account = openAccount(c);
        deposit(c, account, "1000.00").andExpect(status().isCreated());

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                gate.await();
                return withdraw(c, account, "300.00").andReturn().getResponse().getStatus();
            }));
        }
        gate.countDown();
        int succeeded = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            // Com retry em conflito de versão, ninguém fica com 409: ou sacou (201) ou, relendo o saldo, 422.
            assertThat(code).isIn(201, 422);
            if (code == 201) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(3); // 1000 / 300 = exatamente 3 saques cabem
        String expected = new java.math.BigDecimal("1000.00").subtract(new java.math.BigDecimal("300.00")
                .multiply(java.math.BigDecimal.valueOf(succeeded))).toPlainString();
        assertThat(balance(c, account)).isEqualTo(expected);
        mvc.perform(as(c, get("/api/v1/accounts/" + account + "/statement")))
                .andExpect(jsonPath("$.totalElements").value(1 + succeeded)); // saldo e razão batem
    }

    // ---------- helpers ----------

    /** Autentica a requisição com o access token (JWT) do cliente. */
    private static MockHttpServletRequestBuilder as(String accessToken, MockHttpServletRequestBuilder request) {
        return TestUsers.bearer(accessToken, request);
    }

    /** Registra um cliente novo e devolve o access token dele. */
    private String customer() throws Exception {
        return TestUsers.registerAndLogin(mvc).accessToken();
    }

    private MvcResult openAccountResult(String customerId) throws Exception {
        return mvc.perform(as(customerId, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CHECKING\"}"))
                .andExpect(status().isCreated()).andReturn();
    }

    private String openAccount(String customerId) throws Exception {
        return read(openAccountResult(customerId), "$.id");
    }

    private ResultActions deposit(String customerId, String account, String amount) throws Exception {
        return mvc.perform(as(customerId, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
    }

    private ResultActions withdraw(String customerId, String account, String amount) throws Exception {
        return mvc.perform(as(customerId, post("/api/v1/accounts/" + account + "/withdrawals")).header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
    }

    private ResultActions transfer(String customerId, String sourceAccount, MvcResult destination, String amount,
            String key) throws Exception {
        String branch = read(destination, "$.branch");
        String number = read(destination, "$.accountNumber");
        return mvc.perform(as(customerId, post("/api/v1/transfers")).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content(json("""
                        {"sourceAccountId":"%s","destinationBranch":"%s","destinationAccountNumber":"%s","amount":"%s","description":"teste"}""",
                        sourceAccount, branch, number, amount)));
    }

    private ResultActions pay(String customerId, String account, String amount, String barcode, String key)
            throws Exception {
        return mvc.perform(as(customerId, post("/api/v1/payments")).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content(json("""
                        {"accountId":"%s","amount":"%s","barcode":"%s","description":"conta"}""", account, amount,
                        barcode)));
    }

    private String balance(String customerId, String account) throws Exception {
        return read(mvc.perform(as(customerId, get("/api/v1/accounts/" + account + "/balance"))).andReturn(),
                "$.balance.amount");
    }

    private static String json(String template, Object... args) {
        return String.format(template, args);
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
