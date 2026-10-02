package com.securebank.shared.infrastructure.web;

import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * O usuário autenticado desta requisição, lido do JWT já validado pelo Spring Security. Os controllers passam o
 * resultado aos casos de uso; nada da camada de aplicação conhece o token.
 */
@Component
public class CurrentUser {

    public UserId userId() {
        return parse(jwt().getSubject(), UserId::of);
    }

    /** Cliente do usuário. Equipe (sem Customer) não tem: 403, nunca um id inventado. */
    public CustomerId customerId() {
        String cid = jwt().getClaimAsString("cid");
        if (cid == null) {
            throw ApplicationException.forbidden();
        }
        return parse(cid, CustomerId::of);
    }

    public SessionId sessionId() {
        return parse(jwt().getClaimAsString("sid"), SessionId::of);
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw ApplicationException.unauthenticated();
    }

    private static <T> T parse(String value, java.util.function.Function<String, T> factory) {
        try {
            return factory.apply(value);
        } catch (InvalidValueException | NullPointerException e) {
            throw ApplicationException.unauthenticated();
        }
    }
}
