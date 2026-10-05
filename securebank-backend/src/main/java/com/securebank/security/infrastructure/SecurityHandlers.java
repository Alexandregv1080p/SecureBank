package com.securebank.security.infrastructure;

import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.shared.infrastructure.web.ErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401 e 403 no formato de erro único. O motivo da recusa do token (expirado, assinatura inválida, revogado...) não
 * vai para o cliente. Acesso negado a quem está autenticado é auditado (ACCESS_DENIED), em transação própria.
 */
@Component
class SecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityHandlers.class);

    private final ErrorWriter errors;
    private final AuditService audit;

    SecurityHandlers(ErrorWriter errors, AuditService audit) {
        this.errors = errors;
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        if (dependencyFailure(e)) {
            // Não deu para validar a sessão (Redis fora): o token não é aceito, mas o cliente não deve achar que
            // a sessão acabou. 503 + Retry-After pede nova tentativa em vez de novo login.
            response.setHeader("Retry-After", "5");
            errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Service temporarily unavailable");
            return;
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        errors.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }

    private static boolean dependencyFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof JwtValidationException v
                    && v.getErrors().stream().anyMatch(err -> JwtConfig.SERVER_ERROR.equals(err.getErrorCode()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        try {
            audit.recordIndependently(AuditEntry.of(AuditEvent.ACCESS_DENIED)
                    .detail(request.getMethod() + " " + request.getRequestURI()));
        } catch (RuntimeException failure) {
            log.error("Could not audit access denied", failure); // negar o acesso não depende da auditoria
        }
        errors.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }
}
