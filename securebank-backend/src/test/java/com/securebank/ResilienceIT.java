package com.securebank;

import static com.securebank.TestUsers.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;

/**
 * Falha do Redis (ADR-003): os controles de segurança que dependem dele FALHAM FECHADO, e o que não depende continua.
 * Contexto próprio (a propriedade abaixo só existe para não compartilhar o Redis com as outras classes, pois aqui ele é derrubado).
 */
@SpringBootTest(properties = "securebank.test.scenario=redis-down")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ResilienceIT {

    @Autowired MockMvc mvc;
    @Autowired @Qualifier("redis") GenericContainer<?> redis;

    @Test
    void whenRedisIsDownSecurityControlsFailClosed() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        mvc.perform(bearer(login.accessToken(), get("/api/v1/customers/me"))).andExpect(status().isOk());

        redis.stop();

        // sem poder consultar a denylist de sessões, o token deixa de valer (em vez de valer sem checagem)
        mvc.perform(bearer(login.accessToken(), get("/api/v1/customers/me"))).andExpect(status().isUnauthorized());
        // o limite de taxa não pode ser contornado derrubando o Redis: 503, não "sem limite"
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + login.email() + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        // rotas públicas que não dependem do Redis seguem respondendo
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk());
        assertThat(redis.isRunning()).isFalse();
    }
}
