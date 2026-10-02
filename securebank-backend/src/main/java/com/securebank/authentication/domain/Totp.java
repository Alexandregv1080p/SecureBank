package com.securebank.authentication.domain;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238): HMAC-SHA1, passo de 30 s, 6 dígitos — compatível com Google Authenticator, Authy etc.
 * Aceita o passo atual e um vizinho (±30 s de relógio torto) e nunca aceita um passo já usado (anti-replay).
 */
public final class Totp {

    static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** Segredo novo: 160 bits em Base32. */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    public static long stepOf(Instant time) {
        return Math.floorDiv(time.getEpochSecond(), STEP_SECONDS);
    }

    /** Código de 6 dígitos para um passo (público para testes e para o RFC 6238). */
    public static String code(String base32Secret, long step) {
        return truncate(base32Secret, step, DIGITS);
    }

    /**
     * @param lastUsedStep último passo já consumido por este dispositivo
     * @return o passo que casou (para gravar como usado), ou vazio se o código é inválido ou repetido
     */
    public static OptionalLong verify(String base32Secret, String code, Instant now, long lastUsedStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        long current = stepOf(now);
        OptionalLong match = OptionalLong.empty();
        // compara os três candidatos sempre, em tempo constante, para não vazar qual passo casou
        for (long step = current - 1; step <= current + 1; step++) {
            boolean equal = MessageDigest.isEqual(code(base32Secret, step).getBytes(StandardCharsets.UTF_8),
                    code.getBytes(StandardCharsets.UTF_8));
            if (equal && step > lastUsedStep && match.isEmpty()) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }

    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + encodedIssuer + ":" + encodedAccount + "?secret=" + base32Secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    static String truncate(String base32Secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(Base32.decode(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + digits + "d", binary % POWERS[digits]);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e); // HmacSHA1 é obrigatório em toda JVM
        }
    }
}
