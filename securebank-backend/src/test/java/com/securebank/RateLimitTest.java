package com.securebank;

import static com.securebank.TestUsers.bearer;
import static com.securebank.TestUsers.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Limite por requisição nos endpoints sensíveis (login por IP; dinheiro por usuário). */
@SpringBootTest(properties = {
        "securebank.ratelimit.login-per-minute-ip=3",
        "securebank.ratelimit.money-per-minute-user=2"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class RateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clearCounters() {
        Set<String> keys = redis.keys("rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void loginIsLimitedPerIpEvenWithValidCredentials() throws Exception {
        String email = TestUsers.newEmail();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.registerBody(email, TestUsers.PASSWORD))).andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            login(email).andExpect(status().isOk());
        }
        login(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists("Retry-After"));
        assertThat(Long.parseLong(login(email).andReturn().getResponse().getHeader("Retry-After"))).isBetween(1L, 60L);
    }

    @Test
    void paymentsAreLimitedPerUserNotPerIp() throws Exception {
        TestUsers.Login first = TestUsers.registerAndLogin(mvc);
        TestUsers.Login second = TestUsers.registerAndLogin(mvc);
        String firstAccount = openAndFund(first.accessToken());
        String secondAccount = openAndFund(second.accessToken());

        pay(first.accessToken(), firstAccount).andExpect(status().isCreated());
        pay(first.accessToken(), firstAccount).andExpect(status().isCreated());
        pay(first.accessToken(), firstAccount).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // o mesmo IP, outro usuário: cota própria
        pay(second.accessToken(), secondAccount).andExpect(status().isCreated());
    }

    // ---------- helpers ----------

    private ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, TestUsers.PASSWORD)));
    }

    private String openAndFund(String token) throws Exception {
        String account = read(mvc.perform(bearer(token, post("/api/v1/accounts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CHECKING\"}")).andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(bearer(token, post("/api/v1/accounts/" + account + "/deposits")).header("Idempotency-Key", "idem-" + java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"500.00\"}")).andExpect(status().isCreated());
        return account;
    }

    private ResultActions pay(String token, String account) throws Exception {
        return mvc.perform(bearer(token, post("/api/v1/payments")).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .content(String.format("{\"accountId\":\"%s\",\"amount\":\"1.00\","
                        + "\"barcode\":\"34191790010104351004791020150008291070026000\"}", account)));
    }
}
