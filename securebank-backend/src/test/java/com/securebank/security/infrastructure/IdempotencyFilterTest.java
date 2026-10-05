package com.securebank.security.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.IdempotencyStore;
import com.securebank.shared.infrastructure.web.ErrorWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;

/** Sem o banco não há como garantir idempotência: a operação de dinheiro é recusada (503), não executada às cegas. */
class IdempotencyFilterTest {

    private final IdempotencyStore store = mock(IdempotencyStore.class);
    private final ErrorWriter errors = mock(ErrorWriter.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final IdempotencyFilter filter = new IdempotencyFilter(store, errors, mock(BankTime.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storeFailureFailsClosedWith503AndNeverRunsTheOperation() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user-1", "n/a", "ROLE_CUSTOMER"));
        when(store.claim(any(), any(), any(), any())).thenThrow(new DataAccessResourceFailureException("db down"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers");
        request.addHeader("Idempotency-Key", "00000000-0000-4000-8000-000000000001");
        request.setContent("{}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(errors).write(any(), any(), eq(HttpStatus.SERVICE_UNAVAILABLE), eq("SERVICE_UNAVAILABLE"), any());
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
    }
}
