package com.securebank.shared.domain;

import java.util.regex.Pattern;

/** Chave enviada pelo cliente no header {@code Idempotency-Key} (seção 13). */
public record IdempotencyKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._-]{8,128}");

    public IdempotencyKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("Idempotency key must have 8-128 characters of [A-Za-z0-9._-]");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
