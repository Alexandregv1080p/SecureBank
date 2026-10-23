package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.securebank.card.domain.CardNumber;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Cartão de débito virtual pela API, com Postgres real: o dinheiro da compra sai da conta e o número nunca fica em claro. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CardApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuesACardThatShowsOnlyTheLastDigits() throws Exception {
        String token = customer();
        String account = openAccount(token);

        MvcResult issued = mvc.perform(as(token, post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\"}", account)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.holderName").value("CLIENTE TESTE"))
                .andExpect(jsonPath("$.maskedNumber").value(org.hamcrest.Matchers.startsWith("**** **** **** ")))
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.cvv").doesNotExist())
                .andReturn();
        String card = read(issued, "$.id");

        mvc.perform(as(token, get("/api/v1/cards"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(card));
        mvc.perform(as(token, get("/api/v1/cards/" + card))).andExpect(status().isOk());
    }

    @Test
    void revealShowsAValidNumberWithAStableCvvAndIsNeverCached() throws Exception {
        String token = customer();
        String card = issue(token, openAccount(token));

        MvcResult first = mvc.perform(as(token, post("/api/v1/cards/" + card + "/reveal")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        String number = read(first, "$.number");
        String cvv = read(first, "$.cvv");
        assertThat(CardNumber.isValid(number)).isTrue();
        assertThat(number).startsWith("9");
        assertThat(cvv).matches("\\d{3}");

        String again = read(mvc.perform(as(token, post("/api/v1/cards/" + card + "/reveal"))).andReturn(), "$.cvv");
        assertThat(again).isEqualTo(cvv);

        // o número NÃO está em claro no banco, e o CVV não está em lugar nenhum
        String stored = jdbc.queryForObject("select number_encrypted from cards where id = ?::uuid", String.class, card);
        assertThat(stored).doesNotContain(number);
        assertThat(jdbc.queryForObject("select last4 from cards where id = ?::uuid", String.class, card))
                .isEqualTo(number.substring(12));
        // cada consulta ao número fica na auditoria
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where event = 'CARD_REVEALED' and detail = ?",
                Integer.class, card)).isEqualTo(2);
    }

    @Test
    void aPurchaseComesOutOfTheBalanceAndShowsInTheStatementAndTheCardHistory() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "500.00");
        String card = issue(token, account);

        purchase(token, card, "129.90", "Mercado Central", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount.amount").value("129.90"))
                .andExpect(jsonPath("$.merchant").value("Mercado Central"));

        assertThat(balance(token, account)).isEqualTo("370.10");
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("CARD_PURCHASE"))
                .andExpect(jsonPath("$.items[0].direction").value("DEBIT"));
        mvc.perform(as(token, get("/api/v1/cards/" + card + "/purchases")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].merchant").value("Mercado Central"));
    }

    @Test
    void theSamePurchaseKeyDoesNotChargeTwice() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "200.00");
        String card = issue(token, account);
        String key = key();

        purchase(token, card, "50.00", "Padaria", key).andExpect(status().isCreated());
        purchase(token, card, "50.00", "Padaria", key).andExpect(status().isCreated());

        assertThat(balance(token, account)).isEqualTo("150.00");
    }

    @Test
    void aPurchaseNeedsBalanceAndRespectsTheCardLimit() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String card = issue(token, account);

        purchase(token, card, "100.01", "Loja", key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        deposit(token, account, "100000.00");
        purchase(token, card, "3000.01", "Loja", key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
        purchase(token, card, "3000.00", "Loja", key()).andExpect(status().isCreated());
        purchase(token, card, "2000.01", "Loja", key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED")); // passa do diário (5.000,00)
        assertThat(balance(token, account)).isEqualTo("97100.00");
    }

    @Test
    void aBlockedCardRefusesPurchasesUntilItIsUnblockedAndACanceledOneNeverComesBack() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String card = issue(token, account);

        mvc.perform(as(token, post("/api/v1/cards/" + card + "/block"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        purchase(token, card, "10.00", "Loja", key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_NOT_USABLE"));

        mvc.perform(as(token, post("/api/v1/cards/" + card + "/unblock"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        purchase(token, card, "10.00", "Loja", key()).andExpect(status().isCreated());

        mvc.perform(as(token, post("/api/v1/cards/" + card + "/cancel"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        mvc.perform(as(token, post("/api/v1/cards/" + card + "/unblock"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(as(token, post("/api/v1/cards/" + card + "/reveal"))).andExpect(status().isUnprocessableEntity());
        purchase(token, card, "10.00", "Loja", key()).andExpect(status().isUnprocessableEntity());
        mvc.perform(as(token, get("/api/v1/cards"))).andExpect(jsonPath("$", hasSize(0))); // cancelado sai da lista
    }

    @Test
    void anotherCustomerCannotSeeOrUseMyCard() throws Exception {
        String token = customer();
        String account = openAccount(token);
        String card = issue(token, account);
        String other = customer();

        mvc.perform(as(other, get("/api/v1/cards/" + card))).andExpect(status().isNotFound());
        mvc.perform(as(other, post("/api/v1/cards/" + card + "/reveal"))).andExpect(status().isNotFound());
        mvc.perform(as(other, post("/api/v1/cards/" + card + "/block"))).andExpect(status().isNotFound());
        purchase(other, card, "1.00", "Loja", key()).andExpect(status().isNotFound());
        mvc.perform(as(other, get("/api/v1/cards"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(other, post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\"}", account))).andExpect(status().isNotFound()); // conta de outro dono
    }

    @Test
    void anAccountHasAtMostThreeOpenCardsAndACanceledOneFreesASlot() throws Exception {
        String token = customer();
        String account = openAccount(token);
        String first = issue(token, account);
        issue(token, account);
        issue(token, account);

        mvc.perform(as(token, post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\"}", account)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_LIMIT_REACHED"));

        mvc.perform(as(token, post("/api/v1/cards/" + first + "/cancel"))).andExpect(status().isOk());
        issue(token, account);
    }

    @Test
    void theMerchantIsRequiredAndCannotCarryControlCharacters() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String card = issue(token, account);

        purchase(token, card, "1.00", "   ", key()).andExpect(status().isBadRequest());
        purchase(token, card, "1.00", "Loja\\nLinha 2", key()).andExpect(status().isBadRequest());
        purchase(token, card, "1.00", "x".repeat(61), key()).andExpect(status().isBadRequest());
        purchase(token, card, "0.00", "Loja", key()).andExpect(status().isBadRequest());
        assertThat(balance(token, account)).isEqualTo("100.00");
    }

    @Test
    void aPurchaseNeedsAnIdempotencyKeyAndAnActiveAccount() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String card = issue(token, account);

        mvc.perform(as(token, post("/api/v1/cards/" + card + "/purchases")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"1.00\",\"merchant\":\"Loja\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void staffCannotOperateCustomerCards() throws Exception {
        String support = TestUsers.staffToken(mvc, "SUPPORT");
        String admin = TestUsers.staffToken(mvc, "ADMIN");
        String id = UUID.randomUUID().toString();
        for (String staff : new String[] {support, admin}) {
            mvc.perform(as(staff, get("/api/v1/cards"))).andExpect(status().isForbidden());
            mvc.perform(as(staff, post("/api/v1/cards/" + id + "/reveal"))).andExpect(status().isForbidden());
            mvc.perform(as(staff, post("/api/v1/cards/" + id + "/purchases")).header("Idempotency-Key", key())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
    }

    // ---------- helpers ----------

    private ResultActions purchase(String token, String card, String amount, String merchant, String key) throws Exception {
        return mvc.perform(as(token, post("/api/v1/cards/" + card + "/purchases")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"amount\":\"%s\",\"merchant\":\"%s\"}", amount, merchant)));
    }

    private String issue(String token, String account) throws Exception {
        return read(mvc.perform(as(token, post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\"}", account))).andExpect(status().isCreated()).andReturn(), "$.id");
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
