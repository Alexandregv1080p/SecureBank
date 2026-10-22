package com.securebank.payment.domain;

/**
 * Dígitos verificadores de boleto (padrão Febraban): código de barras de 44 dígitos, linha digitável de boleto bancário
 * (47) e de arrecadação/convênio (48). Pega erro de digitação antes de o pagamento sair da conta.
 */
public final class BoletoCode {

    private BoletoCode() {}

    public static boolean isValid(String code) {
        if (code == null || !code.matches("\\d{44}|\\d{47}|\\d{48}") || code.chars().distinct().count() == 1) {
            return false;
        }
        return switch (code.length()) {
            case 47 -> bankLine(code);
            case 48 -> convenioLine(code);
            default -> code.charAt(0) == '8' ? convenioBar(code) : bankBar(code);
        };
    }

    /** Três campos com módulo 10 e o dígito geral (módulo 11) do código de barras que a linha representa. */
    private static boolean bankLine(String c) {
        if (mod10(c.substring(0, 9)) != digit(c, 9) || mod10(c.substring(10, 20)) != digit(c, 20)
                || mod10(c.substring(21, 31)) != digit(c, 31)) {
            return false;
        }
        String bar = c.substring(0, 4) + c.substring(32, 47) + c.substring(4, 9) + c.substring(10, 20) + c.substring(21, 31);
        return bankBar(bar);
    }

    private static boolean bankBar(String c) {
        return mod11(c.substring(0, 4) + c.substring(5), false) == digit(c, 4);
    }

    /** Quatro blocos de 11 dígitos + verificador; o terceiro dígito diz se é módulo 10 (6, 7) ou 11. */
    private static boolean convenioLine(String c) {
        boolean ten = c.charAt(2) == '6' || c.charAt(2) == '7';
        for (int i = 0; i < 48; i += 12) {
            String block = c.substring(i, i + 11);
            if ((ten ? mod10(block) : mod11(block, true)) != digit(c, i + 11)) {
                return false;
            }
        }
        return true;
    }

    private static boolean convenioBar(String c) {
        boolean ten = c.charAt(2) == '6' || c.charAt(2) == '7';
        String rest = c.substring(0, 3) + c.substring(4);
        return (ten ? mod10(rest) : mod11(rest, true)) == digit(c, 3);
    }

    private static int digit(String s, int i) {
        return s.charAt(i) - '0';
    }

    /** Pesos 2,1,2,1... da direita; soma os algarismos de cada produto. */
    private static int mod10(String s) {
        int total = 0;
        int weight = 2;
        for (int i = s.length() - 1; i >= 0; i--) {
            int p = digit(s, i) * weight;
            total += p / 10 + p % 10;
            weight = weight == 2 ? 1 : 2;
        }
        return (10 - total % 10) % 10;
    }

    /** Pesos 2..9 da direita. Banco: resto que dá 0, 10 ou 11 vira 1. Convênio: resto 0 ou 1 vira 0. */
    private static int mod11(String s, boolean convenio) {
        int total = 0;
        int weight = 2;
        for (int i = s.length() - 1; i >= 0; i--) {
            total += digit(s, i) * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int rest = total % 11;
        if (convenio) {
            return rest <= 1 ? 0 : 11 - rest;
        }
        int dv = 11 - rest;
        return dv == 0 || dv == 10 || dv == 11 ? 1 : dv;
    }
}
