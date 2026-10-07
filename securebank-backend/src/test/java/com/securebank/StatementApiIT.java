package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Gerenciamento do histórico pela API: filtros por categoria/sentido, resumo do mês e exportação CSV. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class StatementApiIT {

    @Autowired MockMvc mvc;

    private record Scenario(String token, String account) {}

    /** Movimento: depósito 500 (CASH+), saque 50 (CASH−), guardar 100 (SAVINGS−), resgatar 20 (SAVINGS+). */
    private Scenario scenario() throws Exception {
        String token = TestUsers.registerAndLogin(mvc).accessToken();
        String account = read(mvc.perform(as(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andReturn(), "$.id");
        money(token, "/accounts/" + account + "/deposits", "500.00");
        money(token, "/accounts/" + account + "/withdrawals", "50.00");
        String piggy = read(mvc.perform(as(token, post("/api/v1/piggies")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + account + "\",\"name\":\"Viagem\"}")).andReturn(), "$.id");
        money(token, "/piggies/" + piggy + "/deposits", "100.00");
        money(token, "/piggies/" + piggy + "/withdrawals", "20.00");
        return new Scenario(token, account);
    }

    @Test
    void filtersTheStatementByCategoryAndByDirection() throws Exception {
        Scenario s = scenario();
        String url = "/api/v1/accounts/" + s.account() + "/statement";

        mvc.perform(as(s.token(), get(url))).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(as(s.token(), get(url + "?category=CASH")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].type").value(containsInAnyOrder("DEPOSIT", "WITHDRAW")));
        mvc.perform(as(s.token(), get(url + "?category=SAVINGS"))).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(s.token(), get(url + "?category=PIX"))).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(as(s.token(), get(url + "?direction=DEBIT"))).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(s.token(), get(url + "?category=SAVINGS&direction=CREDIT")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].amount.amount").value("20.00"));
    }

    @Test
    void anUnknownCategoryOrDirectionIsABadRequest() throws Exception {
        Scenario s = scenario();
        String url = "/api/v1/accounts/" + s.account() + "/statement";

        mvc.perform(as(s.token(), get(url + "?category=FOO"))).andExpect(status().isBadRequest());
        mvc.perform(as(s.token(), get(url + "?direction=SIDEWAYS"))).andExpect(status().isBadRequest());
    }

    @Test
    void theMonthlySummaryTotalsIncomeAndExpensesPerCategory() throws Exception {
        Scenario s = scenario();
        String month = YearMonth.now(ZoneId.of("America/Sao_Paulo")).toString();

        mvc.perform(as(s.token(), get("/api/v1/accounts/" + s.account() + "/statement/summary?month=" + month)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(month))
                .andExpect(jsonPath("$.income.amount").value("520.00"))
                .andExpect(jsonPath("$.expenses.amount").value("150.00"))
                .andExpect(jsonPath("$.net.amount").value("370.00"))
                .andExpect(jsonPath("$.byCategory[?(@.category=='CASH')].income.amount").value("500.00"))
                .andExpect(jsonPath("$.byCategory[?(@.category=='CASH')].expenses.amount").value("50.00"))
                .andExpect(jsonPath("$.byCategory[?(@.category=='SAVINGS')].income.amount").value("20.00"))
                .andExpect(jsonPath("$.byCategory[?(@.category=='SAVINGS')].expenses.amount").value("100.00"))
                .andExpect(jsonPath("$.byCategory[?(@.category=='PIX')].income.amount").value("0.00"))
                .andExpect(jsonPath("$.byCategory", hasSize(7)));
    }

    @Test
    void aMonthWithoutMovementSumsToZero() throws Exception {
        Scenario s = scenario();

        mvc.perform(as(s.token(), get("/api/v1/accounts/" + s.account() + "/statement/summary?month=2020-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income.amount").value("0.00"))
                .andExpect(jsonPath("$.net.amount").value("0.00"));
    }

    @Test
    void theSummaryRejectsABadMonthAndAStrangersAccount() throws Exception {
        Scenario s = scenario();
        Scenario other = scenario();

        mvc.perform(as(s.token(), get("/api/v1/accounts/" + s.account() + "/statement/summary?month=outubro")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(s.token(), get("/api/v1/accounts/" + s.account() + "/statement/summary")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(other.token(), get("/api/v1/accounts/" + s.account() + "/statement/summary?month=2026-10")))
                .andExpect(status().isNotFound());
        mvc.perform(as(other.token(), get("/api/v1/accounts/" + s.account() + "/statement/export")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/" + s.account() + "/statement/summary?month=2026-10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exportsTheStatementAsCsvWithTheSameFilters() throws Exception {
        Scenario s = scenario();
        String url = "/api/v1/accounts/" + s.account() + "/statement/export";

        MvcResult all = mvc.perform(as(s.token(), get(url)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("extrato.csv")))
                .andExpect(header().string("X-Truncated", "false"))
                .andReturn();
        String[] lines = all.getResponse().getContentAsString().split("\r\n");
        assertThat(lines).hasSize(5); // cabeçalho + 4 lançamentos
        assertThat(lines[0]).isEqualTo("date,type,category,direction,amount,currency,balance_after,status,reference");
        assertThat(lines[1]).contains("PIGGY_OUT,SAVINGS,CREDIT,20.00,BRL"); // mais recente primeiro

        String savings = mvc.perform(as(s.token(), get(url + "?category=SAVINGS"))).andReturn().getResponse()
                .getContentAsString();
        assertThat(savings.split("\r\n")).hasSize(3);
        mvc.perform(as(s.token(), get(url + "?from=2999-01-01&to=2998-01-01"))).andExpect(status().isBadRequest());
    }

    private void money(String token, String path, String amount) throws Exception {
        mvc.perform(as(token, post("/api/v1" + path)).header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isCreated());
    }

    private static MockHttpServletRequestBuilder as(String accessToken, MockHttpServletRequestBuilder request) {
        return TestUsers.bearer(accessToken, request);
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
