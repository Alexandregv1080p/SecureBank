package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Investimentos (renda fixa simulada) pela API, com Postgres real: o dinheiro muda de lugar sem sumir nem duplicar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class InvestmentApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void listsTheAvailableProducts() throws Exception {
        String token = customer();

        mvc.perform(as(token, get("/api/v1/investments/products")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.code=='CDB_DAILY')].kind").value("DAILY"))
                .andExpect(jsonPath("$[?(@.code=='CDB_DAILY')].annualRatePercent").value("10.50"))
                .andExpect(jsonPath("$[?(@.code=='CDB_90')].termDays").value(90))
                .andExpect(jsonPath("$[?(@.code=='CDB_365')].minAmount").value("100.00"));
        mvc.perform(get("/api/v1/investments/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void applyingMovesMoneyOutOfTheAccountAndShowsOnTheStatement() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");

        mvc.perform(apply(token, account, "CDB_DAILY", "400.00", key()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.principal.amount").value("400.00"))
                .andExpect(jsonPath("$.productName").value("CDB Liquidez Diária"))
                .andExpect(jsonPath("$.annualRatePercent").value("10.50"))
                .andExpect(jsonPath("$.net.amount").value("400.00")) // ainda sem dia completo
                .andExpect(jsonPath("$.canRedeem").value(true));

        assertThat(balance(token, account)).isEqualTo("600.00");
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/statement?category=INVESTMENTS")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("INVEST_OUT"))
                .andExpect(jsonPath("$.items[0].direction").value("DEBIT"));
        mvc.perform(as(token, get("/api/v1/investments"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void refusesWhatCannotBeApplied() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "50.00");

        mvc.perform(apply(token, account, "CDB_DAILY", "50.01", key()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mvc.perform(apply(token, account, "CDB_90", "50.00", key())) // abaixo do mínimo de R$ 100
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVESTMENT_BELOW_MINIMUM"));
        mvc.perform(apply(token, account, "NAO_EXISTE", "10.00", key())).andExpect(status().isNotFound());
        mvc.perform(apply(token, account, "CDB_DAILY", "0", key())).andExpect(status().isBadRequest());
        mvc.perform(apply(token, account, "CDB_DAILY", "1.234", key())).andExpect(status().isBadRequest());
        mvc.perform(as(token, post("/api/v1/investments")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"productCode\":\"CDB_DAILY\",\"amount\":\"1.00\"}", account)))
                .andExpect(status().isBadRequest()); // sem Idempotency-Key

        assertThat(balance(token, account)).isEqualTo("50.00"); // nada saiu
    }

    @Test
    void cannotApplyFromAnotherCustomersAccount() throws Exception {
        String owner = customer();
        String account = openAccount(owner);
        deposit(owner, account, "100.00");

        mvc.perform(apply(customer(), account, "CDB_DAILY", "10.00", key())).andExpect(status().isNotFound());
        assertThat(balance(owner, account)).isEqualTo("100.00");
    }

    @Test
    void theSameKeyAppliesOnlyOnce() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String key = key();

        mvc.perform(apply(token, account, "CDB_DAILY", "30.00", key)).andExpect(status().isCreated()); // aplica
        mvc.perform(apply(token, account, "CDB_DAILY", "30.00", key))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(balance(token, account)).isEqualTo("70.00");
        mvc.perform(as(token, get("/api/v1/investments"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void redeemingADailyInvestmentReturnsTheMoneyAndOnlyOnce() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "500.00");
        String id = read(mvc.perform(apply(token, account, "CDB_DAILY", "500.00", key())).andReturn(), "$.id");
        assertThat(balance(token, account)).isEqualTo("0.00");

        mvc.perform(redeem(token, id, key()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.net.amount").value("500.00"))
                .andExpect(jsonPath("$.canRedeem").value(false));
        assertThat(balance(token, account)).isEqualTo("500.00");

        mvc.perform(redeem(token, id, key()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVESTMENT_ALREADY_REDEEMED"));
        assertThat(balance(token, account)).isEqualTo("500.00"); // não creditou de novo
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/statement?category=INVESTMENTS")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void aYieldingInvestmentPaysMoreThanWasApplied() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String id = read(mvc.perform(apply(token, account, "CDB_DAILY", "1000.00", key())).andReturn(), "$.id");
        jdbc.update("update investments set applied_at = applied_at - interval '365 days' where id = ?::uuid", id);

        MvcResult view = mvc.perform(as(token, get("/api/v1/investments/" + id)))
                .andExpect(jsonPath("$.daysHeld").value(365))
                .andExpect(jsonPath("$.taxRatePercent").value("17.50"))
                .andReturn();
        BigDecimal net = new BigDecimal((String) JsonPath.read(view.getResponse().getContentAsString(), "$.net.amount"));
        assertThat(net).isBetween(new BigDecimal("1086.60"), new BigDecimal("1086.65"));

        mvc.perform(redeem(token, id, key())).andExpect(status().isCreated());
        assertThat(new BigDecimal(balance(token, account))).isEqualByComparingTo(net); // caiu exatamente o que foi mostrado
    }

    @Test
    void aTermInvestmentCannotBeRedeemedBeforeMaturityButCanAfterwards() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String id = read(mvc.perform(apply(token, account, "CDB_90", "1000.00", key())).andReturn(), "$.id");

        mvc.perform(as(token, get("/api/v1/investments/" + id)))
                .andExpect(jsonPath("$.canRedeem").value(false))
                .andExpect(jsonPath("$.termDays").value(90));
        mvc.perform(redeem(token, id, key()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVESTMENT_NOT_MATURED"));
        assertThat(balance(token, account)).isEqualTo("0.00");

        jdbc.update("update investments set applied_at = applied_at - interval '91 days',"
                + " matures_at = matures_at - interval '91 days' where id = ?::uuid", id);
        mvc.perform(redeem(token, id, key()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.daysHeld").value(90)); // o rendimento parou no vencimento
        assertThat(new BigDecimal(balance(token, account))).isGreaterThan(new BigDecimal("1000.00"));
    }

    @Test
    void anotherCustomerCannotSeeOrRedeemMyInvestment() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String id = read(mvc.perform(apply(token, account, "CDB_DAILY", "100.00", key())).andReturn(), "$.id");
        String stranger = customer();

        mvc.perform(as(stranger, get("/api/v1/investments/" + id))).andExpect(status().isNotFound());
        mvc.perform(redeem(stranger, id, key())).andExpect(status().isNotFound());
        mvc.perform(as(stranger, get("/api/v1/investments"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(token, get("/api/v1/investments/" + id))).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void concurrentRedeemsPayExactlyOnce() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "300.00");
        String id = read(mvc.perform(apply(token, account, "CDB_DAILY", "300.00", key())).andReturn(), "$.id");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Integer> call = () -> mvc.perform(redeem(token, id, key())).andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) {
                created++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(balance(token, account)).isEqualTo("300.00"); // creditou uma vez só
    }

    @Test
    void aCustomerCanHaveAtMost50ActiveInvestments() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        jdbc.update("""
                insert into investments (id, customer_id, account_id, product_code, product_name, principal, currency,
                    annual_rate, term_days, applied_at, matures_at, status)
                select gen_random_uuid(), customer_id, id, 'CDB_DAILY', 'x', 1.00, 'BRL', 0.1050, null, now(), null, 'ACTIVE'
                from accounts a, generate_series(1, 50) where a.id = ?::uuid""", account);

        mvc.perform(apply(token, account, "CDB_DAILY", "1.00", key()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVESTMENT_LIMIT_REACHED"));
    }

    @Test
    void partialRedeemPaysTheAskedAmountAndKeepsTheRestApplied() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String id = read(mvc.perform(apply(token, account, "CDB_DAILY", "1000.00", key())).andReturn(), "$.id");

        mvc.perform(redeemAmount(token, id, "400.00", key()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.paidAmount.amount").value("400.00"))
                .andExpect(jsonPath("$.principal.amount").value("600.00")); // dia 0: líquido = principal
        assertThat(balance(token, account)).isEqualTo("400.00");
        mvc.perform(as(token, get("/api/v1/investments/" + id)))
                .andExpect(jsonPath("$.net.amount").value("600.00"))
                .andExpect(jsonPath("$.paidAmount").doesNotExist());

        mvc.perform(redeemAmount(token, id, "9999.00", key())) // mais que o líquido: resgata o que resta
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.paidAmount.amount").value("600.00"));
        assertThat(balance(token, account)).isEqualTo("1000.00");
    }

    @Test
    void partialRedeemValidatesTheAmountAndTheMaturity() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "500.00");
        String term = read(mvc.perform(apply(token, account, "CDB_90", "300.00", key())).andReturn(), "$.id");
        String daily = read(mvc.perform(apply(token, account, "CDB_DAILY", "200.00", key())).andReturn(), "$.id");

        mvc.perform(redeemAmount(token, term, "100.00", key()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVESTMENT_NOT_MATURED"));
        mvc.perform(redeemAmount(token, daily, "0", key())).andExpect(status().isBadRequest());
        mvc.perform(redeemAmount(token, daily, "-5", key())).andExpect(status().isBadRequest());
        mvc.perform(redeemAmount(token, daily, "1.234", key())).andExpect(status().isBadRequest());
        assertThat(balance(token, account)).isEqualTo("0.00");
    }

    @Test
    void partialRedeemsRunInParallelWithoutPayingMoreThanTheTotal() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String id = read(mvc.perform(apply(token, account, "CDB_DAILY", "1000.00", key())).andReturn(), "$.id");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Callable<Integer> call = () -> mvc.perform(redeemAmount(token, id, "300.00", key())).andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) {
                created++;
            }
        }
        pool.shutdown();

        // 1000 dá para no máximo 3 resgates de 300 inteiros + o que resta (4º pode pagar 100); nunca mais que o total
        assertThat(new BigDecimal(balance(token, account))).isLessThanOrEqualTo(new BigDecimal("1000.00"));
        assertThat(created).isBetween(3, 4);
    }

    // ---------- aviso de vencimento ----------

    @Autowired com.securebank.investment.application.InvestmentApplicationService investmentService;

    @Test
    void aMaturedTermInvestmentIsAnnouncedExactlyOnce() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "800.00");
        String term = read(mvc.perform(apply(token, account, "CDB_90", "300.00", key())).andReturn(), "$.id");
        String notYet = read(mvc.perform(apply(token, account, "CDB_365", "300.00", key())).andReturn(), "$.id");
        String daily = read(mvc.perform(apply(token, account, "CDB_DAILY", "100.00", key())).andReturn(), "$.id");
        jdbc.update("update investments set applied_at = applied_at - interval '91 days',"
                + " matures_at = matures_at - interval '91 days' where id = ?::uuid", term);

        assertThat(investmentService.notifyMatured()).isEqualTo(1);
        assertThat(investmentService.notifyMatured()).isZero(); // não repete

        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'InvestmentMatured'"
                + " and aggregate_id = ?", Integer.class, term)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'InvestmentMatured'"
                + " and aggregate_id in (?, ?)", Integer.class, notYet, daily)).isZero();
    }

    @Test
    void aRedeemedOrFutureInvestmentIsNeverAnnounced() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "300.00");
        String term = read(mvc.perform(apply(token, account, "CDB_90", "300.00", key())).andReturn(), "$.id");
        jdbc.update("update investments set applied_at = applied_at - interval '91 days',"
                + " matures_at = matures_at - interval '91 days' where id = ?::uuid", term);
        mvc.perform(redeem(token, term, key())).andExpect(status().isCreated()); // resgatou antes do job rodar

        assertThat(investmentService.notifyMatured()).isZero();
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder redeemAmount(String token, String id, String amount, String key) {
        return redeem(token, id, key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}");
    }


    private MockHttpServletRequestBuilder apply(String token, String account, String product, String amount, String key) {
        return as(token, post("/api/v1/investments")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\",\"productCode\":\"%s\",\"amount\":\"%s\"}", account, product, amount));
    }

    private MockHttpServletRequestBuilder redeem(String token, String id, String key) {
        return as(token, post("/api/v1/investments/" + id + "/redeem")).header("Idempotency-Key", key);
    }

    private String customer() throws Exception {
        return TestUsers.registerAndLogin(mvc).accessToken();
    }

    private String openAccount(String token) throws Exception {
        return read(mvc.perform(as(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private void deposit(String token, String account, String amount) throws Exception {
        mvc.perform(as(token, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isCreated());
    }

    private String balance(String token, String account) throws Exception {
        return read(mvc.perform(as(token, get("/api/v1/accounts/" + account + "/balance"))).andReturn(), "$.balance.amount");
    }

    private static String key() {
        return "idem-" + UUID.randomUUID();
    }

    private static MockHttpServletRequestBuilder as(String accessToken, MockHttpServletRequestBuilder request) {
        return TestUsers.bearer(accessToken, request);
    }

    private static String json(String template, Object... args) {
        return String.format(template, args);
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
