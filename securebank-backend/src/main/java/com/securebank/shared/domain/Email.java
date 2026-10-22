package com.securebank.shared.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private static final String ATOM = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+";
    /** Cadastro: parte local sem ponto no começo, no fim ou dobrado; domínio em rótulos de letras, dígitos e hífen e TLD de letras. */
    private static final Pattern STRICT = Pattern.compile(
            ATOM + "(\\." + ATOM + ")*@([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}");

    public Email {
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("Invalid email");
        }
    }

    /**
     * E-mail digitado agora (cadastro, troca de contato): formato estrito. O construtor continua tolerante porque também
     * reconstitui e-mails já gravados e confere o login, e uma regra nova não pode trancar quem já tem conta.
     */
    public static Email forInput(String raw) {
        Email email = new Email(raw);
        if (email.value.indexOf('@') > 64 || !STRICT.matcher(email.value).matches()) {
            throw new InvalidValueException("Invalid email");
        }
        return email;
    }

    @Override
    public String toString() {
        return value;
    }
}
