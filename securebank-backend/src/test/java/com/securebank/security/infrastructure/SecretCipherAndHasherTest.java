package com.securebank.security.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherAndHasherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(new SecurityProperties(KEY, null));

    @Test
    void encryptsAndDecryptsWithAFreshIvEachTime() {
        String first = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String second = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(first).isNotEqualTo(second).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(second)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("segredo"));
        bytes[bytes.length - 1] ^= 1; // GCM autentica: um bit alterado invalida tudo
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String other = Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes());
        String encrypted = cipher.encrypt("segredo");

        assertThatThrownBy(() -> new AesGcmSecretCipher(new SecurityProperties(other, null)).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithoutAProperKey() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(new SecurityProperties("", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MFA_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new AesGcmSecretCipher(new SecurityProperties(
                Base64.getEncoder().encodeToString(new byte[16]), null))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void argon2IdHashesAreSaltedVerifiableAndSelfDescribing() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher();

        String a = hasher.hash("Correct-Horse-Battery-9");
        String b = hasher.hash("Correct-Horse-Battery-9");

        assertThat(a).startsWith("{argon2@SpringSecurity_v5_8}$argon2id$").isNotEqualTo(b).doesNotContain("Correct");
        assertThat(hasher.matches("Correct-Horse-Battery-9", a)).isTrue();
        assertThat(hasher.matches("correct-horse-battery-9", a)).isFalse();
    }
}
