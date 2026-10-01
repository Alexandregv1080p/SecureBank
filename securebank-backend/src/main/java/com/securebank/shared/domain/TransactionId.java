package com.securebank.shared.domain;

import java.util.UUID;

public record TransactionId(UUID value) {

    public TransactionId {
        if (value == null) {
            throw new InvalidValueException("TransactionId is required");
        }
    }

    public static TransactionId newId() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(String value) {
        try {
            return new TransactionId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid TransactionId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
