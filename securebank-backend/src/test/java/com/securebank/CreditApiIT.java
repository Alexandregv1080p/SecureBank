package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Cartão de crédito pela API, com Postgres real: limite, parcelas, faturas e o dinheiro saindo da conta só ao pagar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CreditApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuesACreditCardWithTheInitialLimitAndTheChosenDueDay() throws Exception {
        String token = customer();
        String account = openAccount(token);

        mvc.perform(as(token, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"dueDay\":10}", account)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CREDIT"))
                .andExpect(jsonPath("$.dueDay").value(10))
                .andExpect(jsonPath("$.creditLimit.amount").value("2000.00"))
                .andExpect(jsonPath("$.number").doesNotExist());

        // o mesmo cartão aparece na lista de cartões, com o tipo
        mvc.perform(as(token, get("/api/v1/cards"))).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].kind").value("CREDIT"));
    }

    @Test
    void theDueDayMustBeOneOfTheOfferedOnes() throws Exception {
        String token = customer();
        String account = openAccount(token);
        for (int bad : new int[] {0, 7, 26, 31, 99}) {
            mvc.perform(as(token, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                    .content(json("{\"accountId\":\"%s\",\"dueDay\":%d}", account, bad))).andExpect(status().isBadRequest());
        }
        mvc.perform(as(token, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\"}", account))).andExpect(status().isBadRequest());
    }

    @Test
    void anInstallmentPurchaseUsesLimitNowAndSpreadsAcrossInvoices() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String card = issueCredit(token, account, 10);

        mvc.perform(as(token, get("/api/v1/credit/cards/" + card)))
                .andExpect(jsonPath("$.used.amount").value("0.00")).andExpect(jsonPath("$.available.amount").value("2000.00"));

        purchase(token, card, "600.00", "Eletronicos", 3, key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.installments").value(3))
                .andExpect(jsonPath("$.installmentAmount.amount").value("200.00"));

        // usa limite, mas o saldo da conta NÃO mexe: o dinheiro só sai quando a fatura é paga
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card)))
                .andExpect(jsonPath("$.used.amount").value("600.00")).andExpect(jsonPath("$.available.amount").value("1400.00"));
        assertThat(balance(token, account)).isEqualTo("1000.00");

        MvcResult invoices = mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices")))
                .andExpect(status().isOk()).andReturn();
        String body = invoices.getResponse().getContentAsString();
        java.util.List<String> amounts = JsonPath.read(body, "$[?(@.total.amount != '0.00')].total.amount");
        assertThat(amounts).containsExactly("200.00", "200.00", "200.00"); // uma parcela por fatura, em meses seguidos
        java.util.List<String> months = JsonPath.read(body, "$[?(@.total.amount != '0.00')].month");
        assertThat(months).hasSize(3).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void aPurchaseOverTheAvailableLimitIsRefusedAndTheRestKeepsWorking() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 15);

        purchase(token, card, "2000.01", "Loja", 1, key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDED"));
        purchase(token, card, "1500.00", "Loja", 1, key()).andExpect(status().isCreated());
        purchase(token, card, "500.01", "Loja", 1, key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDED"));
        purchase(token, card, "500.00", "Loja", 2, key()).andExpect(status().isCreated()); // usa o limite todo
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.available.amount").value("0.00"));
    }

    @Test
    void thePurchaseRulesAreEnforcedByTheServer() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 20);

        purchase(token, card, "100.00", "Loja", 13, key()).andExpect(status().isBadRequest());
        purchase(token, card, "100.00", "Loja", 0, key()).andExpect(status().isBadRequest());
        purchase(token, card, "20.00", "Loja", 5, key()).andExpect(status().isBadRequest()); // 4,00 por parcela
        purchase(token, card, "100.00", "   ", 1, key()).andExpect(status().isBadRequest());
        purchase(token, card, "100.00", "Loja\\nlinha 2", 1, key()).andExpect(status().isBadRequest());
        purchase(token, card, "0.00", "Loja", 1, key()).andExpect(status().isBadRequest());
        mvc.perform(as(token, post("/api/v1/credit/cards/" + card + "/purchases")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"10.00\",\"merchant\":\"Loja\"}")).andExpect(status().isBadRequest()); // sem Idempotency-Key
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.used.amount").value("0.00"));
    }

    @Test
    void theSamePurchaseKeyDoesNotUseTheLimitTwice() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 10);
        String key = key();

        purchase(token, card, "300.00", "Loja", 1, key).andExpect(status().isCreated());
        purchase(token, card, "300.00", "Loja", 1, key).andExpect(status().isCreated());

        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.used.amount").value("300.00"));
    }

    @Test
    void payingAnInvoiceTakesMoneyFromTheAccountAndFreesTheLimit() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String card = issueCredit(token, account, 10);
        purchase(token, card, "400.00", "Loja", 1, key()).andExpect(status().isCreated());
        String month = firstInvoiceMonthWithDebt(token, card);

        // paga uma parte
        pay(token, card, month, "150.00", key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.paid.amount").value("150.00"))
                .andExpect(jsonPath("$.remaining.amount").value("250.00"));
        assertThat(balance(token, account)).isEqualTo("850.00");
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card)))
                .andExpect(jsonPath("$.used.amount").value("250.00")).andExpect(jsonPath("$.available.amount").value("1750.00"));

        // mais do que falta, ou mais do que tem na conta, é recusado
        pay(token, card, month, "250.01", key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));
        // quita
        pay(token, card, month, "250.00", key()).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(balance(token, account)).isEqualTo("600.00");
        pay(token, card, month, "1.00", key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));

        // o pagamento aparece no extrato da conta e no detalhe da fatura
        mvc.perform(as(token, get("/api/v1/accounts/" + account + "/statement")))
                .andExpect(jsonPath("$.items[0].type").value("CARD_INVOICE_PAYMENT"))
                .andExpect(jsonPath("$.items[0].direction").value("DEBIT"));
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices/" + month)))
                .andExpect(jsonPath("$.payments", hasSize(2))).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].merchant").value("Loja"));
    }

    @Test
    void payingNeedsBalanceInTheAccount() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String card = issueCredit(token, account, 10);
        purchase(token, card, "500.00", "Loja", 1, key()).andExpect(status().isCreated());
        String month = firstInvoiceMonthWithDebt(token, card);

        pay(token, card, month, "100.01", key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(balance(token, account)).isEqualTo("100.00");
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.used.amount").value("500.00"));
    }

    @Test
    void aCreditCardWithDebtCannotBeCanceledUntilItIsPaid() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "300.00");
        String card = issueCredit(token, account, 10);
        purchase(token, card, "200.00", "Loja", 1, key()).andExpect(status().isCreated());

        cancel(token, card).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CARD_HAS_DEBT"));
        pay(token, card, firstInvoiceMonthWithDebt(token, card), "200.00", key()).andExpect(status().isCreated());
        cancel(token, card).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
        purchase(token, card, "10.00", "Loja", 1, key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_NOT_USABLE"));
    }

    @Test
    void aBlockedCreditCardRefusesPurchases() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 10);
        mvc.perform(as(token, post("/api/v1/cards/" + card + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Perdi o cartao\"}")).andExpect(status().isOk());

        purchase(token, card, "10.00", "Loja", 1, key()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_NOT_USABLE"));
    }

    @Test
    void creditAndDebitCardsDoNotMix() throws Exception {
        String token = customer();
        String account = openAccount(token);
        String credit = issueCredit(token, account, 10);
        String debit = read(mvc.perform(as(token, post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\"}", account))).andExpect(status().isCreated()).andReturn(), "$.id");

        // compra de débito num cartão de crédito
        mvc.perform(as(token, post("/api/v1/cards/" + credit + "/purchases")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"10.00\",\"merchant\":\"Loja\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CARD_IS_CREDIT"));
        // crédito num cartão de débito: não existe como cartão de crédito
        purchase(token, debit, "10.00", "Loja", 1, key()).andExpect(status().isNotFound());
        mvc.perform(as(token, get("/api/v1/credit/cards/" + debit))).andExpect(status().isNotFound());
    }

    @Test
    void eachKindHasItsOwnQuotaOfThreeCardsPerAccount() throws Exception {
        String token = customer();
        String account = openAccount(token);
        for (int i = 0; i < 3; i++) {
            issueCredit(token, account, 10);
        }
        mvc.perform(as(token, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"accountId\":\"%s\",\"dueDay\":10}", account)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CARD_LIMIT_REACHED"));
        // o débito tem a cota dele
        mvc.perform(as(token, post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\"}", account))).andExpect(status().isCreated());
    }

    @Test
    void anotherCustomerCannotSeeUseOrPayMyCreditCard() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String card = issueCredit(token, account, 10);
        purchase(token, card, "50.00", "Loja", 1, key()).andExpect(status().isCreated());
        String month = firstInvoiceMonthWithDebt(token, card);
        String other = customer();

        mvc.perform(as(other, get("/api/v1/credit/cards/" + card))).andExpect(status().isNotFound());
        mvc.perform(as(other, get("/api/v1/credit/cards/" + card + "/invoices"))).andExpect(status().isNotFound());
        mvc.perform(as(other, get("/api/v1/credit/cards/" + card + "/invoices/" + month))).andExpect(status().isNotFound());
        purchase(other, card, "1.00", "Loja", 1, key()).andExpect(status().isNotFound());
        pay(other, card, month, "1.00", key()).andExpect(status().isNotFound());
        mvc.perform(as(other, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\",\"dueDay\":10}", account))).andExpect(status().isNotFound());
    }

    @Test
    void anInvalidMonthIsRejected() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 10);
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices/2026-13"))).andExpect(status().isBadRequest());
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices/abc"))).andExpect(status().isBadRequest());
    }

    @Test
    void staffCannotOperateCustomerCreditCards() throws Exception {
        String support = TestUsers.staffToken(mvc, "SUPPORT");
        String id = UUID.randomUUID().toString();
        mvc.perform(as(support, get("/api/v1/credit/cards/" + id))).andExpect(status().isForbidden());
        mvc.perform(as(support, post("/api/v1/credit/cards/" + id + "/purchases")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void theCustomerCanLowerAndRaiseTheLimitWithinTheRules() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 10);
        purchase(token, card, "500.00", "Loja", 1, key()).andExpect(status().isCreated());

        limit(token, card, "1000.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit.amount").value("1000.00")).andExpect(jsonPath("$.available.amount").value("500.00"));
        limit(token, card, "400.00").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CREDIT_LIMIT_BELOW_USED"));
        limit(token, card, "500.00").andExpect(status().isOk()).andExpect(jsonPath("$.available.amount").value("0.00"));
        limit(token, card, "1050.00").andExpect(status().isBadRequest()); // não é múltiplo de 100
        limit(token, card, "50.00").andExpect(status().isBadRequest());
        limit(token, card, "10100.00").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CREDIT_LIMIT_ABOVE_MAX"));
        limit(token, card, "10000.00").andExpect(status().isOk());
        purchase(token, card, "9000.00", "Loja", 1, key()).andExpect(status().isCreated()); // o limite novo vale na hora
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.creditLimit.amount").value("10000.00"));
    }

    @Test
    void aBlockedCardCannotHaveItsLimitRaisedButCanHaveItLowered() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 10);
        mvc.perform(as(token, post("/api/v1/cards/" + card + "/block")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Perdi o cartao\"}")).andExpect(status().isOk());

        limit(token, card, "3000.00").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CARD_NOT_USABLE"));
        limit(token, card, "500.00").andExpect(status().isOk());
    }

    @Test
    void anotherCustomerCannotChangeMyLimit() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token), 10);
        limit(customer(), card, "100.00").andExpect(status().isNotFound());
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.creditLimit.amount").value("2000.00"));
    }

    @Test
    void theStaffSeesTheCustomersCreditCardsAndAdminsSetTheLimitAboveTheSelfServiceCeiling() throws Exception {
        TestUsers.Login customer = TestUsers.registerAndLogin(mvc);
        String account = openAccount(customer.accessToken());
        String card = issueCredit(customer.accessToken(), account, 15);
        purchase(customer.accessToken(), card, "700.00", "Loja", 1, key()).andExpect(status().isCreated());
        String support = TestUsers.staffToken(mvc, "SUPPORT");
        String admin = TestUsers.staffToken(mvc, "ADMIN");

        mvc.perform(as(support, get("/api/v1/admin/customers/" + customer.customerId() + "/credit-cards")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(card)).andExpect(jsonPath("$[0].used.amount").value("700.00"))
                .andExpect(jsonPath("$[0].dueDay").value(15)).andExpect(jsonPath("$[0].number").doesNotExist());

        // suporte só lê; quem decide o limite é o admin
        mvc.perform(as(support, put("/api/v1/admin/credit/cards/" + card + "/limit")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":\"20000.00\"}")).andExpect(status().isForbidden());
        mvc.perform(as(admin, put("/api/v1/admin/credit/cards/" + card + "/limit")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":\"20000.00\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.creditLimit.amount").value("20000.00"));
        mvc.perform(as(admin, put("/api/v1/admin/credit/cards/" + card + "/limit")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":\"600.00\"}")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_BELOW_USED"));
        mvc.perform(as(admin, put("/api/v1/admin/credit/cards/" + card + "/limit")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":\"100100.00\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(customer.accessToken(), get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.creditLimit.amount").value("20000.00"));
        // um cliente comum não chega nas rotas da equipe
        mvc.perform(as(customer.accessToken(), put("/api/v1/admin/credit/cards/" + card + "/limit")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":\"100000.00\"}")).andExpect(status().isForbidden());
        // e o do débito não é de crédito
        String debit = read(mvc.perform(as(customer.accessToken(), post("/api/v1/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\"}", account))).andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(as(admin, put("/api/v1/admin/credit/cards/" + debit + "/limit")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":\"1000.00\"}")).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private ResultActions limit(String token, String card, String value) throws Exception {
        return mvc.perform(as(token, put("/api/v1/credit/cards/" + card + "/limit")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"limit\":\"%s\"}", value)));
    }


    /** Mês (AAAA-MM) da primeira fatura que ficou com saldo a pagar. */
    private String firstInvoiceMonthWithDebt(String token, String card) throws Exception {
        String body = mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices"))).andReturn().getResponse().getContentAsString();
        java.util.List<String> months = JsonPath.read(body, "$[?(@.remaining.amount != '0.00')].month");
        return months.get(months.size() - 1); // a lista vem da mais nova para a mais antiga
    }

    private ResultActions purchase(String token, String card, String amount, String merchant, int installments, String key) throws Exception {
        return mvc.perform(as(token, post("/api/v1/credit/cards/" + card + "/purchases")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"amount\":\"%s\",\"merchant\":\"%s\",\"installments\":%d}", amount, merchant, installments)));
    }

    private ResultActions pay(String token, String card, String month, String amount, String key) throws Exception {
        return mvc.perform(as(token, post("/api/v1/credit/cards/" + card + "/invoices/" + month + "/payments")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json("{\"amount\":\"%s\"}", amount)));
    }

    private ResultActions cancel(String token, String card) throws Exception {
        return mvc.perform(as(token, post("/api/v1/cards/" + card + "/cancel")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Nao preciso mais\"}"));
    }

    private String issueCredit(String token, String account, int dueDay) throws Exception {
        return read(mvc.perform(as(token, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"accountId\":\"%s\",\"dueDay\":%d}", account, dueDay))).andExpect(status().isCreated()).andReturn(), "$.id");
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
