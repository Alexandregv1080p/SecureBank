package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O crédito com o tempo passando de verdade: o relógio do banco é trocado por um que o teste move, então a fatura fecha,
 * vence, rende multa e juros e pode ser parcelada, tudo pela API. Datas em março de 2027 com vencimento no dia 10
 * (a fatura fecha no dia 3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, CreditTimeTravelIT.ClockConfig.class})
class CreditTimeTravelIT {

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2027-03-01T15:00:00Z"));

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    @Autowired MockMvc mvc;

    @BeforeEach
    void startOfMarch() {
        at("2027-03-01");
    }

    @Test
    void anInvoiceOpensClosesAndTheNextPurchaseGoesToTheNextOne() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token));
        purchase(token, card, "400.00", 1).andExpect(status().isCreated()); // dia 1: ainda cabe na fatura de março

        invoice(token, card, "2027-03").andExpect(jsonPath("$.invoice.status").value("OPEN"))
                .andExpect(jsonPath("$.invoice.closingDate").value("2027-03-03")).andExpect(jsonPath("$.invoice.dueDate").value("2027-03-10"));

        at("2027-03-04"); // fechou
        invoice(token, card, "2027-03").andExpect(jsonPath("$.invoice.status").value("CLOSED"))
                .andExpect(jsonPath("$.invoice.minimumDue.amount").value("60.00")); // 15% de 400,00
        purchase(token, card, "100.00", 1).andExpect(status().isCreated()); // depois do fechamento: fatura de abril
        invoice(token, card, "2027-04").andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.invoice.principal.amount").value("100.00"));
        invoice(token, card, "2027-03").andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.invoice.principal.amount").value("400.00"));
    }

    @Test
    void anOverdueInvoiceChargesFeeAndInterestAndBlocksPurchasesUntilTheMinimumIsPaid() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String card = issueCredit(token, account);
        purchase(token, card, "400.00", 1).andExpect(status().isCreated());

        at("2027-03-10"); // vence hoje: ainda dá tempo
        invoice(token, card, "2027-03").andExpect(jsonPath("$.invoice.status").value("CLOSED")).andExpect(jsonPath("$.invoice.charges.amount").value("0.00"));

        at("2027-03-11"); // venceu sem o mínimo: multa de 2% (8,00) e um dia de juros (408,00 × 10% ÷ 30 = 1,36)
        invoice(token, card, "2027-03").andExpect(jsonPath("$.invoice.status").value("OVERDUE"))
                .andExpect(jsonPath("$.invoice.charges.amount").value("9.36"))
                .andExpect(jsonPath("$.invoice.remaining.amount").value("409.36"))
                .andExpect(jsonPath("$.charges[?(@.kind == 'LATE_FEE')].amount.amount").value("8.00"));
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.used.amount").value("409.36"));
        purchase(token, card, "10.00", 1).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CREDIT_OVERDUE"));

        // paga o mínimo (60,00): sai do bloqueio e fica no rotativo, rendendo juros só sobre o resto
        pay(token, card, "2027-03", "60.00").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REVOLVING"))
                .andExpect(jsonPath("$.remaining.amount").value("349.36"));
        purchase(token, card, "10.00", 1).andExpect(status().isCreated());
        at("2027-03-12"); // mais um dia: 349,36 × 10% ÷ 30 = 1,16
        invoice(token, card, "2027-03").andExpect(jsonPath("$.invoice.status").value("REVOLVING"))
                .andExpect(jsonPath("$.invoice.remaining.amount").value("350.52"));
    }

    @Test
    void payingTheMinimumBeforeTheDueDateAvoidsTheFeeAndEarnsOnlyInterestOnTheRest() throws Exception {
        String token = customer();
        String account = openAccount(token);
        deposit(token, account, "1000.00");
        String card = issueCredit(token, account);
        purchase(token, card, "400.00", 1).andExpect(status().isCreated());
        at("2027-03-05");
        pay(token, card, "2027-03", "60.00").andExpect(status().isCreated());

        at("2027-03-11");
        invoice(token, card, "2027-03").andExpect(jsonPath("$.invoice.status").value("REVOLVING"))
                .andExpect(jsonPath("$.charges[?(@.kind == 'LATE_FEE')]").isEmpty())
                .andExpect(jsonPath("$.invoice.charges.amount").value("1.13")); // 340,00 × 10% ÷ 30 = 1,1333
    }

    @Test
    void aClosedInvoiceCanBePaidInInstallmentsWithInterestAndItClearsTheArrears() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token));
        purchase(token, card, "400.00", 1).andExpect(status().isCreated());

        // ainda aberta: não se parcela
        refinanceOptions(token, card, "2027-03").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));

        at("2027-03-11"); // vencida: 409,36 a pagar
        refinanceOptions(token, card, "2027-03").andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].installments").value(2))
                .andExpect(jsonPath("$.options[0].monthlyRate").value("5"))
                .andExpect(jsonPath("$.options[?(@.installments == 4)].installment.amount").value("115.44"))
                .andExpect(jsonPath("$.options[?(@.installments == 4)].total.amount").value("461.76"));
        purchase(token, card, "10.00", 1).andExpect(status().isUnprocessableEntity());

        refinance(token, card, "2027-03", 4).andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.status").value("PAID")).andExpect(jsonPath("$.plan.installments").value(4))
                .andExpect(jsonPath("$.purchase.kind").value("REFINANCE")).andExpect(jsonPath("$.purchase.merchant").value("Parcelamento da fatura 03/2027"));

        // as 4 parcelas caem nas próximas faturas (abril a julho) e o bloqueio acaba
        for (String month : new String[] {"2027-04", "2027-05", "2027-06", "2027-07"}) {
            invoice(token, card, month).andExpect(jsonPath("$.invoice.principal.amount").value("115.44"));
        }
        mvc.perform(as(token, get("/api/v1/credit/cards/" + card))).andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.used.amount").value("461.76"));
        purchase(token, card, "10.00", 1).andExpect(status().isCreated());
        // a fatura parcelada não rende mais nada
        at("2027-03-20");
        invoice(token, card, "2027-03").andExpect(jsonPath("$.invoice.status").value("PAID")).andExpect(jsonPath("$.invoice.remaining.amount").value("0.00"))
                .andExpect(jsonPath("$.payments[0].kind").value("REFINANCE"));
        // e não dá para parcelar a mesma fatura de novo
        refinance(token, card, "2027-03", 3).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));
    }

    @Test
    void theRefinanceRulesAreEnforced() throws Exception {
        String token = customer();
        String card = issueCredit(token, openAccount(token));
        purchase(token, card, "40.00", 1).andExpect(status().isCreated());
        at("2027-03-04");

        refinance(token, card, "2027-03", 2).andExpect(status().isUnprocessableEntity()); // menos de R$ 50,00 não parcela

        at("2027-03-01"); // outro cliente compra antes de a fatura de março fechar
        String other = customer();
        String otherCard = issueCredit(other, openAccount(other));
        purchase(other, otherCard, "300.00", 1).andExpect(status().isCreated());
        at("2027-03-04");
        refinance(other, otherCard, "2027-03", 1).andExpect(status().isBadRequest()); // de 2 a 12 parcelas
        refinance(other, otherCard, "2027-03", 13).andExpect(status().isBadRequest());
        refinance(token, otherCard, "2027-03", 3).andExpect(status().isNotFound()); // cartão de outro cliente
        refinance(other, otherCard, "2027-03", 12).andExpect(status().isCreated()); // 12x de R$ 33,85: acima do mínimo de R$ 10,00
    }

    // ---------- helpers ----------

    private static void at(String day) {
        CLOCK.set(Instant.parse(day + "T15:00:00Z"));
    }

    private ResultActions invoice(String token, String card, String month) throws Exception {
        return mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices/" + month))).andExpect(status().isOk());
    }

    private ResultActions refinanceOptions(String token, String card, String month) throws Exception {
        return mvc.perform(as(token, get("/api/v1/credit/cards/" + card + "/invoices/" + month + "/refinance")));
    }

    private ResultActions refinance(String token, String card, String month, int installments) throws Exception {
        return mvc.perform(as(token, post("/api/v1/credit/cards/" + card + "/invoices/" + month + "/refinance")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"installments\":" + installments + "}"));
    }

    private ResultActions purchase(String token, String card, String amount, int installments) throws Exception {
        return mvc.perform(as(token, post("/api/v1/credit/cards/" + card + "/purchases")).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"amount\":\"%s\",\"merchant\":\"Loja\",\"installments\":%d}", amount, installments)));
    }

    private ResultActions pay(String token, String card, String month, String amount) throws Exception {
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
