package com.securebank.security.infrastructure;

import com.securebank.authentication.domain.Digests;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.IdempotencyStore;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.infrastructure.web.ErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotência das operações que mexem em dinheiro (seção 13): mesma {@code Idempotency-Key} + mesmo pedido = mesma
 * resposta, sem executar de novo. Roda DEPOIS da autorização (só quem pode executar reserva chave) e a chave vale por
 * usuário. Não é um bean: é montado na cadeia de segurança.
 */
class IdempotencyFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdempotencyFilter.class);

    static final String HEADER = "Idempotency-Key";
    private static final int MAX_BODY = 64 * 1024;
    private static final List<String> PATHS = List.of("/api/v1/transfers", "/api/v1/payments",
            "/api/v1/accounts/*/deposits", "/api/v1/accounts/*/withdrawals",
            "/api/v1/piggies/*/deposits", "/api/v1/piggies/*/withdrawals", "/api/v1/pix/transfers");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final IdempotencyStore store;
    private final ErrorWriter errors;
    private final BankTime time;

    IdempotencyFilter(IdempotencyStore store, ErrorWriter errors, BankTime time) {
        this.store = store;
        this.errors = errors;
        this.time = time;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!"POST".equals(request.getMethod()) || PATHS.stream().noneMatch(p -> matcher.match(p, request.getRequestURI()))
                || auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getHeader(HEADER);
        try {
            new IdempotencyKey(key);
        } catch (InvalidValueException e) {
            errors.write(request, response, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Header Idempotency-Key is required (8-128 characters of [A-Za-z0-9._-])");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            errors.write(request, response, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request body too large");
            return;
        }

        String scope = auth.getName();
        String fingerprint = Digests.sha256Url(request.getMethod() + " " + request.getRequestURI() + "\n"
                + new String(body, StandardCharsets.UTF_8));
        IdempotencyStore.Claim claim;
        try {
            claim = store.claim(scope, key, fingerprint, time.now());
        } catch (RuntimeException e) {
            // Sem o banco não dá para garantir a idempotência, e mover dinheiro sem essa garantia é pior que recusar.
            log.error("Idempotency store unavailable; rejecting request (fail closed)", e);
            response.setHeader("Retry-After", "5");
            errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Service temporarily unavailable");
            return;
        }

        if (claim instanceof IdempotencyStore.Claim.Replay replay) {
            response.setStatus(replay.status());
            response.setHeader("Idempotency-Replayed", "true");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getOutputStream().write(replay.body() == null ? new byte[0] : replay.body().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (claim instanceof IdempotencyStore.Claim.InProgress) {
            response.setHeader("Retry-After", "1");
            errors.write(request, response, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_IN_PROGRESS",
                    "A request with this Idempotency-Key is still being processed");
            return;
        }
        if (claim instanceof IdempotencyStore.Claim.Mismatch) {
            errors.write(request, response, HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used with a different request");
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(new CachedBodyRequest(request, body), wrapped);
            int status = wrapped.getStatus();
            // Só resultados determinísticos são guardados; conflito de versão (409), limite de taxa e 5xx podem
            // dar certo numa nova tentativa, então a chave é liberada.
            if (status < 500 && status != 409 && status != 429) {
                store.complete(scope, key, status, new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8));
                stored = true;
            }
        } finally {
            if (!stored) {
                try {
                    store.release(scope, key);
                } catch (RuntimeException e) {
                    log.warn("Could not release idempotency key (it expires on its own)", e);
                }
            }
            wrapped.copyBodyToResponse();
        }
    }

    /** Reentrega o corpo já lido (o stream original só pode ser consumido uma vez). */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { /* leitura síncrona */ }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
