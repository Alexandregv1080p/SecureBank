package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** A equipe (ADMIN) ajusta a cotação e o spread; o cliente passa a ver e pagar o novo preço, e tudo fica na auditoria. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class FxAdminApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** As cotações são compartilhadas entre os testes: sempre volta ao padrão. */
    @AfterEach
    void restoreRates() {
        jdbc.update("update fx_rates set mid_rate = 5.200000, spread = 0.0150 where currency = 'USD'");
        jdbc.update("update fx_rates set mid_rate = 5.650000, spread = 0.0150 where currency = 'EUR'");
    }

    private MockHttpServletRequestBuilder change(String token, String currency, String mid, String spreadPercent) {
        return TestUsers.bearer(token, put("/api/v1/admin/fx/rates/" + currency)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mid\":\"" + mid + "\",\"spreadPercent\":\"" + spreadPercent + "\"}");
    }

    @Test
    void anAdminChangesTheRateAndCustomersSeeAndPayTheNewPrice() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        String customer = TestUsers.registerAndLogin(mvc).accessToken();

        mvc.perform(change(admin, "USD", "5.500000", "2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.buyRate").value("5.610000")) // 5.50 + 2%
                .andExpect(jsonPath("$.sellRate").value("5.390000")) // 5.50 − 2%
                .andExpect(jsonPath("$.spreadPercent").value("2"));

        mvc.perform(TestUsers.bearer(customer, get("/api/v1/fx/rates")))
                .andExpect(jsonPath("$[?(@.currency=='USD')].buyRate").value("5.610000"))
                .andExpect(jsonPath("$[?(@.currency=='EUR')].buyRate").value("5.734750")); // outra moeda intacta
    }

    @Test
    void aTradeWithTheOldQuoteIsRefusedAfterTheChange() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        var login = TestUsers.registerAndLogin(mvc);
        String account = com.jayway.jsonpath.JsonPath.read(mvc.perform(TestUsers.bearer(login.accessToken(),
                post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CHECKING\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(TestUsers.bearer(login.accessToken(), post("/api/v1/accounts/" + account + "/deposits"))
                .header("Idempotency-Key", "idem-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"1000.00\"}")).andExpect(status().isCreated());
        mvc.perform(change(admin, "USD", "5.300000", "1.5")).andExpect(status().isOk());

        mvc.perform(TestUsers.bearer(login.accessToken(), post("/api/v1/fx/buy"))
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + account + "\",\"currency\":\"USD\",\"amount\":\"10.00\","
                                + "\"quotedRate\":\"5.278000\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FX_RATE_CHANGED"));
    }

    @Test
    void theChangeIsAudited() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        int before = jdbc.queryForObject("select count(*) from audit_logs where event = 'FX_RATE_CHANGED'", Integer.class);

        mvc.perform(change(admin, "EUR", "5.800000", "1.0")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from audit_logs where event = 'FX_RATE_CHANGED'", Integer.class))
                .isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("select detail from audit_logs where event = 'FX_RATE_CHANGED'"
                + " order by occurred_at desc limit 1", String.class)).contains("EUR").contains("5.800000");
    }

    @Test
    void aBigJumpOrAHugeSpreadIsRefusedAsLikelyTypos() throws Exception {
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(change(admin, "USD", "6.300000", "1.5")) // +21%
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FX_RATE_CHANGE_TOO_LARGE"));
        mvc.perform(change(admin, "USD", "52.000000", "1.5")) // vírgula no lugar errado
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(change(admin, "USD", "5.300000", "10.5"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FX_SPREAD_TOO_LARGE"));
        mvc.perform(change(admin, "USD", "6.240000", "1.5")).andExpect(status().isOk()); // +20% exato passa

        assertThat(jdbc.queryForObject("select mid_rate from fx_rates where currency = 'USD'", java.math.BigDecimal.class))
                .isEqualByComparingTo("6.24");
    }

    @Test
    void invalidInputAndUnknownCurrenciesAreRejected() throws Exception {
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(change(admin, "USD", "0", "1.5")).andExpect(status().isBadRequest());
        mvc.perform(change(admin, "USD", "-5", "1.5")).andExpect(status().isBadRequest());
        mvc.perform(change(admin, "USD", "5.3", "-1")).andExpect(status().isBadRequest());
        mvc.perform(change(admin, "JPY", "0.034", "1.5")).andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdminMayChangeRates() throws Exception {
        String customer = TestUsers.registerAndLogin(mvc).accessToken();
        String support = TestUsers.staffToken(mvc, "SUPPORT");

        mvc.perform(change(customer, "USD", "5.300000", "1.5")).andExpect(status().isForbidden());
        mvc.perform(change(support, "USD", "5.300000", "1.5")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/fx/rates/USD").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mid\":\"5.3\",\"spreadPercent\":\"1.5\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select mid_rate from fx_rates where currency = 'USD'", java.math.BigDecimal.class))
                .isEqualByComparingTo("5.20");
    }
}
