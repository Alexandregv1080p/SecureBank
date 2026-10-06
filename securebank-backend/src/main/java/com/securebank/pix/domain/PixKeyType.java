package com.securebank.pix.domain;

import com.securebank.customer.domain.Cpf;
import com.securebank.shared.domain.InvalidValueException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public enum PixKeyType {
    CPF, EMAIL, PHONE, RANDOM;

    private static final Pattern EMAIL_FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{9,14}");

    /** Chave canônica deste tipo (é o que se grava e se compara), ou {@link InvalidValueException}. */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidValueException("Pix key is required");
        }
        String value = raw.trim();
        switch (this) {
            case CPF -> {
                String digits = digitsOf(value);
                new Cpf(digits); // valida os dígitos verificadores
                return digits;
            }
            case EMAIL -> {
                String email = value.toLowerCase();
                if (email.length() > 77 || !EMAIL_FORMAT.matcher(email).matches()) {
                    throw new InvalidValueException("Invalid Pix e-mail key");
                }
                return email;
            }
            case PHONE -> {
                if (!E164.matcher(value).matches()) {
                    throw new InvalidValueException("Invalid Pix phone key (use +5511999998888)");
                }
                return value;
            }
            default -> {
                try {
                    return UUID.fromString(value).toString();
                } catch (IllegalArgumentException e) {
                    throw new InvalidValueException("Invalid random Pix key");
                }
            }
        }
    }

    /**
     * Interpretações possíveis do que o usuário digitou, da mais provável para a menos: "11999998888" pode ser um
     * telefone ou (se tiver 11 dígitos e dígitos verificadores certos) um CPF, então quem busca tenta todas.
     */
    public static List<Candidate> candidates(String raw) {
        List<Candidate> found = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return found;
        }
        String text = raw.trim();
        if (text.contains("@")) {
            add(found, EMAIL, text);
            return found;
        }
        if (text.contains("-") && text.length() == 36) {
            add(found, RANDOM, text);
            return found;
        }
        String digits = digitsOf(text);
        if (digits.length() == 11 && !text.startsWith("+")) {
            add(found, CPF, digits);
        }
        if (text.startsWith("+")) {
            add(found, PHONE, "+" + digits);
        } else if (digits.length() == 10 || digits.length() == 11) {
            add(found, PHONE, "+55" + digits);
        } else if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith("55")) {
            add(found, PHONE, "+" + digits);
        }
        return found;
    }

    public record Candidate(PixKeyType type, String value) {}

    private static void add(List<Candidate> list, PixKeyType type, String raw) {
        try {
            list.add(new Candidate(type, type.normalize(raw)));
        } catch (InvalidValueException ignored) {
            // essa interpretação não vale (ex.: 11 dígitos que não são um CPF)
        }
    }

    private static String digitsOf(String value) {
        StringBuilder digits = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
