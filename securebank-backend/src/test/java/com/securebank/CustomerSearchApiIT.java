package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Busca de clientes pela equipe: por nome, e-mail, telefone e CPF completo, sempre auditada e sem vazar o que foi digitado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CustomerSearchApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private MockHttpServletRequestBuilder search(String token, String q) {
        return TestUsers.bearer(token, get("/api/v1/admin/customers")).param("q", q);
    }

    private Map<String, Object> row(String email) {
        return jdbc.queryForMap("select id, name, document, phone from customers where email = ?", email);
    }

    @Test
    void findsACustomerByEmailAndShowsTheCpfMasked() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(search(admin, customer.email().substring(0, customer.email().indexOf('@')).toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(customer.customerId()))
                .andExpect(jsonPath("$.items[0].email").value(customer.email()))
                .andExpect(jsonPath("$.items[0].document").value(org.hamcrest.Matchers.containsString("*")));
    }

    @Test
    void findsByFullCpfButNeverByAPrefix() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        String admin = TestUsers.adminToken(mvc);
        String cpf = (String) row(customer.email()).get("document");

        mvc.perform(search(admin, cpf))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(customer.customerId()));
        String formatted = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
        mvc.perform(search(admin, formatted)).andExpect(jsonPath("$.totalElements").value(1)); // aceita a máscara
        mvc.perform(search(admin, cpf.substring(0, 10))).andExpect(jsonPath("$.totalElements").value(0)); // sem prefixo
    }

    @Test
    void findsByPhoneDigitsAndByNameFragment() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        String admin = TestUsers.adminToken(mvc);
        Map<String, Object> row = row(customer.email());
        String phone = ((String) row.get("phone")).replaceAll("\\D", "");

        mvc.perform(search(admin, phone.substring(phone.length() - 8)))
                .andExpect(jsonPath("$.items[*].id").value(hasItem(customer.customerId())));
        String name = (String) row.get("name");
        mvc.perform(search(admin, name.toLowerCase().substring(0, 4)))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void wildcardsAreLiteralTextSoNobodyDumpsTheWholeBase() throws Exception {
        TestUsers.registerAndLogin(mvc);
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(search(admin, "%%%")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(search(admin, "___")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(search(admin, "%@%")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(search(admin, "\\\\\\")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void tooShortOrMissingQueriesAreRefused() throws Exception {
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(search(admin, "ab")).andExpect(status().isBadRequest());
        mvc.perform(search(admin, "  a  ")).andExpect(status().isBadRequest());
        mvc.perform(search(admin, "12")).andExpect(status().isBadRequest());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/customers"))).andExpect(status().isBadRequest());
        mvc.perform(search(admin, "abc").param("size", "1000")).andExpect(status().isBadRequest());
    }

    @Test
    void resultsArePagedAndOrderedByName() throws Exception {
        TestUsers.registerAndLogin(mvc);
        TestUsers.registerAndLogin(mvc);
        TestUsers.registerAndLogin(mvc);
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(search(admin, "example.com").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void everySearchIsAuditedWithoutWhatWasTyped() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        String admin = TestUsers.adminToken(mvc);
        String typed = customer.email();

        mvc.perform(search(admin, typed)).andExpect(status().isOk());

        String detail = jdbc.queryForObject("select detail from audit_logs where event = 'CUSTOMER_SEARCHED'"
                + " order by occurred_at desc limit 1", String.class);
        assertThat(detail).isEqualTo("email: 1 resultado(s)");
        assertThat(detail).doesNotContain(typed);
    }

    @Test
    void listsACustomersAccountsWithoutBalances() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        for (String type : new String[] {"CHECKING", "SAVINGS"}) {
            mvc.perform(TestUsers.bearer(customer.accessToken(), post("/api/v1/accounts"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"" + type + "\"}")).andExpect(status().isCreated());
        }
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/customers/" + customer.customerId() + "/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].type").value(hasItem("SAVINGS")))
                .andExpect(jsonPath("$[0].customerId").value(customer.customerId()))
                .andExpect(jsonPath("$[0].balance").doesNotExist());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/customers/" + UUID.randomUUID() + "/accounts")))
                .andExpect(status().isNotFound());
    }

    @Test
    void supportMaySearchButCustomersAndAnonymousMayNot() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        String support = TestUsers.staffToken(mvc, "SUPPORT");

        mvc.perform(search(support, "example.com")).andExpect(status().isOk());
        mvc.perform(TestUsers.bearer(support, get("/api/v1/admin/customers/" + customer.customerId() + "/accounts")))
                .andExpect(status().isOk());
        mvc.perform(search(customer.accessToken(), "example.com")).andExpect(status().isForbidden());
        mvc.perform(TestUsers.bearer(customer.accessToken(), get("/api/v1/admin/customers/" + customer.customerId() + "/accounts")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/customers").param("q", "example.com")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSearchWithNoMatchIsAnEmptyPageNotAnError() throws Exception {
        String admin = TestUsers.adminToken(mvc);

        mvc.perform(search(admin, "ninguem-com-este-nome-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem("x"))));
    }
}
