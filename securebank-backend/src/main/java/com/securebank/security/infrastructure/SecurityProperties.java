package com.securebank.security.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Segredos e chaves (prefixo {@code securebank.security}). Todos vêm do ambiente, nunca do Git.
 *
 * @param mfaEncryptionKey chave AES-256 em Base64 que cifra os segredos TOTP no banco (obrigatória)
 * @param jwt par de chaves RSA em PEM; se ausente, gera um par efêmero (só desenvolvimento: tokens não sobrevivem a reinício)
 */
@ConfigurationProperties("securebank.security")
public record SecurityProperties(String mfaEncryptionKey, @DefaultValue Jwt jwt) {

    public record Jwt(String privateKey, String publicKey) {

        boolean configured() {
            return privateKey != null && !privateKey.isBlank() && publicKey != null && !publicKey.isBlank();
        }
    }
}
