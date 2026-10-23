package com.securebank.card.infrastructure;

import com.securebank.authentication.application.SecretCipher;
import com.securebank.authentication.domain.Digests;
import com.securebank.card.application.CardSecrets;
import com.securebank.security.infrastructure.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.YearMonth;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * O número é cifrado com o mesmo AES-256-GCM dos segredos de MFA; o CVV sai de um HMAC-SHA256 com a chave do servidor
 * sobre número e validade, então não existe CVV no banco para vazar e quem tem só o número não o calcula.
 */
@Component
class CardSecretsAdapter implements CardSecrets {

    private final SecretCipher cipher;
    private final SecretKeySpec cvvKey;

    CardSecretsAdapter(SecretCipher cipher, SecurityProperties props) {
        this.cipher = cipher;
        this.cvvKey = new SecretKeySpec(Base64.getDecoder().decode(props.mfaEncryptionKey().trim()), "HmacSHA256");
    }

    @Override
    public String encrypt(String number) {
        return cipher.encrypt(number);
    }

    @Override
    public String decrypt(String stored) {
        return cipher.decrypt(stored);
    }

    @Override
    public String fingerprint(String number) {
        return Digests.sha256Url(number);
    }

    @Override
    public String cvv(String number, YearMonth expiry) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cvvKey);
            byte[] digest = mac.doFinal(("cvv|" + number + "|" + expiry).getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0x7f) << 24 | (digest[1] & 0xff) << 16 | (digest[2] & 0xff) << 8 | (digest[3] & 0xff)) % 1000;
            return String.format("%03d", value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not derive the card verification code", e);
        }
    }
}
