package com.securebank;

import static com.securebank.TestUsers.bearer;
import static com.securebank.TestUsers.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.securebank.authentication.domain.Totp;
import java.time.Instant;
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

/** MFA TOTP: cadastro em duas etapas, login em duas etapas, anti-replay e anti-força-bruta. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class MfaTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** Código válido para o passo atual + offset (a janela aceita -1, 0 e +1). */
    private static String code(String secret, int offset) {
        return Totp.code(secret, Totp.stepOf(Instant.now()) + offset);
    }

    @Test
    void enablingMfaIsATwoStepProcessAndTheSecretIsStoredEncrypted() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        MvcResult setup = mvc.perform(bearer(login.accessToken(), post("/api/v1/security/mfa")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/SecureBank:")))
                .andReturn();
        String secret = read(setup, "$.secret");

        // enquanto não confirma, o login segue normal e o segredo não está em claro no banco
        assertThat(TestUsers.login(mvc, login.email(), TestUsers.PASSWORD).getResponse().getContentAsString())
                .contains("accessToken");
        String stored = jdbc.queryForObject("select secret_cipher from mfa_devices where user_id = ?::uuid",
                String.class, login.userId());
        assertThat(stored).doesNotContain(secret);

        confirm(login.accessToken(), "000000").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
        confirm(login.accessToken(), code(secret, 0)).andExpect(status().isNoContent());

        // já ligado: não dá para cadastrar de novo
        mvc.perform(bearer(login.accessToken(), post("/api/v1/security/mfa"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MFA_ALREADY_ENABLED"));
    }

    @Test
    void loginWithMfaNeedsTheSecondFactorAndTheCodeCannotBeReplayed() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        String secret = enableMfa(login);

        // etapa 1: senha certa não entrega tokens de sessão
        MvcResult step1 = TestUsers.login(mvc, login.email(), TestUsers.PASSWORD);
        String body = step1.getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(body, "$.mfaRequired")).isTrue();
        assertThat(body).doesNotContain("accessToken").doesNotContain("refreshToken");
        String mfaToken = read(step1, "$.mfaToken");

        // o token de desafio não vale como access token
        mvc.perform(bearer(mfaToken, get("/api/v1/customers/me"))).andExpect(status().isUnauthorized());

        verify(mfaToken, "000000").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));

        // etapa 2: código do passo seguinte (o do passo atual foi consumido ao confirmar)
        String good = code(secret, 1);
        MvcResult step2 = verify(mfaToken, good).andExpect(status().isOk()).andReturn();
        String accessToken = read(step2, "$.accessToken");
        assertThat((java.util.List<String>) JsonPath.read(TestUsers.claims(accessToken), "$.amr"))
                .containsExactly("pwd", "otp");
        mvc.perform(bearer(accessToken, get("/api/v1/customers/me"))).andExpect(status().isOk());

        // replay: o mesmo código (e qualquer passo já consumido) é recusado, mesmo com desafio novo
        String freshChallenge = read(TestUsers.login(mvc, login.email(), TestUsers.PASSWORD), "$.mfaToken");
        verify(freshChallenge, good).andExpect(status().isUnauthorized());
        verify(freshChallenge, code(secret, 0)).andExpect(status().isUnauthorized());

        String admin = TestUsers.adminToken(mvc);
        mvc.perform(bearer(admin, get("/api/v1/audit?event=MFA_FAILED&userId=" + login.userId())))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void anAccessTokenIsNotAValidMfaChallenge() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        enableMfa(login);

        verify(login.accessToken(), "123456").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CHALLENGE"));
        verify("garbage", "123456").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCodesAreThrottled() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        String secret = enableMfa(login);
        String mfaToken = read(TestUsers.login(mvc, login.email(), TestUsers.PASSWORD), "$.mfaToken");

        for (int i = 0; i < 5; i++) {
            verify(mfaToken, "000000").andExpect(status().isUnauthorized());
        }
        // depois de 5 erros nem o código certo passa
        verify(mfaToken, code(secret, 1)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void disablingMfaRequiresAValidCode() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        String secret = enableMfa(login);

        // um token roubado sozinho não desliga o MFA
        disable(login.accessToken(), "000000").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
        disable(login.accessToken(), code(secret, 1)).andExpect(status().isNoContent());

        assertThat(TestUsers.login(mvc, login.email(), TestUsers.PASSWORD).getResponse().getContentAsString())
                .contains("accessToken");
        disable(login.accessToken(), "123456").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MFA_NOT_ENABLED"));

        String admin = TestUsers.adminToken(mvc);
        mvc.perform(bearer(admin, get("/api/v1/audit?event=MFA_DISABLED&userId=" + login.userId())))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------- helpers ----------

    /** Cadastra e confirma o MFA; devolve o segredo (o do app autenticador). */
    private String enableMfa(TestUsers.Login login) throws Exception {
        String secret = read(mvc.perform(bearer(login.accessToken(), post("/api/v1/security/mfa")))
                .andExpect(status().isCreated()).andReturn(), "$.secret");
        confirm(login.accessToken(), code(secret, 0)).andExpect(status().isNoContent());
        return secret;
    }

    private ResultActions confirm(String token, String code) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/security/mfa/confirm")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions disable(String token, String code) throws Exception {
        return mvc.perform(bearer(token, delete("/api/v1/security/mfa")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions verify(String mfaToken, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + code + "\"}"));
    }
}
