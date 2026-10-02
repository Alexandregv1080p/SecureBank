package com.securebank;

import static com.securebank.TestUsers.bearer;
import static com.securebank.TestUsers.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/** Registro, login, renovação com rotação, logout, sessões e senha — pela API, com Postgres e Redis reais. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthenticationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void accessTokenCarriesOnlyTheExpectedNonSensitiveClaims() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        MvcResult result = TestUsers.login(mvc, login.email(), TestUsers.PASSWORD);
        assertThat(read(result, "$.tokenType")).isEqualTo("Bearer");
        assertThat((Integer) JsonPath.read(result.getResponse().getContentAsString(), "$.expiresIn")).isEqualTo(900);

        String claims = TestUsers.claims(read(result, "$.accessToken"));
        assertThat((String) JsonPath.read(claims, "$.iss")).isEqualTo("securebank");
        Object audience = JsonPath.read(claims, "$.aud"); // uma só audiência pode vir como string (RFC 7519)
        assertThat(audience instanceof java.util.List<?> list ? list : java.util.List.of(audience))
                .containsExactly("securebank-api");
        assertThat((java.util.List<String>) JsonPath.read(claims, "$.roles")).containsExactly("CUSTOMER");
        assertThat((String) JsonPath.read(claims, "$.sub")).isEqualTo(login.userId());
        assertThat((String) JsonPath.read(claims, "$.cid")).isEqualTo(login.customerId());
        assertThat((java.util.List<String>) JsonPath.read(claims, "$.amr")).containsExactly("pwd");
        assertThat((Integer) JsonPath.read(claims, "$.exp") - (Integer) JsonPath.read(claims, "$.iat")).isEqualTo(900);
        assertThat(claims).doesNotContain("password", "email", login.email(), "document");
    }

    @Test
    void passwordPolicyIsEnforcedAtRegistration() throws Exception {
        for (String weak : new String[] {"short", "password1234", "aaaaaaaaaaaaaaaa", "ana-teste-12345678"}) {
            // o último contém a parte local do e-mail
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(TestUsers.registerBody("ana-teste@example.com", weak)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
        }
    }

    @Test
    void passwordsAreStoredOnlyAsArgon2idAndNeverReturned() throws Exception {
        String email = TestUsers.newEmail();
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(TestUsers.registerBody(email, TestUsers.PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String loginResponse = TestUsers.login(mvc, email, TestUsers.PASSWORD).getResponse().getContentAsString();

        String stored = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(stored).startsWith("{argon2@SpringSecurity_v5_8}$argon2id$").doesNotContain(TestUsers.PASSWORD);
        assertThat(response).doesNotContain(TestUsers.PASSWORD).doesNotContain("hash");
        assertThat(loginResponse).doesNotContain(TestUsers.PASSWORD).doesNotContain("hash");
    }

    @Test
    void loginFailureLooksTheSameForUnknownUserAndWrongPassword() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        String wrongPassword = attempt(login.email(), "Wrong-Password-123456").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = attempt(TestUsers.newEmail(), "Wrong-Password-123456").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        for (String body : new String[] {wrongPassword, unknownUser}) {
            assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("INVALID_CREDENTIALS");
            assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("Invalid credentials");
        }
    }

    @Test
    void repeatedFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        for (int i = 0; i < 5; i++) {
            attempt(login.email(), "Wrong-Password-123456").andExpect(status().isUnauthorized());
        }
        attempt(login.email(), TestUsers.PASSWORD) // senha certa, mas a conta está bloqueada temporariamente
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(header().exists("Retry-After"));

        // outro usuário não é afetado
        TestUsers.registerAndLogin(mvc);

        String admin = TestUsers.adminToken(mvc);
        mvc.perform(bearer(admin, get("/api/v1/audit?event=LOGIN_FAILED&userId=" + login.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6)); // 5 senhas erradas + 1 recusado por bloqueio
    }

    @Test
    void refreshRotatesTokensAndReuseRevokesTheWholeSession() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        MvcResult rotated = refresh(login.refreshToken()).andExpect(status().isOk()).andReturn();
        String newAccess = read(rotated, "$.accessToken");
        String newRefresh = read(rotated, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(login.refreshToken());
        mvc.perform(bearer(newAccess, get("/api/v1/customers/me"))).andExpect(status().isOk());

        // alguém reapresenta o token antigo (roubado, ou cliente duplicado): reuso detectado
        refresh(login.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // a sessão inteira caiu: o token novo e o access token novo também deixam de valer, na hora
        refresh(newRefresh).andExpect(status().isUnauthorized());
        mvc.perform(bearer(newAccess, get("/api/v1/customers/me"))).andExpect(status().isUnauthorized());

        String admin = TestUsers.adminToken(mvc);
        mvc.perform(bearer(admin, get("/api/v1/audit?event=REFRESH_TOKEN_REUSE_DETECTED&userId=" + login.userId())))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        Integer plain = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                login.refreshToken());
        Integer hashed = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                com.securebank.authentication.domain.RefreshToken.hash(login.refreshToken()));

        assertThat(plain).isZero();
        assertThat(hashed).isEqualTo(1);
    }

    @Test
    void malformedRefreshTokensAreRejectedUniformly() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutKillsTheAccessTokenAndTheRefreshTokenImmediately() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        mvc.perform(bearer(login.accessToken(), get("/api/v1/customers/me"))).andExpect(status().isOk());

        mvc.perform(bearer(login.accessToken(), post("/api/v1/auth/logout"))).andExpect(status().isNoContent());

        mvc.perform(bearer(login.accessToken(), get("/api/v1/customers/me"))).andExpect(status().isUnauthorized());
        refresh(login.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionsAreListedAndCanBeRevokedIndividually() throws Exception {
        TestUsers.Login first = TestUsers.registerAndLogin(mvc);
        MvcResult second = TestUsers.login(mvc, first.email(), TestUsers.PASSWORD);
        String secondToken = read(second, "$.accessToken");

        MvcResult listed = mvc.perform(bearer(first.accessToken(), get("/api/v1/security/sessions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.current==true)]", hasSize(1)))
                .andExpect(jsonPath("$[0].ip").value("127.0.0.0")) // IP mascarado
                .andReturn();
        assertThat(listed.getResponse().getContentAsString()).doesNotContain("127.0.0.1");
        java.util.List<String> others = JsonPath.read(listed.getResponse().getContentAsString(),
                "$[?(@.current==false)].id");
        String otherSessionId = others.get(0);

        // outro usuário não enxerga nem revoga sessão alheia: 404
        String stranger = TestUsers.registerAndLogin(mvc).accessToken();
        mvc.perform(bearer(stranger, delete("/api/v1/security/sessions/" + otherSessionId)))
                .andExpect(status().isNotFound());

        mvc.perform(bearer(first.accessToken(), delete("/api/v1/security/sessions/" + otherSessionId)))
                .andExpect(status().isNoContent());

        // a revogada cai na hora; a atual segue valendo
        mvc.perform(bearer(secondToken, get("/api/v1/customers/me"))).andExpect(status().isUnauthorized());
        mvc.perform(bearer(first.accessToken(), get("/api/v1/customers/me"))).andExpect(status().isOk());
        mvc.perform(bearer(first.accessToken(), get("/api/v1/security/sessions"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void changingThePasswordRevokesTheOtherSessions() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        String otherDevice = read(TestUsers.login(mvc, login.email(), TestUsers.PASSWORD), "$.accessToken");
        String newPassword = "Another-Strong-Pass-77";

        changePassword(login.accessToken(), "Wrong-Current-Password-1", newPassword)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        changePassword(login.accessToken(), TestUsers.PASSWORD, "short").andExpect(status().isBadRequest());

        changePassword(login.accessToken(), TestUsers.PASSWORD, newPassword).andExpect(status().isNoContent());

        mvc.perform(bearer(otherDevice, get("/api/v1/customers/me"))).andExpect(status().isUnauthorized());
        mvc.perform(bearer(login.accessToken(), get("/api/v1/customers/me"))).andExpect(status().isOk());
        attempt(login.email(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());
        attempt(login.email(), newPassword).andExpect(status().isOk());

        String admin = TestUsers.adminToken(mvc);
        mvc.perform(bearer(admin, get("/api/v1/audit?event=PASSWORD_CHANGED&userId=" + login.userId())))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void auditTrailRecordsLoginsWithMaskedIpAndTraceId() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        String admin = TestUsers.adminToken(mvc);
        mvc.perform(bearer(admin, get("/api/v1/audit?event=LOGIN_SUCCESS&userId=" + login.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].event").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.items[0].userId").value(login.userId()))
                .andExpect(jsonPath("$.items[0].ip").value("127.0.0.0"))
                .andExpect(jsonPath("$.items[0].traceId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].detail").doesNotExist());
        mvc.perform(bearer(admin, get("/api/v1/audit?event=USER_REGISTERED&userId=" + login.userId())))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------- helpers ----------

    private ResultActions attempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/security/password")).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}", current, next)));
    }
}
