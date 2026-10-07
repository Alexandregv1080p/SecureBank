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

/** Câmbio simulado pela API, com Postgres real: reais e moeda trocam de lugar sem sumir nem duplicar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class FxApiIT {

    private static final String ASK = "5.278000"; // USD: 5.20 + 1,5%
    private static final String BID = "5.122000"; // USD: 5.20 − 1,5%

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void listsTheRatesWithTheCustomerPrices() throws Exception {
        String token = customer();

        mvc.perform(as(token, get("/api/v1/fx/rates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.currency=='USD')].buyRate").value(ASK))
                .andExpect(jsonPath("$[?(@.currency=='USD')].sellRate").value(BID))
                .andExpect(jsonPath("$[?(@.currency=='USD')].spreadPercent").value("1.5"))
                .andExpect(jsonPath("$[?(@.currency=='EUR')].mid").value("5.650000"));
        mvc.perform(get("/api/v1/fx/rates")).andExpect(status().isUnauthorized());
    }

    @Test
    void walletsStartEmptyForEverySupportedCurrency() throws Exception {
        String token = customer();

        mvc.perform(as(token, get("/api/v1/fx/wallets")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.currency=='USD')].balance.amount").value("0.00"))
                .andExpect(jsonPath("$[?(@.currency=='EUR')].balance.currency").value("EUR"));
    }

    @Test
    void buyingDebitsTheExactCostAndCreditsTheWalletAndShowsOnTheStatement() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");

        mvc.perform(trade(token, "buy", account, "USD", "100.00", ASK, key()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.foreignAmount.amount").value("100.00"))
                .andExpect(jsonPath("$.foreignAmount.currency").value("USD"))
                .andExpect(jsonPath("$.brlAmount.amount").value("527.80"))
                .andExpect(jsonPath("$.rate").value(ASK));

        assertThat(balance(token, account)).isEqualTo("472.20");
        mvc.perform(as(token, get("/api/v1/fx/wallets")))
                .andExpect(jsonPath("$[?(@.currency=='USD')].balance.amount").value("100.00"));
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/statement?category=FX")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("FX_BUY"))
                .andExpect(jsonPath("$.items[0].amount.amount").value("527.80"));
        mvc.perform(as(token, get("/api/v1/fx/operations")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].side").value("BUY"));
    }

    @Test
    void sellingCreditsTheBidRoundedDownAndDebitsTheWallet() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        mvc.perform(trade(token, "buy", account, "USD", "100.00", ASK, key())).andExpect(status().isCreated());

        mvc.perform(trade(token, "sell", account, "USD", "40.00", BID, key()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.brlAmount.amount").value("204.88")); // 40 × 5.122

        assertThat(balance(token, account)).isEqualTo("677.08"); // 1000 − 527.80 + 204.88
        mvc.perform(as(token, get("/api/v1/fx/wallets")))
                .andExpect(jsonPath("$[?(@.currency=='USD')].balance.amount").value("60.00"));
    }

    @Test
    void aRoundTripCostsTheSpread() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");

        mvc.perform(trade(token, "buy", account, "EUR", "100.00", "5.734750", key())).andExpect(status().isCreated());
        mvc.perform(trade(token, "sell", account, "EUR", "100.00", "5.565250", key())).andExpect(status().isCreated());

        assertThat(new BigDecimal(balance(token, account))).isLessThan(new BigDecimal("1000.00"));
        assertThat(balance(token, account)).isEqualTo("983.04"); // 1000 − 573.48 + 556.52 (cima/baixo favorece o banco)
    }

    @Test
    void aStaleQuoteIsRefusedAndNothingMoves() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");

        mvc.perform(trade(token, "buy", account, "USD", "10.00", "5.100000", key()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FX_RATE_CHANGED"));
        mvc.perform(trade(token, "buy", account, "USD", "10.00", BID, key())) // preço de venda não vale para compra
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FX_RATE_CHANGED"));

        assertThat(balance(token, account)).isEqualTo("1000.00");
    }

    @Test
    void itFollowsTheServerRateWhenItChanges() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        try {
            jdbc.update("update fx_rates set mid_rate = 6.000000 where currency = 'USD'");

            mvc.perform(trade(token, "buy", account, "USD", "10.00", ASK, key())) // a cotação antiga foi recusada
                    .andExpect(jsonPath("$.code").value("FX_RATE_CHANGED"));
            mvc.perform(trade(token, "buy", account, "USD", "10.00", "6.090000", key())) // 6.00 + 1,5%
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.brlAmount.amount").value("60.90"));
        } finally {
            jdbc.update("update fx_rates set mid_rate = 5.200000 where currency = 'USD'");
        }
    }

    @Test
    void refusesWhatCannotBeDone() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");

        mvc.perform(trade(token, "buy", account, "USD", "20.00", ASK, key())) // custa 105,56 > saldo
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mvc.perform(trade(token, "sell", account, "USD", "1.00", BID, key())) // nunca comprou
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FX_FUNDS"));
        mvc.perform(trade(token, "buy", account, "JPY", "10.00", ASK, key())).andExpect(status().isNotFound());
        mvc.perform(trade(token, "buy", account, "USD", "0", ASK, key())).andExpect(status().isBadRequest());
        mvc.perform(trade(token, "buy", account, "USD", "1.234", ASK, key())).andExpect(status().isBadRequest());
        mvc.perform(as(token, post("/api/v1/fx/buy")).contentType(MediaType.APPLICATION_JSON)
                        .content(body(account, "USD", "1.00", ASK)))
                .andExpect(status().isBadRequest()); // sem Idempotency-Key

        assertThat(balance(token, account)).isEqualTo("100.00");
    }

    @Test
    void cannotSellMoreThanTheWalletHolds() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        mvc.perform(trade(token, "buy", account, "USD", "10.00", ASK, key())).andExpect(status().isCreated());

        mvc.perform(trade(token, "sell", account, "USD", "10.01", BID, key()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FX_FUNDS"));
        mvc.perform(as(token, get("/api/v1/fx/wallets")))
                .andExpect(jsonPath("$[?(@.currency=='USD')].balance.amount").value("10.00"));
    }

    @Test
    void theFxLimitCapsEachPurchaseAndTheDay() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "50000.00");
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/limits")))
                .andExpect(jsonPath("$[?(@.type=='FX')].perOperation.amount").value("10000.00"))
                .andExpect(jsonPath("$[?(@.type=='FX')].daily.amount").value("20000.00"));

        mvc.perform(trade(token, "buy", account, "USD", "1900.00", ASK, key())) // 10.028,20 > 10.000 por operação
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
        mvc.perform(trade(token, "buy", account, "USD", "1800.00", ASK, key())).andExpect(status().isCreated()); // 9.500,40
        mvc.perform(trade(token, "buy", account, "USD", "1800.00", ASK, key())).andExpect(status().isCreated()); // 19.000,80
        mvc.perform(trade(token, "buy", account, "USD", "300.00", ASK, key())) // passaria de 20.000 no dia
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
    }

    @Test
    void theSameKeyTradesOnlyOnce() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String key = key();

        mvc.perform(trade(token, "buy", account, "USD", "10.00", ASK, key)).andExpect(status().isCreated());
        mvc.perform(trade(token, "buy", account, "USD", "10.00", ASK, key))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed", "true"));

        assertThat(balance(token, account)).isEqualTo("947.22"); // 1000 − 52.78, uma vez só
    }

    @Test
    void cannotTradeFromAnotherCustomersAccount() throws Exception {
        String owner = customer();
        String account = openAccount(owner);
        deposit(owner, account, "100.00");

        mvc.perform(trade(customer(), "buy", account, "USD", "1.00", ASK, key())).andExpect(status().isNotFound());
        assertThat(balance(owner, account)).isEqualTo("100.00");
    }

    @Test
    void parallelBuysNeverSpendMoreThanTheBalance() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Callable<Integer> call = () -> mvc.perform(trade(token, "buy", account, "USD", "50.00", ASK, key()))
                    .andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) {
                created++;
            }
        }
        pool.shutdown();

        // cada compra custa 263,90: cabem 3 em R$ 1.000. O dinheiro e a carteira têm que fechar com as que passaram.
        BigDecimal spent = new BigDecimal("1000.00").subtract(new BigDecimal(balance(token, account)));
        assertThat(spent).isEqualByComparingTo(new BigDecimal("263.90").multiply(BigDecimal.valueOf(created)));
        mvc.perform(as(token, get("/api/v1/fx/wallets")))
                .andExpect(jsonPath("$[?(@.currency=='USD')].balance.amount")
                        .value(String.valueOf(50 * created) + ".00"));
        assertThat(created).isBetween(1, 3);
    }

    // ---------- helpers ----------

    private static String body(String account, String currency, String amount, String rate) {
        return String.format("{\"accountId\":\"%s\",\"currency\":\"%s\",\"amount\":\"%s\",\"quotedRate\":\"%s\"}",
                account, currency, amount, rate);
    }

    private MockHttpServletRequestBuilder trade(String token, String side, String account, String currency,
            String amount, String rate, String key) {
        return as(token, post("/api/v1/fx/" + side)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(account, currency, amount, rate));
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

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
