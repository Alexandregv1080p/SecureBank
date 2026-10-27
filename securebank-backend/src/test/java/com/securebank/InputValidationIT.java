package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
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

/**
 * Entrada inválida nunca vira erro 500: valor fora da faixa, texto gigante ou com caracteres de controle, tipo errado,
 * corpo quebrado, paginação absurda e ids malformados recebem sempre uma resposta 4xx (ou 200 com o limite aplicado).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class InputValidationIT {

    @Autowired MockMvc mvc;

    private String token;
    private String account;

    private void setup() throws Exception {
        token = TestUsers.registerAndLogin(mvc).accessToken();
        account = JsonPath.read(mvc.perform(TestUsers.bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int send(MockHttpServletRequestBuilder request, String body) throws Exception {
        MockHttpServletRequestBuilder builder = TestUsers.bearer(token, request).header("Idempotency-Key", UUID.randomUUID().toString());
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = mvc.perform(builder).andReturn();
        return result.getResponse().getStatus();
    }

    private static String json(String template, Object... args) {
        return String.format(template, args);
    }

    private void never5xx(String what, int status) {
        assertThat(status).as(what).isLessThan(500);
    }

    @Test
    void badAmountsAreRefusedOnEveryMoneyEndpoint() throws Exception {
        setup();
        String[] amounts = {"0", "-1", "abc", "99999999999999999999", "0.001", "NaN", "", " ", "\"x\"", "[1]", "{}", "null"};
        for (String amount : amounts) {
            String value = amount.startsWith("\"") || amount.startsWith("[") || amount.startsWith("{") || amount.equals("null") ? amount : "\"" + amount + "\"";
            String[][] calls = {
                    {"/api/v1/accounts/" + account + "/deposits", json("{\"amount\":%s}", value)},
                    {"/api/v1/accounts/" + account + "/withdrawals", json("{\"amount\":%s}", value)},
                    {"/api/v1/transfers", json("{\"sourceAccountId\":\"%s\",\"destinationBranch\":\"0001\",\"destinationAccountNumber\":\"1\",\"amount\":%s}", account, value)},
                    {"/api/v1/payments", json("{\"accountId\":\"%s\",\"amount\":%s,\"barcode\":\"123\"}", account, value)},
                    {"/api/v1/piggies", json("{\"accountId\":\"%s\",\"name\":\"Meta\",\"goal\":%s}", account, value)},
                    {"/api/v1/pix/charges", json("{\"accountId\":\"%s\",\"amount\":%s}", account, value)},
                    {"/api/v1/pix/transfers", json("{\"sourceAccountId\":\"%s\",\"key\":\"a@b.com\",\"amount\":%s}", account, value)},
                    {"/api/v1/fx/buy", json("{\"accountId\":\"%s\",\"currency\":\"USD\",\"amount\":%s,\"quotedRate\":\"5.2\"}", account, value)},
                    {"/api/v1/investments", json("{\"accountId\":\"%s\",\"productCode\":\"CDB\",\"amount\":%s}", account, value)},
            };
            for (String[] call : calls) {
                int status = send(post(call[0]), call[1]);
                if (call[0].equals("/api/v1/piggies") && (amount.isBlank() || amount.equals("null"))) {
                    continue; // a meta do porquinho é opcional: vazio ou nulo significa "sem meta"
                }
                assertThat(status).as("%s amount=%s", call[0], amount).isBetween(400, 499);
            }
        }
    }

    @Test
    void oversizedAndControlCharacterTextIsRefusedOrHandledNeverCrashes() throws Exception {
        setup();
        String[] texts = {"x".repeat(5000), "a\\u0000b", "linha1\\nlinha2", "<script>alert(1)</script>", "'; drop table users;--", "😀".repeat(300), ""};
        for (String text : texts) {
            String[][] calls = {
                    {"/api/v1/transfers", json("{\"sourceAccountId\":\"%s\",\"destinationBranch\":\"%s\",\"destinationAccountNumber\":\"%s\",\"amount\":\"1.00\",\"description\":\"%s\"}", account, text, text, text)},
                    {"/api/v1/payments", json("{\"accountId\":\"%s\",\"amount\":\"1.00\",\"barcode\":\"%s\",\"description\":\"%s\"}", account, text, text)},
                    {"/api/v1/piggies", json("{\"accountId\":\"%s\",\"name\":\"%s\"}", account, text)},
                    {"/api/v1/pix/transfers", json("{\"sourceAccountId\":\"%s\",\"key\":\"%s\",\"amount\":\"1.00\",\"message\":\"%s\"}", account, text, text)},
                    {"/api/v1/pix/charges", json("{\"accountId\":\"%s\",\"amount\":\"1.00\",\"description\":\"%s\"}", account, text)},
                    {"/api/v1/fx/buy", json("{\"accountId\":\"%s\",\"currency\":\"%s\",\"amount\":\"1.00\",\"quotedRate\":\"5.2\"}", account, text)},
                    {"/api/v1/investments", json("{\"accountId\":\"%s\",\"productCode\":\"%s\",\"amount\":\"1.00\"}", account, text)},
                    {"/api/v1/security/password", json("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}", text, text)},
                    {"/api/v1/security/mfa/confirm", json("{\"code\":\"%s\"}", text)},
                    {"/api/v1/cards/" + UUID.randomUUID() + "/block", json("{\"reason\":\"%s\"}", text)},
            };
            for (String[] call : calls) {
                never5xx(call[0] + " texto=" + text.substring(0, Math.min(12, text.length())), send(post(call[0]), call[1]));
            }
        }
        // cadastro e login: texto absurdo em qualquer campo é recusado (sem token, direto)
        for (String text : texts) {
            String register = json("{\"name\":\"%s\",\"document\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"password\":\"%s\"}", text, text, text, text, text);
            int status = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register)).andReturn().getResponse().getStatus();
            assertThat(status).as("register").isBetween(400, 499);
            status = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", text, text))).andReturn().getResponse().getStatus();
            assertThat(status).as("login").isBetween(400, 499);
        }
    }

    @Test
    void brokenBodiesAndWrongTypesAreBadRequests() throws Exception {
        setup();
        String[] bodies = {"", "{", "[]", "null", "\"texto\"", "{\"amount\":{}}", "{\"amount\":[1]}", "{\"amount\":true}", "{\"sourceAccountId\":123}"};
        for (String path : new String[] {"/api/v1/transfers", "/api/v1/payments", "/api/v1/piggies", "/api/v1/pix/transfers", "/api/v1/accounts"}) {
            for (String body : bodies) {
                int status = send(post(path), body);
                assertThat(status).as("%s corpo=%s", path, body).isBetween(400, 499);
            }
        }
        for (String body : new String[] {"{\"phone\":123}", "{\"email\":[]}", "{\"phone\":\"" + "9".repeat(300) + "\"}"}) {
            assertThat(send(patch("/api/v1/customers/me"), body)).as("PATCH %s", body.substring(0, Math.min(30, body.length()))).isBetween(400, 499);
        }
    }

    @Test
    void absurdPaginationAndFiltersNeverCrash() throws Exception {
        setup();
        String[] queries = {"?page=-1", "?size=0", "?size=-5", "?size=100000", "?page=999999999999", "?page=abc", "?size=1.5", "?page=", "?size=2147483648",
                "?from=ontem", "?month=2026-13", "?category=XYZ", "?direction=SIDEWAYS"};
        String[] lists = {"/api/v1/accounts/" + account + "/statement", "/api/v1/transfers", "/api/v1/payments", "/api/v1/pix/transfers", "/api/v1/pix/charges",
                "/api/v1/pix/schedules", "/api/v1/notifications", "/api/v1/fx/operations", "/api/v1/piggies", "/api/v1/investments", "/api/v1/cards", "/api/v1/credit/cards"};
        for (String list : lists) {
            for (String query : queries) {
                never5xx("GET " + list + query, send(get(list + query), null));
            }
        }
    }

    @Test
    void malformedIdsAreNotFoundOrBadRequest() throws Exception {
        setup();
        for (String id : new String[] {"abc", "123", "00000000-0000-0000-0000-000000000000", "%00", "a".repeat(3000)}) {
            for (String path : new String[] {"/api/v1/accounts/%s", "/api/v1/accounts/%s/balance", "/api/v1/transfers/%s", "/api/v1/cards/%s",
                    "/api/v1/piggies/%s", "/api/v1/investments/%s", "/api/v1/pix/charges/%s"}) {
                int status = send(get(String.format(path, id)), null);
                assertThat(status).as(path + " id=" + id.substring(0, Math.min(10, id.length()))).isBetween(400, 499);
            }
        }
    }

    @Test
    void registrationRefusesInvalidDocumentsPhonesAndEmails() throws Exception {
        for (String document : new String[] {"123", "00000000000", "11111111111", "abc", "1".repeat(14), "12345678901"}) {
            for (String phone : new String[] {"+55", "11999999999", "+5511abc", "+" + "9".repeat(30)}) {
                String body = json("{\"name\":\"Ana Souza\",\"document\":\"%s\",\"email\":\"%s@example.com\",\"phone\":\"%s\",\"password\":\"%s\"}",
                        document, UUID.randomUUID().toString().substring(0, 8), phone, TestUsers.PASSWORD);
                int status = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
                assertThat(status).as("doc=%s phone=%s", document, phone).isBetween(400, 499);
            }
        }
        for (String email : new String[] {"sem-arroba", "@x.com", "a@", "a b@x.com", "a@b", "a@@b.com", "x".repeat(300) + "@x.com"}) {
            String body = json("{\"name\":\"Ana Souza\",\"document\":\"%s\",\"email\":\"%s\",\"phone\":\"+5511999990001\",\"password\":\"%s\"}",
                    TestUsers.cpf(), email, TestUsers.PASSWORD);
            int status = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            assertThat(status).as("email=%s", email.substring(0, Math.min(20, email.length()))).isBetween(400, 499);
        }
    }
}
