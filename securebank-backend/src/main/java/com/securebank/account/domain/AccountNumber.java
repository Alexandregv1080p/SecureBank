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

    /** Número a partir da sequence do banco: 6+ dígitos e dígito verificador (módulo 11, pesos 2..9 da direita). */
    public static AccountNumber generate(long sequence) {
        if (sequence < 0) {
            throw new InvalidValueException("Sequence must not be negative");
        }
        String digits = String.format("%06d", sequence);
        int sum = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        return new AccountNumber(digits + "-" + (remainder < 2 ? 0 : 11 - remainder));
    }

    @Override
    public String toString() {
        return value;
    }
}
