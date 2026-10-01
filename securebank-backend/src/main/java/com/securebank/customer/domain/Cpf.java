package com.securebank.customer.domain;

import com.securebank.shared.domain.InvalidValueException;

/** CPF com dígitos verificadores válidos, guardado só com dígitos. */
public record Cpf(String value) {

    public Cpf {
        String digits = value == null ? "" : value.replace(".", "").replace("-", "");
        if (!digits.matches("\\d{11}") || digits.chars().distinct().count() == 1
                || digit(digits, 9) != digits.charAt(9) - '0' || digit(digits, 10) != digits.charAt(10) - '0') {
            throw new InvalidValueException("Invalid CPF");
        }
        value = digits;
    }

    private static int digit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }

    /** Para logs e auditoria: o CPF completo é dado pessoal. */
    public String masked() {
        return "***." + value.substring(3, 6) + ".***-**";
    }

    @Override
    public String toString() {
        return masked();
    }
}
