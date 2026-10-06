package com.securebank.shared.domain;

import java.util.UUID;

public record PixTransferId(UUID value) {

    public PixTransferId {
        if (value == null) {
            throw new InvalidValueException("PixTransferId is required");
        }
    }

    public static PixTransferId newId() {
        return new PixTransferId(UUID.randomUUID());
    }

    public static PixTransferId of(String value) {
        try {
            return new PixTransferId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid PixTransferId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
