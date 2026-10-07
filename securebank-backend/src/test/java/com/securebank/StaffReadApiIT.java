package com.securebank;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;

/** Leituras da equipe que alimentam o painel: conta, limites, cotações e lista da equipe. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class StaffReadApiIT {

    @Autowired MockMvc mvc;

    private String openAccount(String customerToken) throws Exception {
        return JsonPath.read(mvc.perform(TestUsers.bearer(customerToken, post("/api/v1/accounts"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"SAVINGS\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void anAdminSeesAnyAccountWithoutItsBalance() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        String account = openAccount(customer.accessToken());

        mvc.perform(TestUsers.bearer(TestUsers.adminToken(mvc), get("/api/v1/admin/accounts/" + account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account))
                .andExpect(jsonPath("$.customerId").value(customer.customerId()))
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.branch").value("0001"))
                .andExpect(jsonPath("$.balance").doesNotExist());
        mvc.perform(TestUsers.bearer(TestUsers.adminToken(mvc), get("/api/v1/admin/accounts/" + UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminSeesTheConfiguredLimitsAndTodaysUse() throws Exception {
        String account = openAccount(TestUsers.registerAndLogin(mvc).accessToken());

        mvc.perform(TestUsers.bearer(TestUsers.adminToken(mvc), get("/api/v1/admin/accounts/" + account + "/limits")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.type=='PIX')].perOperation.amount").value("5000.00"))
                .andExpect(jsonPath("$[?(@.type=='FX')].daily.amount").value("20000.00"))
                .andExpect(jsonPath("$[?(@.type=='WITHDRAW')].usedToday.amount").value("0.00"));
    }

    @Test
    void anAdminListsTheFxRatesAndTheStaffButNeverCustomers() throws Exception {
        String admin = TestUsers.adminToken(mvc);
        TestUsers.staffToken(mvc, "SUPPORT"); // garante ao menos um usuário da equipe além do admin
        var customer = TestUsers.registerAndLogin(mvc);

        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/fx/rates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].currency").value(hasItem("USD")))
                .andExpect(jsonPath("$[?(@.currency=='USD')].buyRate").exists());
        mvc.perform(TestUsers.bearer(admin, get("/api/v1/admin/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role").value(hasItem("ADMIN")))
                .andExpect(jsonPath("$[*].role").value(hasItem("SUPPORT")))
                .andExpect(jsonPath("$[*].role").value(not(hasItem("CUSTOMER"))))
                .andExpect(jsonPath("$[*].email").value(not(hasItem(customer.email()))))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void supportAndCustomersCannotUseTheAdminReads() throws Exception {
        var customer = TestUsers.registerAndLogin(mvc);
        String account = openAccount(customer.accessToken());
        String support = TestUsers.staffToken(mvc, "SUPPORT");

        for (String path : new String[] {"/api/v1/admin/accounts/" + account, "/api/v1/admin/accounts/" + account + "/limits",
                "/api/v1/admin/fx/rates", "/api/v1/admin/users"}) {
            mvc.perform(TestUsers.bearer(support, get(path))).andExpect(status().isForbidden());
            mvc.perform(TestUsers.bearer(customer.accessToken(), get(path))).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
