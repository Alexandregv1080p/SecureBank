package com.securebank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ApiDocsTest {

    @Autowired MockMvc mvc;

    @Test
    void openApiDescribesTheCoreEndpointsAndIdempotencyHeader() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.parameters[?(@.name=='Idempotency-Key')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}/statement']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments']").exists());
    }
}
