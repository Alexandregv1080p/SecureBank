package com.securebank.shared.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Banco fora do ar vira 503 + Retry-After (o cliente tenta de novo), nunca um 500 genérico. */
class GlobalExceptionHandlerTest {

    @RestController
    static class Failing {
        @GetMapping("/tx")
        String tx() {
            throw new CannotCreateTransactionException("no connection");
        }

        @GetMapping("/resource")
        String resource() {
            throw new DataAccessResourceFailureException("connection refused");
        }

        @GetMapping("/timeout")
        String timeout() {
            throw new QueryTimeoutException("too slow");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("bug");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Failing())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void databaseFailuresAreServiceUnavailableWithRetryAfter() throws Exception {
        for (String path : new String[] {"/tx", "/resource", "/timeout"}) {
            mvc.perform(get(path))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "5"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }
    }

    @Test
    void otherFailuresStayGenericInternalErrors() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal error"));
    }
}
