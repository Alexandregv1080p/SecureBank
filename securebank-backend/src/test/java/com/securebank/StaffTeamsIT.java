package com.securebank;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Equipe com departamentos e hierarquia: cada departamento vê e altera só o seu, e mudar de posição derruba as sessões. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class StaffTeamsIT {

    @Autowired MockMvc mvc;

    private String createUser(String admin, String role, String level) throws Exception {
        String email = TestUsers.newEmail();
        MvcResult created = mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.staffBody(email, TestUsers.PASSWORD, role, level))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void theTokenCarriesTheDepartmentAndLevelAndTheRoleFollowsTheDepartment() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        String email = TestUsers.newEmail();
        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.staffBody(email, TestUsers.PASSWORD, "RISK", "MANAGER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("RISK"))
                .andExpect(jsonPath("$.department").value("RISCO")).andExpect(jsonPath("$.level").value("MANAGER"))
                .andExpect(jsonPath("$.mfaEnabled").value(false));

        String claims = TestUsers.claims(TestUsers.read(TestUsers.login(mvc, email, TestUsers.PASSWORD), "$.accessToken"));
        org.assertj.core.api.Assertions.assertThat(claims).contains("\"RISK\"").contains("\"RISCO\"").contains("\"MANAGER\"");
        // cliente não carrega departamento
        String customer = TestUsers.claims(TestUsers.registerAndLogin(mvc).accessToken());
        org.assertj.core.api.Assertions.assertThat(customer).doesNotContain("dept");
    }

    @Test
    void theBoardOnlyHasManagersAndBadPositionsAreRejected() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.staffBody(TestUsers.newEmail(), TestUsers.PASSWORD, "ADMIN", "ANALYST"))).andExpect(status().isBadRequest());
        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + TestUsers.newEmail() + "\",\"password\":\"" + TestUsers.PASSWORD + "\",\"department\":\"MARKETING\",\"level\":\"ANALYST\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + TestUsers.newEmail() + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void eachDepartmentReachesOnlyItsOwnTools() throws Exception {
        TestUsers.Login customer = TestUsers.registerAndLogin(mvc);
        String account = JsonPath.read(mvc.perform(TestUsers.bearer(customer.accessToken(), post("/api/v1/accounts"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CHECKING\"}")).andReturn().getResponse().getContentAsString(), "$.id");

        String risk = TestUsers.staffToken(mvc, "RISK");
        String credit = TestUsers.staffToken(mvc, "CREDIT");
        String finance = TestUsers.staffToken(mvc, "FINANCE");
        String auditor = TestUsers.staffToken(mvc, "AUDITOR");

        // Risco bloqueia; Crédito, Financeiro e Auditor não
        mvc.perform(TestUsers.bearer(risk, post("/api/v1/admin/accounts/" + account + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Suspeita de fraude\"}")).andExpect(status().isOk());
        for (String other : new String[] {credit, finance, auditor}) {
            mvc.perform(TestUsers.bearer(other, post("/api/v1/admin/accounts/" + account + "/block")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Sem permissão\"}")).andExpect(status().isForbidden());
        }
        // limite de operação: Crédito sim; Risco, Financeiro e Auditor não
        String body = "{\"perOperation\":\"500.00\",\"daily\":\"1000.00\"}";
        mvc.perform(TestUsers.bearer(credit, put("/api/v1/admin/accounts/" + account + "/limits/TRANSFER")).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());
        for (String other : new String[] {risk, finance, auditor}) {
            mvc.perform(TestUsers.bearer(other, put("/api/v1/admin/accounts/" + account + "/limits/TRANSFER")).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isForbidden());
        }
        // câmbio: só o Financeiro
        mvc.perform(TestUsers.bearer(finance, get("/api/v1/admin/fx/rates"))).andExpect(status().isOk());
        for (String other : new String[] {risk, credit, auditor}) {
            mvc.perform(TestUsers.bearer(other, get("/api/v1/admin/fx/rates"))).andExpect(status().isForbidden());
        }
        // visão geral: Financeiro e Auditor; Risco e Crédito não
        mvc.perform(TestUsers.bearer(finance, get("/api/v1/admin/overview"))).andExpect(status().isOk());
        mvc.perform(TestUsers.bearer(auditor, get("/api/v1/admin/overview"))).andExpect(status().isOk());
        mvc.perform(TestUsers.bearer(risk, get("/api/v1/admin/overview"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(credit, get("/api/v1/admin/overview"))).andExpect(status().isForbidden());
        // auditoria: Risco e Auditor leem; Crédito e Financeiro não
        mvc.perform(TestUsers.bearer(risk, get("/api/v1/audit"))).andExpect(status().isOk());
        mvc.perform(TestUsers.bearer(auditor, get("/api/v1/audit"))).andExpect(status().isOk());
        mvc.perform(TestUsers.bearer(credit, get("/api/v1/audit"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(finance, get("/api/v1/audit"))).andExpect(status().isForbidden());
        // ninguém além do ADMIN gerencia a equipe
        for (String other : new String[] {risk, credit, finance, auditor}) {
            mvc.perform(TestUsers.bearer(other, get("/api/v1/admin/users"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void assigningSomeoneToAnotherPositionChangesTheRoleAndEndsTheirSessions() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        String email = TestUsers.newEmail();
        MvcResult created = mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.staffBody(email, TestUsers.PASSWORD, "SUPPORT", "ANALYST"))).andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        MvcResult login = TestUsers.login(mvc, email, TestUsers.PASSWORD);
        String oldToken = TestUsers.read(login, "$.accessToken");
        mvc.perform(TestUsers.bearer(oldToken, get("/api/v1/audit"))).andExpect(status().isOk());

        mvc.perform(TestUsers.bearer(admin, put("/api/v1/admin/users/" + id + "/assignment")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"department\":\"CREDITO\",\"level\":\"MANAGER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CREDIT")).andExpect(jsonPath("$.level").value("MANAGER"));

        // o token de antes não vale mais (a sessão caiu) e o novo traz as permissões do departamento novo
        mvc.perform(TestUsers.bearer(oldToken, get("/api/v1/audit"))).andExpect(status().isUnauthorized());
        String fresh = TestUsers.read(TestUsers.login(mvc, email, TestUsers.PASSWORD), "$.accessToken");
        mvc.perform(TestUsers.bearer(fresh, get("/api/v1/audit"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/audit?event=USER_ASSIGNED"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].detail", hasItem(org.hamcrest.Matchers.containsString("ATENDIMENTO/ANALYST -> CREDITO/MANAGER"))));

        // a Diretoria não aceita analista, e ninguém muda a própria posição
        mvc.perform(TestUsers.bearer(admin, put("/api/v1/admin/users/" + id + "/assignment")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"department\":\"DIRETORIA\",\"level\":\"ANALYST\"}")).andExpect(status().isBadRequest());
        String self = JsonPath.<java.util.List<String>>read(mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/users"))).andReturn().getResponse().getContentAsString(),
                "$[?(@.email == '" + TestUsers.ADMIN_EMAIL + "')].id").get(0);
        mvc.perform(TestUsers.bearer(admin, put("/api/v1/admin/users/" + self + "/assignment")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"department\":\"RISCO\",\"level\":\"MANAGER\"}")).andExpect(status().isConflict());
        // só o ADMIN muda posições
        String risk = TestUsers.staffToken(mvc, "RISK", "MANAGER");
        mvc.perform(TestUsers.bearer(risk, put("/api/v1/admin/users/" + id + "/assignment")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"department\":\"RISCO\",\"level\":\"ANALYST\"}")).andExpect(status().isForbidden());
        createUser(admin, "FINANCE", "ANALYST"); // e a lista continua coerente
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/users"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].department", hasItem("FINANCEIRO")));
    }
}
