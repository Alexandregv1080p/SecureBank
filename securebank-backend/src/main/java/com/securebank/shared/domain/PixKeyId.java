package com.securebank.shared.domain;

import java.util.UUID;

public record PixKeyId(UUID value) {

    public PixKeyId {
        if (value == null) {
            throw new InvalidValueException("PixKeyId is required");
        }
    }

    public static PixKeyId newId() {
        return new PixKeyId(UUID.randomUUID());
    }

    public static PixKeyId of(String value) {
        try {
            return new PixKeyId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid PixKeyId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
