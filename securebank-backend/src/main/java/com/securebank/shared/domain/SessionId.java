package com.securebank.shared.domain;

import java.util.UUID;

public record SessionId(UUID value) {

    public SessionId {
        if (value == null) {
            throw new InvalidValueException("SessionId is required");
        }
    }

    public static SessionId newId() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId of(String value) {
        try {
            return new SessionId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid SessionId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
