package com.securebank.customer.domain;

import com.securebank.shared.domain.InvalidValueException;
import java.util.regex.Pattern;

/** Telefone em E.164 (ex.: +5511999998888). */
public record Phone(String value) {

    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{9,14}");

    public Phone {
        if (value == null || !E164.matcher(value).matches()) {
            throw new InvalidValueException("Phone must be in E.164 format, e.g. +5511999998888");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
