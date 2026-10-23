package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.securebank.credit.application.CreditBillingService;
import com.securebank.shared.domain.CardId;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Multa, juros e avisos do crédito. O relógio da API não anda, então os testes chamam o processamento diário com a data
 * que querem ({@code billing.bill(cartão, dia)}), o mesmo que o agendador faz com a data de hoje.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CreditBillingIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CreditBillingService billing;

    @Test
    void anInvoiceLeftUnpaidGetsTheLateFeeOnceAndInterestEveryDay() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token));
        purchase(token, card, "200.00");
        LocalDate due = dueDateOfTheFirstInvoice(token, card);

        billing.bill(id(card), due.plusDays(3));

        // multa de 2% sobre os 200,00 e juros de 10% ao mês por dia, compostos: 0,68 nos três dias
        assertThat(charge(card, "LATE_FEE")).isEqualTo("4.00");
        assertThat(charge(card, "INTEREST")).isEqualTo("2.04");
        assertThat(count("select count(*) from credit_charges where card_id = ?::uuid and kind = 'INTEREST'", card)).isEqualTo(3);

        // rodar de novo no mesmo dia não duplica nada
        billing.bill(id(card), due.plusDays(3));
        assertThat(charge(card, "LATE_FEE")).isEqualTo("4.00");
        assertThat(charge(card, "INTEREST")).isEqualTo("2.04");

        // mais dois dias: só os dias novos entram, e a multa continua uma só
        billing.bill(id(card), due.plusDays(5));
        assertThat(count("select count(*) from credit_charges where card_id = ?::uuid and kind = 'INTEREST'", card)).isEqualTo(5);
        assertThat(count("select count(*) from credit_charges where card_id = ?::uuid and kind = 'LATE_FEE'", card)).isEqualTo(1);
    }

    @Test
    void payingTheMinimumAvoidsTheLateFeeAndOnlyTheRestEarnsInterest() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "100.00");
        String card = issueCredit(token, account);
        purchase(token, card, "200.00");
        String month = firstInvoiceMonth(token, card);
        LocalDate due = dueDateOfTheFirstInvoice(token, card);

        pay(token, card, month, "30.00").andExpect(status().isCreated()); // o mínimo: 15% de 200,00
        billing.bill(id(card), due.plusDays(2));

        assertThat(count("select count(*) from credit_charges where card_id = ?::uuid and kind = 'LATE_FEE'", card)).isZero();
        // 170,00 de saldo: 0,57 por dia (170,00 × 10% ÷ 30), compostos
        assertThat(charge(card, "INTEREST")).isEqualTo("1.14");
    }

    @Test
    void theInvoiceShowsTheChargesTheMinimumAndTheNewTotal() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token));
        purchase(token, card, "200.00");
        String month = firstInvoiceMonth(token, card);
        LocalDate due = dueDateOfTheFirstInvoice(token, card);

        mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices/" + month)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.minimumDue.amount").value("30.00"))
                .andExpect(jsonPath("$.invoice.charges.amount").value("0.00"))
                .andExpect(jsonPath("$.charges.length()").value(0));

        billing.bill(id(card), due.plusDays(2)); // 4,00 de multa + 0,68 + 0,68 de juros

        mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices/" + month)))
                .andExpect(jsonPath("$.invoice.principal.amount").value("200.00"))
                .andExpect(jsonPath("$.invoice.charges.amount").value("5.36"))
                .andExpect(jsonPath("$.invoice.total.amount").value("205.36"))
                .andExpect(jsonPath("$.invoice.remaining.amount").value("205.36"))
                .andExpect(jsonPath("$.charges[?(@.kind == 'LATE_FEE')].amount.amount").value("4.00"))
                .andExpect(jsonPath("$.charges[?(@.kind == 'INTEREST')].amount.amount").value("1.36"));
        // os encargos também são dívida: usam limite
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.used.amount").value("205.36"));
    }

    @Test
    void eachNoticeLeavesOnceAtTheRightTime() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token));
        purchase(token, card, "200.00");
        LocalDate due = dueDateOfTheFirstInvoice(token, card);
        LocalDate closing = due.minusDays(7);

        billing.bill(id(card), closing); // ainda aberta: nada
        assertThat(notices(card)).isZero();

        billing.bill(id(card), closing.plusDays(1));
        assertThat(outbox("CreditInvoiceClosed", card)).isEqualTo(1);
        billing.bill(id(card), closing.plusDays(2)); // repetir não duplica
        assertThat(outbox("CreditInvoiceClosed", card)).isEqualTo(1);

        billing.bill(id(card), due.minusDays(3));
        assertThat(outbox("CreditInvoiceDueSoon", card)).isEqualTo(1);
        billing.bill(id(card), due.minusDays(1));
        assertThat(outbox("CreditInvoiceDueSoon", card)).isEqualTo(1);

        billing.bill(id(card), due.plusDays(1));
        assertThat(outbox("CreditInvoiceOverdue", card)).isEqualTo(1);
        billing.bill(id(card), due.plusDays(4));
        assertThat(outbox("CreditInvoiceOverdue", card)).isEqualTo(1);
        assertThat(notices(card)).isEqualTo(3);
    }

    @Test
    void aPaidInvoiceGetsNoNoticeAndNoCharge() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "300.00");
        String card = issueCredit(token, account);
        purchase(token, card, "200.00");
        String month = firstInvoiceMonth(token, card);
        LocalDate due = dueDateOfTheFirstInvoice(token, card);
        pay(token, card, month, "200.00").andExpect(status().isCreated());

        billing.bill(id(card), due.minusDays(2));
        billing.bill(id(card), due.plusDays(10));

        assertThat(notices(card)).isZero();
        assertThat(count("select count(*) from credit_charges where card_id = ?::uuid", card)).isZero();
    }

    @Test
    void theDailyJobPicksUpCardsNotYetProcessedToday() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token));
        LocalDate today = LocalDate.of(2030, 1, 15);

        assertThat(billing.cardsDue(today)).contains(id(card)); // nunca processado
        billing.bill(id(card), today);
        assertThat(billing.cardsDue(today)).doesNotContain(id(card)); // já feito hoje
        assertThat(billing.cardsDue(today.plusDays(1))).contains(id(card)); // amanhã de novo
    }

    // ---------- helpers ----------

    private LocalDate dueDateOfTheFirstInvoice(String token, String card) throws Exception {
        return LocalDate.parse(firstOf(token, card, "dueDate"));
    }

    private String firstInvoiceMonth(String token, String card) throws Exception {
        return firstOf(token, card, "month");
    }

    /** Campo da primeira fatura que tem parcela (a lista vem da mais nova para a mais antiga, então o último é o mais antigo). */
    private String firstOf(String token, String card, String field) throws Exception {
        String body = mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices"))).andReturn().getResponse().getContentAsString();
        java.util.List<String> values = JsonPath.read(body, "$[?(@.principal.amount != '0.00')]." + field);
        return values.get(values.size() - 1);
    }

    private String charge(String card, String kind) {
        return jdbc.queryForObject("select coalesce(sum(amount), 0)::numeric(19,2)::text from credit_charges where card_id = ?::uuid and kind = ?",
                String.class, card, kind);
    }

    private int count(String sql, String card) {
        return jdbc.queryForObject(sql, Integer.class, card);
    }

    private int notices(String card) {
        return count("select count(*) from credit_notices where card_id = ?::uuid", card);
    }

    private int outbox(String type, String card) {
        return jdbc.queryForObject("select count(*) from outbox_events where event_type = ? and aggregate_id = ?", Integer.class, type, card);
    }

    private static CardId id(String card) {
        return CardId.of(card);
    }

    private void purchase(String token, String card, String amount) throws Exception {
        mvc.perform(as(token, post("/api/v1/credit/cards/" + card + "/purchases")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"amount\":\"%s\",\"merchant\":\"Loja\",\"installments\":1}", amount)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions pay(String token, String card, String month, String amount) throws Exception {
        return mvc.perform(as(token, post("/api/v1/credit/cards/" + card + "/invoices/" + month + "/payments")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content(String.format("{\"amount\":\"%s\"}", amount)));
    }

    private String issueCredit(String token, String account) throws Exception {
        return read(mvc.perform(as(token, post("/api/v1/credit/cards")).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"accountId\":\"%s\",\"dueDay\":10}", account))).andExpect(status().isCreated()).andReturn(), "$.id");
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
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}")).andExpect(status().isCreated());
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
