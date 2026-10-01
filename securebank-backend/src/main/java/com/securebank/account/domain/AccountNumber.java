package com.securebank.account.domain;

import com.securebank.shared.domain.InvalidValueException;
import java.util.regex.Pattern;

/** Formato {@code 123456-7}. A geração (sequence + dígito) é responsabilidade da infraestrutura. */
public record AccountNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("\\d{6,12}-\\d");

    public AccountNumber {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("Account number must look like 123456-7");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
