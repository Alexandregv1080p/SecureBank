package com.securebank;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Visão gerencial: só o ADMIN vê, só agregados, e os números acompanham o que acontece no banco. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AdminOverviewIT {

    @Autowired MockMvc mvc;

    @Test
    void onlyAdminsSeeTheOverview() throws Exception {
        String support = TestUsers.staffToken(mvc, "SUPPORT");
        mvc.perform(TestUsers.bearer(support, get("/api/v1/admin/overview"))).andExpect(status().isForbidden());
        String customer = TestUsers.registerAndLogin(mvc).accessToken();
        mvc.perform(TestUsers.bearer(customer, get("/api/v1/admin/overview"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSeriesCoversEveryDayAndTheNumbersFollowTheBank() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = JsonPath.read(mvc.perform(TestUsers.bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(TestUsers.bearer(token, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", "ov-" + account)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"100.00\"}")).andExpect(status().isCreated());
        mvc.perform(TestUsers.bearer(token, post("/api/v1/accounts/" + account + "/withdrawals")).header("Idempotency-Key", "ow-" + account)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"30.00\"}")).andExpect(status().isCreated());

        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/overview?days=14"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(14))
                .andExpect(jsonPath("$.activity.days", hasSize(14)))
                .andExpect(jsonPath("$.customers.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.accounts.active", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activity.totalCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activity.byType[?(@.type == 'WITHDRAW')]").isNotEmpty())
                .andExpect(jsonPath("$.credit.cards").isNumber())
                .andExpect(jsonPath("$.security.outboxPending").isNumber())
                .andExpect(jsonPath("$..document").doesNotExist()) // nenhum dado pessoal
                .andExpect(jsonPath("$..email").doesNotExist());
    }

    @Test
    void theAuditTrailCanBeFilteredByPeriod() throws Exception {
        String admin = TestUsers.adminToken(mvc); // o login do admin acabou de gerar eventos
        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/audit?from=" + today + "&to=" + today))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/audit?from=2000-01-01&to=2000-01-31"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/audit?from=ontem"))).andExpect(status().isBadRequest());
    }

    @Test
    void thePeriodIsBounded() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/overview?days=6"))).andExpect(status().isBadRequest());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/overview?days=91"))).andExpect(status().isBadRequest());
    }
}
