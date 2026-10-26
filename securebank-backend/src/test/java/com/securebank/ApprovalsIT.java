package com.securebank;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.ResultActions;

/** Alçadas: o analista pede, o gerente do departamento (ou a Diretoria) decide, e ninguém decide o próprio pedido. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ApprovalsIT {

    @Autowired MockMvc mvc;

    private record Setup(String customerToken, String account, String card) {}

    private Setup customerWithCreditCard() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = JsonPath.read(mvc.perform(TestUsers.bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        String card = JsonPath.read(mvc.perform(TestUsers.bearer(token, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + account + "\",\"dueDay\":10}")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.id");
        return new Setup(token, account, card);
    }

    private ResultActions limit(String token, String card, String value, String note) throws Exception {
        String body = note == null ? "{\"limit\":\"" + value + "\"}" : "{\"limit\":\"" + value + "\",\"note\":\"" + note + "\"}";
        return mvc.perform(TestUsers.bearer(token, put("/api/v1/admin/credit/cards/" + card + "/limit")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String creditLimitOf(Setup s) throws Exception {
        return JsonPath.read(mvc.perform(TestUsers.bearer(s.customerToken(), get("/api/v1/credit/cards/" + s.card()))).andReturn()
                .getResponse().getContentAsString(), "$.creditLimit.amount");
    }

    private String idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void anAnalystDecidesSmallLimitsAloneButAskForBigOnesAndAManagerApproves() throws Exception {
        Setup s = customerWithCreditCard();
        String analyst = TestUsers.staffToken(mvc, "CREDIT", "ANALYST");
        String manager = TestUsers.staffToken(mvc, "CREDIT", "MANAGER");

        // até R$ 20.000,00 o analista resolve na hora
        limit(analyst, s.card(), "15000.00", null).andExpect(status().isOk()).andExpect(jsonPath("$.creditLimit.amount").value("15000.00"));
        org.assertj.core.api.Assertions.assertThat(creditLimitOf(s)).isEqualTo("15000.00");

        // acima disso vira pedido: nada muda para o cliente ainda
        MvcResult asked = limit(analyst, s.card(), "30000.00", "Cliente com renda comprovada").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.kind").value("CREDIT_LIMIT"))
                .andExpect(jsonPath("$.amount").value("30000.00")).andExpect(jsonPath("$.department").value("CREDITO"))
                .andExpect(jsonPath("$.canCancel").value(true)).andExpect(jsonPath("$.canDecide").value(false)).andReturn();
        String id = idOf(asked);
        org.assertj.core.api.Assertions.assertThat(creditLimitOf(s)).isEqualTo("15000.00");

        // um segundo pedido para o mesmo cartão espera o primeiro
        limit(analyst, s.card(), "40000.00", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_ALREADY_PENDING"));
        // o analista não decide o próprio pedido (nem com a posição de outro): só gerente do departamento
        mvc.perform(TestUsers.bearer(analyst, post("/api/v1/admin/approvals/" + id + "/approve"))).andExpect(status().isForbidden());

        // o gerente vê o pedido na fila, com quem pediu e o que será feito
        mvc.perform(TestUsers.bearer(manager, get("/api/v1/admin/approvals?status=PENDING"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].canDecide", hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].currentLimit", hasItem("15000.00")))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].subject", hasItem(containsString("Cartão final"))));
        mvc.perform(TestUsers.bearer(manager, post("/api/v1/admin/approvals/" + id + "/approve")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Renda confere\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionNote").value("Renda confere"));
        org.assertj.core.api.Assertions.assertThat(creditLimitOf(s)).isEqualTo("30000.00");

        // decidido uma vez, não decide de novo
        mvc.perform(TestUsers.bearer(manager, post("/api/v1/admin/approvals/" + id + "/approve"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_PENDING"));
        // o analista vê a decisão no que pediu
        mvc.perform(TestUsers.bearer(analyst, get("/api/v1/admin/approvals?status=APPROVED"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].status", hasItem("APPROVED")));
    }

    @Test
    void reducingTheLimitIsAlwaysTheAnalystsCallAndManagersAndTheBoardActDirectly() throws Exception {
        Setup s = customerWithCreditCard();
        String manager = TestUsers.staffToken(mvc, "CREDIT", "MANAGER");
        String analyst = TestUsers.staffToken(mvc, "CREDIT", "ANALYST");
        String admin = TestUsers.adminToken(mvc);

        limit(manager, s.card(), "50000.00", null).andExpect(status().isOk()); // gerente: direto, acima da alçada do analista
        limit(analyst, s.card(), "40000.00", null).andExpect(status().isOk()); // reduzir o analista faz, mesmo acima de R$ 20.000,00
        limit(analyst, s.card(), "45000.00", null).andExpect(status().isAccepted()); // aumentar acima da alçada, não
        limit(admin, s.card(), "100000.00", null).andExpect(status().isOk()); // Diretoria: direto, e o pedido pendente fica lá
        // limite inválido não vira pedido
        limit(analyst, s.card(), "100100.00", null).andExpect(status().isBadRequest());
        limit(analyst, s.card(), "30050.00", null).andExpect(status().isBadRequest());
    }

    @Test
    void aManagerOfAnotherDepartmentCannotDecideAndRejectionNeedsAReason() throws Exception {
        Setup s = customerWithCreditCard();
        String analyst = TestUsers.staffToken(mvc, "CREDIT", "ANALYST");
        String riskManager = TestUsers.staffToken(mvc, "RISK", "MANAGER");
        String creditManager = TestUsers.staffToken(mvc, "CREDIT", "MANAGER");
        String id = idOf(limit(analyst, s.card(), "25000.00", null).andExpect(status().isAccepted()).andReturn());

        mvc.perform(TestUsers.bearer(riskManager, post("/api/v1/admin/approvals/" + id + "/approve"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(riskManager, get("/api/v1/admin/approvals"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty()); // nem enxerga

        mvc.perform(TestUsers.bearer(creditManager, post("/api/v1/admin/approvals/" + id + "/reject")).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(TestUsers.bearer(creditManager, post("/api/v1/admin/approvals/" + id + "/reject")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Sem renda comprovada\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Sem renda comprovada"));
        org.assertj.core.api.Assertions.assertThat(creditLimitOf(s)).isEqualTo("2000.00"); // nada mudou
        // recusado libera um novo pedido
        limit(analyst, s.card(), "25000.00", null).andExpect(status().isAccepted());
    }

    @Test
    void theRequesterCanWithdrawAndOnlyTheRequesterSeesAndCancelsIt() throws Exception {
        Setup s = customerWithCreditCard();
        String analyst = TestUsers.staffToken(mvc, "CREDIT", "ANALYST");
        String otherAnalyst = TestUsers.staffToken(mvc, "CREDIT", "ANALYST");
        String id = idOf(limit(analyst, s.card(), "25000.00", null).andExpect(status().isAccepted()).andReturn());

        mvc.perform(TestUsers.bearer(otherAnalyst, get("/api/v1/admin/approvals"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty()); // analista só vê os próprios
        mvc.perform(TestUsers.bearer(otherAnalyst, post("/api/v1/admin/approvals/" + id + "/cancel"))).andExpect(status().isNotFound());
        mvc.perform(TestUsers.bearer(analyst, post("/api/v1/admin/approvals/" + id + "/cancel"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        mvc.perform(TestUsers.bearer(analyst, post("/api/v1/admin/approvals/" + id + "/cancel"))).andExpect(status().isConflict());
    }

    @Test
    void whoAskedNeverDecidesEvenAfterBeingPromotedToManager() throws Exception {
        Setup s = customerWithCreditCard();
        String admin = TestUsers.adminToken(mvc);
        String email = TestUsers.newEmail();
        String userId = JsonPath.read(mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.staffBody(email, TestUsers.PASSWORD, "CREDIT", "ANALYST"))).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.id");
        String analyst = TestUsers.read(TestUsers.login(mvc, email, TestUsers.PASSWORD), "$.accessToken");
        String id = idOf(limit(analyst, s.card(), "25000.00", null).andExpect(status().isAccepted()).andReturn());

        mvc.perform(TestUsers.bearer(admin, put("/api/v1/admin/users/" + userId + "/assignment")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"department\":\"CREDITO\",\"level\":\"MANAGER\"}")).andExpect(status().isOk());
        String promoted = TestUsers.read(TestUsers.login(mvc, email, TestUsers.PASSWORD), "$.accessToken");
        mvc.perform(TestUsers.bearer(promoted, post("/api/v1/admin/approvals/" + id + "/approve"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DECIDE_OWN_REQUEST"));
        mvc.perform(TestUsers.bearer(promoted, get("/api/v1/admin/approvals?status=PENDING"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].canDecide", hasItem(false)))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].canCancel", hasItem(true)));
        // outro gerente (ou a Diretoria) decide
        mvc.perform(TestUsers.bearer(admin, post("/api/v1/admin/approvals/" + id + "/approve"))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(creditLimitOf(s)).isEqualTo("25000.00");
    }

    @Test
    void anAnalystOfRiskBlocksAtOnceButNeedsAManagerToUnblock() throws Exception {
        Setup s = customerWithCreditCard();
        String analyst = TestUsers.staffToken(mvc, "RISK", "ANALYST");
        String manager = TestUsers.staffToken(mvc, "RISK", "MANAGER");

        mvc.perform(TestUsers.bearer(analyst, post("/api/v1/admin/accounts/" + s.account() + "/unblock"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_BLOCKED")); // nem abre pedido para conta que não está bloqueada
        mvc.perform(TestUsers.bearer(analyst, post("/api/v1/admin/accounts/" + s.account() + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Suspeita de fraude\"}")).andExpect(status().isOk());

        String id = idOf(mvc.perform(TestUsers.bearer(analyst, post("/api/v1/admin/accounts/" + s.account() + "/unblock"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Cliente confirmou as compras\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.kind").value("ACCOUNT_UNBLOCK"))
                .andExpect(jsonPath("$.note").value("Cliente confirmou as compras")).andReturn());
        mvc.perform(TestUsers.bearer(manager, get("/api/v1/admin/approvals/pending-count"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(TestUsers.bearer(analyst, get("/api/v1/admin/approvals/pending-count"))).andExpect(jsonPath("$.pending").value(0));

        mvc.perform(TestUsers.bearer(manager, post("/api/v1/admin/approvals/" + id + "/approve"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        // a conta voltou: o cliente consegue operar de novo
        mvc.perform(TestUsers.bearer(s.customerToken(), get("/api/v1/accounts/" + s.account()))).andExpect(jsonPath("$.status").value("ACTIVE"));
        // o gerente de Risco desbloqueia direto
        mvc.perform(TestUsers.bearer(manager, post("/api/v1/admin/accounts/" + s.account() + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Nova suspeita\"}")).andExpect(status().isOk());
        mvc.perform(TestUsers.bearer(manager, post("/api/v1/admin/accounts/" + s.account() + "/unblock"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void everyStepIsAudited() throws Exception {
        Setup s = customerWithCreditCard();
        String analyst = TestUsers.staffToken(mvc, "CREDIT", "ANALYST");
        String manager = TestUsers.staffToken(mvc, "CREDIT", "MANAGER");
        String admin = TestUsers.adminToken(mvc);
        String id = idOf(limit(analyst, s.card(), "25000.00", null).andExpect(status().isAccepted()).andReturn());
        mvc.perform(TestUsers.bearer(manager, post("/api/v1/admin/approvals/" + id + "/approve"))).andExpect(status().isOk());

        for (String event : new String[] {"APPROVAL_REQUESTED", "APPROVAL_APPROVED"}) {
            mvc.perform(TestUsers.bearer(admin, get("/api/v1/audit?event=" + event))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].detail", hasItem(containsString(id))));
        }
        // e quem não tem a alçada de decidir não consegue nem listar
        String support = TestUsers.staffToken(mvc, "SUPPORT");
        mvc.perform(TestUsers.bearer(support, get("/api/v1/admin/approvals"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(TestUsers.registerAndLogin(mvc).accessToken(), get("/api/v1/admin/approvals"))).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/approvals?status=ENCERRADO"))).andExpect(status().isBadRequest());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/approvals"))).andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.not(hasSize(0))));
    }
}
