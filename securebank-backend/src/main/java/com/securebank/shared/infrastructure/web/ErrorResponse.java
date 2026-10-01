package com.securebank.shared.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Formato único de erro da API (seção 32). Nunca carrega stack trace, SQL, token ou credencial. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path, String traceId,
        List<FieldViolation> errors) {

    public record FieldViolation(String field, String message) {}
}
