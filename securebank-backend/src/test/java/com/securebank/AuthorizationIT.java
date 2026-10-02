package com.securebank;

import static com.securebank.TestUsers.bearer;
import static com.securebank.TestUsers.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
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

/** RBAC (CUSTOMER/SUPPORT/ADMIN), negar-por-padrão, operações administrativas e a trilha de auditoria delas. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthorizationIT {

    private static final String BARCODE = "34191790010104351004791020150008291070026000";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    // ---------- 401: anônimo ----------

    @Test
    void anonymousUsersGetUnauthorizedOnEveryProtectedEndpoint() throws Exception {
        String id = UUID.randomUUID().toString();
        List<String> gets = List.of("/api/v1/customers/me", "/api/v1/accounts", "/api/v1/accounts/" + id,
                "/api/v1/accounts/" + id + "/balance", "/api/v1/accounts/" + id + "/statement",
                "/api/v1/accounts/" + id + "/limits", "/api/v1/transfers", "/api/v1/transfers/" + id, "/api/v1/payments",
                "/api/v1/security/sessions", "/api/v1/audit", "/api/v1/admin/customers/" + id);
        for (String path : gets) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        List<String> posts = List.of("/api/v1/accounts", "/api/v1/accounts/" + id + "/deposits",
                "/api/v1/accounts/" + id + "/withdrawals", "/api/v1/transfers", "/api/v1/payments",
                "/api/v1/auth/logout", "/api/v1/security/mfa", "/api/v1/security/password", "/api/v1/admin/users",
                "/api/v1/admin/accounts/" + id + "/block");
        for (String path : posts) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/actuator/health")).andExpect(status().isOk()); // só o health é público
        mvc.perform(get("/api/v1/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void routesNobodyDeclaredAreClosedByDefault() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist")).andExpect(status().isUnauthorized());
        String customer = TestUsers.registerAndLogin(mvc).accessToken();
        mvc.perform(bearer(customer, get("/api/v1/does-not-exist"))).andExpect(status().isForbidden());
    }

    // ---------- 403: papel errado ----------

    @Test
    void customersCannotReachStaffEndpointsAndTheAttemptIsAudited() throws Exception {
        TestUsers.Login customer = TestUsers.registerAndLogin(mvc);
        String id = UUID.randomUUID().toString();

        mvc.perform(bearer(customer.accessToken(), get("/api/v1/audit"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(bearer(customer.accessToken(), get("/api/v1/admin/customers/" + id))).andExpect(status().isForbidden());
        mvc.perform(bearer(customer.accessToken(), put("/api/v1/admin/accounts/" + id + "/limits/TRANSFER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"perOperation\":\"999999.00\",\"daily\":\"999999.00\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(customer.accessToken(), post("/api/v1/admin/accounts/" + id + "/block"))).andExpect(status().isForbidden());
        mvc.perform(bearer(customer.accessToken(), post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@example.com\",\"password\":\"" + TestUsers.PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        String admin = TestUsers.adminToken(mvc);
        mvc.perform(bearer(admin, get("/api/v1/audit?event=ACCESS_DENIED&userId=" + customer.userId())))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.items[*].detail", hasItem("GET /api/v1/audit")));
    }

    @Test
    void staffDoNotOperateCustomerAccounts() throws Exception {
        for (String role : List.of("SUPPORT", "ADMIN")) {
            String staff = TestUsers.staffToken(mvc, role);
            mvc.perform(bearer(staff, get("/api/v1/accounts"))).andExpect(status().isForbidden());
            mvc.perform(bearer(staff, get("/api/v1/customers/me"))).andExpect(status().isForbidden());
            mvc.perform(bearer(staff, post("/api/v1/transfers")).contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header("Idempotency-Key", "idem-" + UUID.randomUUID())).andExpect(status().isForbidden());
            // mas qualquer usuário autenticado gerencia a própria segurança
            mvc.perform(bearer(staff, get("/api/v1/security/sessions"))).andExpect(status().isOk());
        }
    }

    @Test
    void supportCanReadCustomersAndAuditButNotChangeAnything() throws Exception {
        TestUsers.Login customer = TestUsers.registerAndLogin(mvc);
        String support = TestUsers.staffToken(mvc, "SUPPORT");
        String account = openAccount(customer.accessToken());

        mvc.perform(bearer(support, get("/api/v1/admin/customers/" + customer.customerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customer.email()))
                .andExpect(jsonPath("$.document").value(org.hamcrest.Matchers.startsWith("***."))); // CPF mascarado
        mvc.perform(bearer(support, get("/api/v1/audit"))).andExpect(status().isOk());

        mvc.perform(bearer(support, put("/api/v1/admin/accounts/" + account + "/limits/TRANSFER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"perOperation\":\"1.00\",\"daily\":\"1.00\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(support, post("/api/v1/admin/accounts/" + account + "/block"))).andExpect(status().isForbidden());
        mvc.perform(bearer(support, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"y@example.com\",\"password\":\"" + TestUsers.PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- administração (ADMIN) ----------

    @Test
    void adminChangesLimitsAndTheCustomerFeelsItImmediately() throws Exception {
        TestUsers.Login customer = TestUsers.registerAndLogin(mvc);
        TestUsers.Login other = TestUsers.registerAndLogin(mvc);
        String account = openAccount(customer.accessToken());
        MvcResult destination = openAccountResult(other.accessToken());
        deposit(customer.accessToken(), account, "1000.00").andExpect(status().isCreated());
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(bearer(admin, put("/api/v1/admin/accounts/" + account + "/limits/transfer")) // case-insensitive
                .contentType(MediaType.APPLICATION_JSON).content("{\"perOperation\":\"100.00\",\"daily\":\"500.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perOperation.amount").value("100.00"));

        transfer(customer.accessToken(), account, destination, "100.01").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
        transfer(customer.accessToken(), account, destination, "100.00").andExpect(status().isCreated());

        // regras de coerência e entrada inválida
        mvc.perform(bearer(admin, put("/api/v1/admin/accounts/" + account + "/limits/TRANSFER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"perOperation\":\"500.00\",\"daily\":\"100.00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VALUE"));
        mvc.perform(bearer(admin, put("/api/v1/admin/accounts/" + account + "/limits/NOPE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"perOperation\":\"1.00\",\"daily\":\"2.00\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(bearer(admin, put("/api/v1/admin/accounts/" + UUID.randomUUID() + "/limits/TRANSFER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"perOperation\":\"1.00\",\"daily\":\"2.00\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(bearer(admin, get("/api/v1/audit?event=LIMIT_CHANGED")))
                .andExpect(jsonPath("$.items[0].detail").value("TRANSFER 100.00/500.00"));
    }

    @Test
    void adminBlocksAndUnblocksAnAccount() throws Exception {
        TestUsers.Login customer = TestUsers.registerAndLogin(mvc);
        String account = openAccount(customer.accessToken());
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(bearer(admin, post("/api/v1/admin/accounts/" + account + "/block")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        deposit(customer.accessToken(), account, "10.00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

        mvc.perform(bearer(admin, post("/api/v1/admin/accounts/" + account + "/unblock")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        deposit(customer.accessToken(), account, "10.00").andExpect(status().isCreated());

        mvc.perform(bearer(admin, get("/api/v1/audit?event=ACCOUNT_BLOCKED")))
                .andExpect(jsonPath("$.items[0].accountId").value(account));
        mvc.perform(bearer(admin, get("/api/v1/audit?event=ACCOUNT_UNBLOCKED")))
                .andExpect(jsonPath("$.items[0].accountId").value(account));
        mvc.perform(bearer(admin, post("/api/v1/admin/accounts/" + UUID.randomUUID() + "/block")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminManagesStaffUsers() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        String email = TestUsers.newEmail();

        // senha fraca, papel inválido para staff e duplicidade
        createUser(admin, email, "short", "SUPPORT").andExpect(status().isBadRequest());
        createUser(admin, email, TestUsers.PASSWORD, "CUSTOMER").andExpect(status().isBadRequest());
        MvcResult created = createUser(admin, email, TestUsers.PASSWORD, "SUPPORT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPPORT")).andReturn();
        createUser(admin, email, TestUsers.PASSWORD, "SUPPORT").andExpect(status().isConflict());
        String userId = read(created, "$.id");

        String token = read(TestUsers.login(mvc, email, TestUsers.PASSWORD), "$.accessToken");
        mvc.perform(bearer(token, get("/api/v1/audit"))).andExpect(status().isOk());

        // desativar derruba as sessões na hora e impede novo login
        mvc.perform(bearer(admin, post("/api/v1/admin/users/" + userId + "/disable"))).andExpect(status().isNoContent());
        mvc.perform(bearer(token, get("/api/v1/audit"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(bearer(admin, post("/api/v1/admin/users/" + userId + "/enable"))).andExpect(status().isNoContent());
        TestUsers.login(mvc, email, TestUsers.PASSWORD);

        // o admin não pode se desativar (evita se trancar para fora)
        String adminId = jdbc.queryForObject("select id::text from users where email = ?", String.class,
                TestUsers.ADMIN_EMAIL);
        mvc.perform(bearer(admin, post("/api/v1/admin/users/" + adminId + "/disable"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    // ---------- auditoria de operações financeiras ----------

    @Test
    void transfersAndPaymentsLeaveAnAuditTrailIncludingFailures() throws Exception {
        TestUsers.Login customer = TestUsers.registerAndLogin(mvc);
        String account = openAccount(customer.accessToken());
        MvcResult destination = openAccountResult(TestUsers.registerAndLogin(mvc).accessToken());
        deposit(customer.accessToken(), account, "200.00").andExpect(status().isCreated());

        transfer(customer.accessToken(), account, destination, "50.00").andExpect(status().isCreated());
        transfer(customer.accessToken(), account, destination, "5000.00").andExpect(status().isUnprocessableEntity()); // sem saldo
        pay(customer.accessToken(), account, "20.00").andExpect(status().isCreated());
        pay(customer.accessToken(), account, "9000.00").andExpect(status().isUnprocessableEntity());

        String admin = TestUsers.adminToken(mvc);
        String base = "/api/v1/audit?userId=" + customer.userId() + "&event=";
        mvc.perform(bearer(admin, get(base + "TRANSFER_CREATED"))).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].accountId").value(account))
                .andExpect(jsonPath("$.items[0].transactionId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].ip").value("127.0.0.0"))
                .andExpect(jsonPath("$.items[0].traceId").isNotEmpty());
        // a falha foi gravada mesmo com a transação de negócio desfeita (transação de auditoria independente)
        mvc.perform(bearer(admin, get(base + "TRANSFER_FAILED"))).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].detail").value("INSUFFICIENT_FUNDS"));
        mvc.perform(bearer(admin, get(base + "PAYMENT_CREATED"))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(bearer(admin, get(base + "PAYMENT_FAILED"))).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].detail").value("INSUFFICIENT_FUNDS"));

        // o saldo reflete só o que deu certo: 200 - 50 - 20
        mvc.perform(bearer(customer.accessToken(), get("/api/v1/accounts/" + account + "/balance")))
                .andExpect(jsonPath("$.balance.amount").value("130.00"));
    }

    @Test
    void auditLogCannotBeEditedOrDeletedAndNeverStoresSecrets() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where user_id = ?::uuid", login.userId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update audit_logs set event = 'LOGIN_FAILED' where user_id = ?::uuid",
                login.userId())).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");

        String dump = String.join(" ", jdbc.queryForList(
                "select concat_ws(' ', event, ip, trace_id, detail) from audit_logs where user_id = ?::uuid", String.class,
                login.userId()));
        assertThat(dump).doesNotContain(TestUsers.PASSWORD).doesNotContain(login.accessToken())
                .doesNotContain(login.refreshToken()).doesNotContain("127.0.0.1");
    }

    @Test
    void auditSearchValidatesItsParameters() throws Exception {
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(bearer(admin, get("/api/v1/audit?size=1000"))).andExpect(status().isBadRequest());
        mvc.perform(bearer(admin, get("/api/v1/audit?event=NOT_AN_EVENT"))).andExpect(status().isBadRequest());
        mvc.perform(bearer(admin, get("/api/v1/audit?userId=not-a-uuid"))).andExpect(status().isBadRequest());
        mvc.perform(bearer(admin, get("/api/v1/audit?size=1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].event", not(hasItem("NOPE"))));
    }

    // ---------- helpers ----------

    private ResultActions createUser(String adminToken, String email, String password, String role) throws Exception {
        return mvc.perform(bearer(adminToken, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}", email, password, role)));
    }

    private MvcResult openAccountResult(String token) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn();
    }

    private String openAccount(String token) throws Exception {
        return read(openAccountResult(token), "$.id");
    }

    private ResultActions deposit(String token, String account, String amount) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
    }

    private ResultActions transfer(String token, String account, MvcResult destination, String amount) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/transfers")).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .content(String.format("{\"sourceAccountId\":\"%s\",\"destinationBranch\":\"%s\","
                                + "\"destinationAccountNumber\":\"%s\",\"amount\":\"%s\"}", account,
                        read(destination, "$.branch"), read(destination, "$.accountNumber"), amount)));
    }

    private ResultActions pay(String token, String account, String amount) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/payments")).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .content(String.format("{\"accountId\":\"%s\",\"amount\":\"%s\",\"barcode\":\"%s\"}", account, amount,
                        BARCODE)));
    }
}
