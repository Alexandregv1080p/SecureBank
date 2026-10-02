package com.securebank.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Escreve o {@link ErrorResponse} direto na resposta — para filtros (segurança, rate limit), fora do MVC. */
@Component
public class ErrorWriter {

    private final JsonMapper mapper;

    ErrorWriter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(Instant.now(), status.value(), code,
                message, request.getRequestURI(), MDC.get(TraceIdFilter.MDC_KEY), null));
    }
}
