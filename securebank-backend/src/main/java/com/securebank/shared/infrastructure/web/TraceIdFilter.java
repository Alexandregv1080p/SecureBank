package com.securebank.shared.infrastructure.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Expõe o {@code traceId} de cada requisição no MDC (logs), no header de resposta e no corpo do erro.
 * Com tracing ativo é o id do span do OpenTelemetry (o mesmo que aparece no Jaeger e nos logs do Loki); o filtro roda
 * depois do de observação do Spring, que abre o span. Sem tracing (testes), cai no id do cliente, aceito só com
 * formato seguro (evita log injection), ou num UUID novo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TraceIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Trace-Id";
    static final String MDC_KEY = "traceId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final ObjectProvider<Tracer> tracer;

    TraceIdFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = fromSpan();
        if (traceId == null) {
            String incoming = request.getHeader(HEADER);
            traceId = incoming != null && SAFE.matcher(incoming).matches()
                    ? incoming
                    : UUID.randomUUID().toString().replace("-", "");
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }

    private String fromSpan() {
        Tracer t = tracer.getIfAvailable();
        Span span = t == null ? null : t.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
