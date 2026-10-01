package com.securebank.customer.domain;

import com.securebank.shared.domain.InvalidValueException;
import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public Email {
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("Invalid email");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
