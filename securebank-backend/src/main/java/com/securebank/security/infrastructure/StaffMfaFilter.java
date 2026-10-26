package com.securebank.security.infrastructure;

import com.securebank.shared.infrastructure.web.ErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A equipe só opera com a verificação em duas etapas: uma sessão de equipe sem o fator OTP (claim amr) alcança apenas
 * a segurança da própria conta (ligar o MFA, trocar a senha, ver sessões) e o logout. Depois de ligar, a pessoa entra
 * de novo com o código e a sessão nova já nasce com OTP. Não é um bean: é montado na cadeia de segurança.
 */
class StaffMfaFilter extends OncePerRequestFilter {

    private final ErrorWriter errors;

    StaffMfaFilter(ErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (needsMfa() && !allowedWithoutMfa(request.getRequestURI())) {
            errors.write(request, response, HttpStatus.FORBIDDEN, "MFA_REQUIRED",
                    "Turn on two-step verification and sign in again to use the staff panel");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean needsMfa() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
            return false;
        }
        Jwt jwt = auth.getToken();
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean staff = roles != null && !roles.isEmpty() && !roles.contains("CUSTOMER");
        List<String> amr = jwt.getClaimAsStringList("amr");
        return staff && (amr == null || !amr.contains("otp"));
    }

    private static boolean allowedWithoutMfa(String path) {
        return path.startsWith("/api/v1/security/") || path.equals("/api/v1/auth/logout");
    }
}
