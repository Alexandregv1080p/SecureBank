package com.securebank.shared.infrastructure.web;

import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Quem está chamando. PROVISÓRIO até a Fase 4 (JWT): lê o id do header {@code X-Customer-Id}, que qualquer cliente
 * pode forjar — por isso só funciona com {@code securebank.devidentity.enabled=true} (desligado por padrão: sem
 * isso toda rota protegida responde 401). A Fase 4 troca o corpo deste método pelo subject do token.
 */
@Component
public class CurrentCustomer {

    private static final Logger log = LoggerFactory.getLogger(CurrentCustomer.class);
    static final String HEADER = "X-Customer-Id";

    private final boolean devIdentityEnabled;

    CurrentCustomer(@Value("${securebank.devidentity.enabled:false}") boolean devIdentityEnabled) {
        this.devIdentityEnabled = devIdentityEnabled;
        if (devIdentityEnabled) {
            log.warn("securebank.devidentity.enabled=true: identidade vem de um header forjável. APENAS DESENVOLVIMENTO.");
        }
    }

    public CustomerId id() {
        if (!devIdentityEnabled) {
            throw ApplicationException.unauthenticated();
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String header = request.getHeader(HEADER);
        if (header == null) {
            throw ApplicationException.unauthenticated();
        }
        try {
            return CustomerId.of(header);
        } catch (InvalidValueException e) {
            throw ApplicationException.unauthenticated();
        }
    }
}
