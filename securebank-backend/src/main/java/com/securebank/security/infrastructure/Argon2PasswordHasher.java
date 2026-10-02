package com.securebank.security.infrastructure;

import com.securebank.authentication.application.PasswordHasher;
import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id (vencedor do PHC, recomendação OWASP) com sal aleatório. O hash guardado começa com o id do algoritmo,
 * então trocar parâmetros/algoritmo no futuro não invalida senhas existentes.
 */
@Component
class Argon2PasswordHasher implements PasswordHasher {

    private static final String ID = "argon2@SpringSecurity_v5_8";

    private final PasswordEncoder encoder = new DelegatingPasswordEncoder(ID,
            Map.of(ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
