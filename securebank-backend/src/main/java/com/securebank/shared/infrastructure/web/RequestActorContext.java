package com.securebank.shared.infrastructure.web;

import com.securebank.shared.application.Actor;
import com.securebank.shared.application.ActorContext;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Monta o {@link Actor} a partir da requisição HTTP corrente e do JWT (se houver). Fora de requisição: anônimo. */
@Component
class RequestActorContext implements ActorContext {

    @Override
    public Actor current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Actor.none();
        }
        HttpServletRequest request = attributes.getRequest();
        UserId userId = null;
        CustomerId customerId = null;
        SessionId sessionId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            try {
                userId = UserId.of(jwt.getSubject());
                customerId = jwt.getClaimAsString("cid") == null ? null : CustomerId.of(jwt.getClaimAsString("cid"));
                sessionId = jwt.getClaimAsString("sid") == null ? null : SessionId.of(jwt.getClaimAsString("sid"));
            } catch (InvalidValueException ignored) {
                // token validado pelo servidor; se algum claim vier malformado, trata como anônimo
            }
        }
        return new Actor(userId, customerId, sessionId, request.getRemoteAddr(), request.getHeader("User-Agent"),
                MDC.get(TraceIdFilter.MDC_KEY));
    }
}
