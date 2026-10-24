package com.securebank;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** A equipe vê os cartões de um cliente e bloqueia/desbloqueia com motivo; o cliente é avisado e a auditoria marca a equipe. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class StaffCardsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private record Setup(String token, String customerId, String card) {}

    private Setup customerWithCard() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        String token = login.accessToken();
        String account = read(mvc.perform(TestUsers.bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn(), "$.id");
        String customerId = login.customerId();
        String card = read(mvc.perform(TestUsers.bearer(token, post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + account + "\"}")).andExpect(status().isCreated()).andReturn(), "$.id");
        return new Setup(token, customerId, card);
    }

    @Test
    void adminBlocksAndUnblocksACustomersCardWithAReason() throws Exception {
        Setup s = customerWithCard();
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/customers/" + s.customerId() + "/cards")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").doesNotExist()).andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/cards/" + s.card() + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Suspeita de fraude\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED")).andExpect(jsonPath("$.statusReason").value("Suspeita de fraude"));

        // o cliente enxerga o bloqueio e o motivo, e a compra é recusada
        mvc.perform(TestUsers.bearer(s.token(), get("/api/v1/cards/" + s.card())))
                .andExpect(jsonPath("$.status").value("BLOCKED")).andExpect(jsonPath("$.statusReason").value("Suspeita de fraude"));
        mvc.perform(TestUsers.bearer(s.token(), post("/api/v1/cards/" + s.card() + "/purchases")).header("Idempotency-Key", "sc-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"10.00\",\"merchant\":\"Loja\"}"))
                .andExpect(status().isUnprocessableEntity());

        Integer audited = jdbc.queryForObject("select count(*) from audit_logs where event = 'CARD_BLOCKED' and detail like ?", Integer.class,
                s.card() + "%| staff");
        org.assertj.core.api.Assertions.assertThat(audited).isEqualTo(1);

        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/cards/" + s.card() + "/unblock"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void theReasonIsRequired() throws Exception {
        Setup s = customerWithCard();
        String admin = TestUsers.adminToken(mvc);
        for (String body : new String[] {"{}", "{\"reason\":\"\"}", "{\"reason\":\"ab\"}"}) {
            mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/cards/" + s.card() + "/block")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void supportCanSeeButNotBlockAndCustomersCannotUseTheRoutes() throws Exception {
        Setup s = customerWithCard();
        String support = TestUsers.staffToken(mvc, "SUPPORT");
        mvc.perform(TestUsers.bearer(support, get("/api/v1/admin/customers/" + s.customerId() + "/cards"))).andExpect(status().isOk());
        mvc.perform(TestUsers.bearer(support, post("/api/v1/admin/cards/" + s.card() + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Teste de acesso\"}")).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(s.token(), post("/api/v1/admin/cards/" + s.card() + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Eu mesmo\"}")).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(s.token(), get("/api/v1/admin/customers/" + s.customerId() + "/cards"))).andExpect(status().isForbidden());
    }

    @Test
    void anUnknownCardIs404() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/cards/" + UUID.randomUUID() + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Qualquer motivo\"}")).andExpect(status().isNotFound());
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
