package com.securebank.shared.domain;

import java.util.UUID;

public record PaymentId(UUID value) {

    public PaymentId {
        if (value == null) {
            throw new InvalidValueException("PaymentId is required");
        }
    }

    public static PaymentId newId() {
        return new PaymentId(UUID.randomUUID());
    }

    public static PaymentId of(String value) {
        try {
            return new PaymentId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid PaymentId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
