package com.securebank.security.infrastructure;

import com.securebank.shared.application.RateLimiter;
import com.securebank.shared.infrastructure.web.ErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite por requisição nos endpoints sensíveis (seção 22). Rota anônima é limitada por IP; rota autenticada, por
 * usuário (subject do token). Roda depois da autenticação. Redis fora do ar → 503 (falha fechada).
 * Não é um bean: é montado na cadeia de segurança, senão o Boot o registraria uma segunda vez como filtro de servlet.
 */
class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    record Rule(String name, HttpMethod method, String pattern, int limit, Duration window, boolean perUser) {}

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<Rule> rules;
    private final RateLimiter limiter;
    private final ErrorWriter errors;

    RateLimitFilter(List<Rule> rules, RateLimiter limiter, ErrorWriter errors) {
        this.rules = rules;
        this.limiter = limiter;
        this.errors = errors;
    }

    static List<Rule> rulesFrom(RateLimitProperties p) {
        return List.of(
                new Rule("login", HttpMethod.POST, "/api/v1/auth/login", p.loginPerMinuteIp(), Duration.ofMinutes(1), false),
                new Rule("register", HttpMethod.POST, "/api/v1/auth/register", p.registerPerHourIp(), Duration.ofHours(1), false),
                new Rule("mfa", HttpMethod.POST, "/api/v1/auth/mfa/verify", p.mfaPerMinuteIp(), Duration.ofMinutes(1), false),
                new Rule("refresh", HttpMethod.POST, "/api/v1/auth/refresh", p.refreshPerMinuteIp(), Duration.ofMinutes(1), false),
                new Rule("transfer", HttpMethod.POST, "/api/v1/transfers", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true),
                new Rule("payment", HttpMethod.POST, "/api/v1/payments", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true),
                new Rule("withdraw", HttpMethod.POST, "/api/v1/accounts/*/withdrawals", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true),
                new Rule("piggy-save", HttpMethod.POST, "/api/v1/piggies/*/deposits", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true),
                new Rule("pix-send", HttpMethod.POST, "/api/v1/pix/transfers", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true),
                // consulta de chave revela o nome (mascarado) do dono: limitada para ninguém varrer chaves
                new Rule("pix-refund", HttpMethod.POST, "/api/v1/pix/transfers/*/refund", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true),
                new Rule("pix-lookup", HttpMethod.GET, "/api/v1/pix/keys/lookup", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true),
                new Rule("piggy-redeem", HttpMethod.POST, "/api/v1/piggies/*/withdrawals", p.moneyPerMinuteUser(), Duration.ofMinutes(1), true));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = match(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            RateLimiter.Decision decision = limiter.hit("rl:" + rule.name() + ":" + subject(rule, request),
                    rule.limit(), rule.window());
            if (!decision.allowed()) {
                response.setHeader("Retry-After", String.valueOf(Math.max(1, decision.retryAfter().toSeconds())));
                errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Too many requests. Try again later");
                return;
            }
        } catch (RuntimeException e) {
            log.error("Rate limiter unavailable; rejecting request (fail closed)", e);
            errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Service temporarily unavailable");
            return;
        }
        chain.doFilter(request, response);
    }

    private Rule match(HttpServletRequest request) {
        for (Rule rule : rules) {
            if (rule.method().name().equals(request.getMethod()) && matcher.match(rule.pattern(), request.getRequestURI())) {
                return rule;
            }
        }
        return null;
    }

    private static String subject(Rule rule, HttpServletRequest request) {
        if (rule.perUser()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
                return "u:" + auth.getName();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
