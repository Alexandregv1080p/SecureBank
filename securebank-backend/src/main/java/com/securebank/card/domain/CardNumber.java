package com.securebank.card.domain;

import com.securebank.shared.domain.InvalidValueException;
import java.security.SecureRandom;

/**
 * Número de cartão fictício: 16 dígitos que começam com 9 (faixa que nenhuma bandeira usa, então nunca coincide com um
 * cartão real) e fecham no dígito verificador de Luhn.
 */
public final class CardNumber {

    public static final int LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CardNumber() {}

    public static String generate() {
        StringBuilder digits = new StringBuilder("9");
        while (digits.length() < LENGTH - 1) {
            digits.append(RANDOM.nextInt(10));
        }
        return digits.append(luhnDigit(digits.toString())).toString();
    }

    public static boolean isValid(String number) {
        if (number == null || number.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < number.length(); i++) {
            if (!Character.isDigit(number.charAt(i))) {
                return false;
            }
        }
        return luhnDigit(number.substring(0, LENGTH - 1)) == number.charAt(LENGTH - 1) - '0';
    }

    public static String last4(String number) {
        if (!isValid(number)) {
            throw new InvalidValueException("Invalid card number");
        }
        return number.substring(LENGTH - 4);
    }

    /** "9999 99** **** 1234": só os 4 últimos aparecem fora da tela de detalhes. */
    public static String masked(String last4) {
        return "**** **** **** " + last4;
    }

    /** Dígito de Luhn para o prefixo: dobra um sim, um não, a partir do último dígito do prefixo. */
    static int luhnDigit(String prefix) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = prefix.length() - 1; i >= 0; i--) {
            int d = prefix.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }
}
