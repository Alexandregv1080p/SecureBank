package com.securebank.authentication.domain;

import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.InvalidValueException;
import java.util.Locale;
import java.util.Set;

/**
 * Política de senha no estilo NIST 800-63B: o que importa é o tamanho e não ser previsível, não regras de
 * composição. Máximo evita que uma senha gigante vire vetor de DoS no hashing.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of("password1234", "123456789012", "qwertyuiop12", "1234567890ab",
            "senha1234567", "senhasenha123", "passwordpassword", "letmein12345", "iloveyou1234", "admin1234567",
            "welcome12345", "changeme1234", "abcdefghijkl", "111111111111", "000000000000");

    private PasswordPolicy() {}

    public static void validate(String password, Email email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new InvalidValueException("Password must have " + MIN_LENGTH + " to " + MAX_LENGTH + " characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || lower.chars().distinct().count() < 5) {
            throw new InvalidValueException("Password is too easy to guess");
        }
        String localPart = email.value().substring(0, email.value().indexOf('@'));
        if (localPart.length() >= 4 && lower.contains(localPart)) {
            throw new InvalidValueException("Password must not contain your email");
        }
    }
}
