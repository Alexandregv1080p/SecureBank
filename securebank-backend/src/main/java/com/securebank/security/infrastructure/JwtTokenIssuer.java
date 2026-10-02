package com.securebank.security.infrastructure;

import com.nimbusds.jose.jwk.RSAKey;
import com.securebank.authentication.application.AuthSettings;
import com.securebank.authentication.application.TokenIssuer;
import com.securebank.authentication.domain.Session;
import com.securebank.authentication.domain.User;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

/** Claims do access token (seção 16): sub, iss, aud, iat, exp, roles — mais jti, sid (sessão), cid e amr. Nada sensível. */
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtDecoder mfaDecoder;
    private final RSAKey key;
    private final AuthSettings settings;
    private final BankTime time;

    JwtTokenIssuer(JwtEncoder encoder, @Qualifier("mfaTokenDecoder") JwtDecoder mfaDecoder, RSAKey key,
            AuthSettings settings, BankTime time) {
        this.encoder = encoder;
        this.mfaDecoder = mfaDecoder;
        this.key = key;
        this.settings = settings;
        this.time = time;
    }

    @Override
    public AccessToken issueAccessToken(User user, Session session) {
        Instant now = time.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(user.id().toString())
                .audience(List.of(settings.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(settings.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(user.role().name()))
                .claim("sid", session.id().toString())
                .claim("amr", session.mfaVerified() ? List.of("pwd", "otp") : List.of("pwd"));
        if (user.customerId() != null) {
            claims.claim("cid", user.customerId().toString());
        }
        return new AccessToken(sign(claims.build()), settings.accessTokenTtl().toSeconds());
    }

    @Override
    public String issueMfaChallenge(UserId userId) {
        Instant now = time.now();
        return sign(JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(userId.toString())
                .audience(List.of(settings.mfaAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(settings.mfaChallengeTtl()))
                .id(UUID.randomUUID().toString())
                .build());
    }

    @Override
    public UserId parseMfaChallenge(String token) {
        try {
            Jwt jwt = mfaDecoder.decode(token);
            return UserId.of(jwt.getSubject());
        } catch (JwtException | InvalidValueException e) {
            throw ApplicationException.unauthenticated("INVALID_MFA_CHALLENGE", "Invalid or expired MFA challenge");
        }
    }

    private String sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
