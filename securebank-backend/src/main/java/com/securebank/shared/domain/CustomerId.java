package com.securebank.shared.domain;

import java.util.UUID;

public record CustomerId(UUID value) {

    public CustomerId {
        if (value == null) {
            throw new InvalidValueException("CustomerId is required");
        }
    }

    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(String value) {
        try {
            return new CustomerId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid CustomerId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
