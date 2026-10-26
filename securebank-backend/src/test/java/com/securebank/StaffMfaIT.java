package com.securebank;

import static com.securebank.TestUsers.bearer;
import static com.securebank.TestUsers.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.securebank.authentication.domain.Totp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** A equipe só opera com a verificação em duas etapas: sem ela, só a segurança da própria conta e o logout. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "securebank.security.staff-mfa-required=true")
class StaffMfaIT {

    @Autowired MockMvc mvc;

    private static String code(String secret) {
        return Totp.code(secret, Totp.stepOf(Instant.now()));
    }

    @Test
    void staffWithoutTwoStepOnlyReachesTheirOwnSecurityUntilTheyEnableItAndSignInAgain() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        String email = TestUsers.newEmail();
        mvc.perform(bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.staffBody(email, TestUsers.PASSWORD, "RISK", "MANAGER"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED")); // nem o admin do ambiente de teste passa sem 2FA

        // o admin só opera com 2FA: liga o dele (com a sessão sem OTP, que alcança a própria segurança) e entra de novo
        String adminWithOtp = enableAndSignInWithCode(TestUsers.ADMIN_EMAIL, TestUsers.ADMIN_PASSWORD);
        mvc.perform(bearer(adminWithOtp, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.staffBody(email, TestUsers.PASSWORD, "RISK", "MANAGER"))).andExpect(status().isCreated());

        // a pessoa nova, sem 2FA: tudo da equipe é recusado com um código que a tela entende
        String first = read(TestUsers.login(mvc, email, TestUsers.PASSWORD), "$.accessToken");
        for (String path : new String[] {"/api/v1/audit", "/api/v1/admin/approvals", "/api/v1/admin/customers?q=abc"}) {
            mvc.perform(bearer(first, get(path))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
        }
        // mas a segurança da própria conta e o logout ficam abertos
        mvc.perform(bearer(first, get("/api/v1/security/mfa"))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.required").value(true));
        mvc.perform(bearer(first, get("/api/v1/security/sessions"))).andExpect(status().isOk());

        // liga o 2FA; a sessão atual continua restrita, a próxima (com o código) já opera
        String secret = read(mvc.perform(bearer(first, post("/api/v1/security/mfa"))).andExpect(status().isCreated()).andReturn(), "$.secret");
        mvc.perform(bearer(first, post("/api/v1/security/mfa/confirm")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code(secret) + "\"}")).andExpect(status().isNoContent());
        mvc.perform(bearer(first, get("/api/v1/audit"))).andExpect(status().isForbidden());

        MvcResult step1 = TestUsers.login(mvc, email, TestUsers.PASSWORD);
        String mfaToken = read(step1, "$.mfaToken");
        // o mesmo código não vale duas vezes (anti-replay): espera o próximo passo da janela
        String next = Totp.code(secret, Totp.stepOf(Instant.now()) + 1);
        String second = read(mvc.perform(post("/api/v1/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + next + "\"}")).andExpect(status().isOk()).andReturn(), "$.accessToken");
        assertThat(TestUsers.claims(second)).contains("otp");
        mvc.perform(bearer(second, get("/api/v1/audit"))).andExpect(status().isOk());
    }

    @Test
    void customersAreNotAffected() throws Exception {
        String customer = TestUsers.registerAndLogin(mvc).accessToken();
        mvc.perform(bearer(customer, get("/api/v1/accounts"))).andExpect(status().isOk());
        mvc.perform(bearer(customer, get("/api/v1/security/mfa"))).andExpect(jsonPath("$.required").value(false)); // exigência é só da equipe
    }

    /** Liga o 2FA do usuário e devolve um access token da sessão nova, aberta com o código. */
    private String enableAndSignInWithCode(String email, String password) throws Exception {
        String plain = read(TestUsers.login(mvc, email, password), "$.accessToken");
        String secret = read(mvc.perform(bearer(plain, post("/api/v1/security/mfa"))).andExpect(status().isCreated()).andReturn(), "$.secret");
        mvc.perform(bearer(plain, post("/api/v1/security/mfa/confirm")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code(secret) + "\"}")).andExpect(status().isNoContent());
        String mfaToken = read(TestUsers.login(mvc, email, password), "$.mfaToken");
        String next = Totp.code(secret, Totp.stepOf(Instant.now()) + 1);
        return read(mvc.perform(post("/api/v1/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + next + "\"}")).andExpect(status().isOk()).andReturn(), "$.accessToken");
    }
}
