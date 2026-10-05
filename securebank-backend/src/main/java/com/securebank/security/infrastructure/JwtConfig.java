package com.securebank.security.infrastructure;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.securebank.authentication.application.AuthSettings;
import com.securebank.authentication.application.SessionDenylist;
import com.securebank.shared.domain.SessionId;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT assinado com RS256 (chave privada só assina; qualquer serviço valida com a pública via JWKS).
 * Os decoders fixam o algoritmo, o emissor e a audiência — fecham alg=none, troca RS256→HS256 e token de outro destino.
 */
@Configuration
class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    RSAKey signingKey(SecurityProperties props) throws Exception {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (props.jwt().configured()) {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pem(props.jwt().privateKey())));
            publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(props.jwt().publicKey())));
        } else {
            log.warn("JWT_PRIVATE_KEY/JWT_PUBLIC_KEY ausentes: gerando par RSA efêmero. Apenas desenvolvimento.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        }
        RSAKey unnamed = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(unnamed.computeThumbprint().toString())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /** Valida os access tokens: assinatura, prazo, emissor, audiência e sessão não revogada. */
    @Bean
    @Primary
    JwtDecoder accessTokenDecoder(RSAKey signingKey, AuthSettings settings, SessionDenylist denylist)
            throws Exception {
        return decoder(signingKey, settings.issuer(), settings.audience(), settings.clockSkew(),
                new SessionNotRevokedValidator(denylist));
    }

    /** Valida o token de desafio MFA: audiência própria, então nunca vale como access token (nem o inverso). */
    @Bean
    JwtDecoder mfaTokenDecoder(RSAKey signingKey, AuthSettings settings) throws Exception {
        return decoder(signingKey, settings.issuer(), settings.mfaAudience(), settings.clockSkew());
    }

    @SafeVarargs
    private static JwtDecoder decoder(RSAKey key, String issuer, String audience, Duration skew,
            OAuth2TokenValidator<Jwt>... extra) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        java.util.List<OAuth2TokenValidator<Jwt>> validators = new java.util.ArrayList<>(List.of(
                new JwtTimestampValidator(skew), new JwtIssuerValidator(issuer), audienceValidator(audience)));
        validators.addAll(List.of(extra));
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
    }

    static final String SERVER_ERROR = "server_error";

    /** Sessão revogada (logout, reuso de refresh, troca de senha...) derruba o access token na hora. */
    private record SessionNotRevokedValidator(SessionDenylist denylist) implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            try {
                String sid = jwt.getClaimAsString("sid");
                if (sid == null || denylist.isRevoked(SessionId.of(sid))) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session revoked", null));
                }
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException e) {
                // Falha fechada: se não dá para saber se a sessão foi revogada, o token não vale. O código
                // "server_error" deixa o SecurityHandlers responder 503 (dependência fora) em vez de 401 (sessão inválida).
                log.error("Could not check session denylist", e);
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(SERVER_ERROR, "Session check failed", null));
            }
        }
    }

    private static byte[] pem(String value) {
        return Base64.getMimeDecoder().decode(value.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", ""));
    }
}
