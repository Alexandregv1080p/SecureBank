package com.securebank.authentication.domain;

import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.InvalidValueException;
import java.util.Locale;
import java.util.Set;

/**
 * Política de senha no estilo NIST 800-63B: o que importa é o tamanho e não ser previsível, não regras de
 * composição. Máximo evita que uma senha gigante vire vetor de DoS no hashing.
 *
 * <p>"Previsível" aqui é: está numa lista de senhas comuns; tem poucos caracteres diferentes; só tem dígitos; é uma
 * unidade curta repetida; ou mais da metade dela é sequência ("abcdef", "654321"), fila do teclado ("qwerty") ou palavra
 * óbvia ("senha", "password"). A mesma regra existe no web e no Android (lib/validation.ts e Validation.kt), com os
 * mesmos casos de teste, para o erro aparecer antes do envio.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of("password1234", "123456789012", "qwertyuiop12", "1234567890ab",
            "senha1234567", "senhasenha123", "passwordpassword", "letmein12345", "iloveyou1234", "admin1234567",
            "welcome12345", "changeme1234", "abcdefghijkl", "111111111111", "000000000000", "mudar1234567",
            "brasil123456", "123456789abc", "qazwsxedcrfv", "1q2w3e4r5t6y", "q1w2e3r4t5y6");

    private static final String[] BAD_WORDS = {"password", "passwd", "senha", "admin", "qwerty", "letmein", "welcome",
            "iloveyou", "changeme", "master", "login", "brasil", "futebol", "mudar", "secure"};

    private static final String[] KEYBOARD_ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm", "qazwsxedcrfv"};

    private PasswordPolicy() {}

    public static void validate(String password, Email email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new InvalidValueException("Password must have " + MIN_LENGTH + " to " + MAX_LENGTH + " characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (isTooEasy(lower)) {
            throw new InvalidValueException("Password is too easy to guess");
        }
        String localPart = email.value().substring(0, email.value().indexOf('@'));
        if (localPart.length() >= 4 && lower.contains(localPart)) {
            throw new InvalidValueException("Password must not contain your email");
        }
    }

    /** @param lower senha em minúsculas */
    static boolean isTooEasy(String lower) {
        if (COMMON.contains(lower) || lower.chars().distinct().count() < 6) {
            return true;
        }
        StringBuilder alnum = new StringBuilder();
        lower.chars().filter(Character::isLetterOrDigit).forEach(c -> alnum.append((char) c));
        String s = alnum.toString();
        if (s.isEmpty() || s.chars().allMatch(Character::isDigit) || repeatsAUnit(lower)) {
            return true;
        }
        // mais da metade é previsível: sequência, fila do teclado ou palavra óbvia
        boolean[] covered = new boolean[s.length()];
        markSequences(s, covered);
        markKeyboardRuns(s, covered);
        for (String word : BAD_WORDS) {
            for (int i = s.indexOf(word); i >= 0; i = s.indexOf(word, i + 1)) {
                for (int k = i; k < i + word.length(); k++) {
                    covered[k] = true;
                }
            }
        }
        int count = 0;
        for (boolean c : covered) {
            if (c) {
                count++;
            }
        }
        return count * 2 > s.length();
    }

    /** "abcabcabc", "senhasenhasenha": a senha inteira é uma unidade curta repetida 2 vezes ou mais. */
    private static boolean repeatsAUnit(String s) {
        int n = s.length();
        for (int unit = 1; unit <= n / 2; unit++) {
            if (n % unit == 0 && s.substring(0, unit).repeat(n / unit).equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** Corridas de 4+ caracteres consecutivos, crescentes, decrescentes ou iguais ("abcd", "4321", "zzzz"). */
    private static void markSequences(String s, boolean[] covered) {
        for (int step : new int[] {1, -1, 0}) {
            int i = 0;
            while (i < s.length()) {
                int j = i;
                while (j + 1 < s.length() && s.charAt(j + 1) - s.charAt(j) == step) {
                    j++;
                }
                if (j - i + 1 >= 4) {
                    for (int k = i; k <= j; k++) {
                        covered[k] = true;
                    }
                }
                i = j + 1;
            }
        }
    }

    /** Trechos de 4+ caracteres de uma fila do teclado, nos dois sentidos ("qwer", "rewq"). */
    private static void markKeyboardRuns(String s, boolean[] covered) {
        for (String row : KEYBOARD_ROWS) {
            for (String r : new String[] {row, new StringBuilder(row).reverse().toString()}) {
                for (int i = 0; i < s.length(); i++) {
                    int len = 0;
                    while (i + len < s.length() && r.contains(s.substring(i, i + len + 1))) {
                        len++;
                    }
                    if (len >= 4) {
                        for (int k = i; k < i + len; k++) {
                            covered[k] = true;
                        }
                    }
                }
            }
        }
    }
}
