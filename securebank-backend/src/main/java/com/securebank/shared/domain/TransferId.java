package com.securebank.shared.domain;

import java.util.UUID;

public record TransferId(UUID value) {

    public TransferId {
        if (value == null) {
            throw new InvalidValueException("TransferId is required");
        }
    }

    public static TransferId newId() {
        return new TransferId(UUID.randomUUID());
    }

    public static TransferId of(String value) {
        try {
            return new TransferId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid TransferId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
