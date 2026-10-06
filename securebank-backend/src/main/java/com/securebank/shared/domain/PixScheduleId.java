package com.securebank.shared.domain;

import java.util.UUID;

public record PixScheduleId(UUID value) {

    public PixScheduleId {
        if (value == null) {
            throw new InvalidValueException("PixScheduleId is required");
        }
    }

    public static PixScheduleId newId() {
        return new PixScheduleId(UUID.randomUUID());
    }

    public static PixScheduleId of(String value) {
        try {
            return new PixScheduleId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid PixScheduleId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
