package com.securebank;

import static com.securebank.TestUsers.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/** Ataques clássicos a JWT (seção 23): todos precisam terminar em 401/403 — e o harness precisa provar que o caso feliz passa. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class JwtAttackIT {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder; // assina com a chave REAL do servidor (só os testes têm acesso)
    @Autowired RSAKey serverKey;

    private static JWTClaimsSet.Builder claims(String customerId, List<String> roles) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("securebank").audience("securebank-api").subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(600))).jwtID(UUID.randomUUID().toString())
                .claim("roles", roles).claim("sid", UUID.randomUUID().toString()).claim("cid", customerId);
    }

    /** Token legítimo, assinado pelo servidor, com claims à escolha. */
    private String signedByServer(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(serverKey.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private JwtClaimsSet.Builder serverClaims(String customerId) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().issuer("securebank").audience(List.of("securebank-api"))
                .subject(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString()).claim("roles", List.of("CUSTOMER"))
                .claim("sid", UUID.randomUUID().toString()).claim("cid", customerId);
    }

    private void assertRejected(String token) throws Exception {
        mvc.perform(bearer(token, get("/api/v1/customers/me"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void sanityAWellFormedServerSignedTokenIsAccepted() throws Exception {
        // sem este controle, os testes negativos abaixo poderiam passar por um motivo errado (ex.: claim faltando)
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        String token = signedByServer(serverClaims(login.customerId()).build());

        mvc.perform(bearer(token, get("/api/v1/customers/me"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(login.customerId()));
    }

    @Test
    void unsignedTokenWithAlgNoneIsRejected() throws Exception {
        String token = new PlainJWT(claims(UUID.randomUUID().toString(), List.of("ADMIN")).build()).serialize();

        assertThat(token).endsWith("."); // alg=none: sem assinatura
        assertRejected(token);
    }

    @Test
    void tamperedPayloadKeepingTheOriginalSignatureIsRejected() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);
        String[] parts = login.accessToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("CUSTOMER", "ADMIN");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "."
                + parts[2];

        assertThat(TestUsers.claims(forged)).contains("ADMIN");
        assertRejected(forged);
        mvc.perform(bearer(forged, get("/api/v1/audit"))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnAttackerKeyIsRejectedEvenWithTheServerKeyId() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID(serverKey.getKeyID()).generate();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(attacker.getKeyID()).build(),
                claims(UUID.randomUUID().toString(), List.of("ADMIN")).build());
        jwt.sign(new RSASSASigner(attacker));

        assertRejected(jwt.serialize());
    }

    @Test
    void hmacTokenSignedWithThePublicKeyIsRejected() throws Exception {
        // ataque de confusão de algoritmo: RS256 → HS256 usando a chave PÚBLICA (conhecida) como segredo HMAC
        String jwks = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        RSAKey publicKey = (RSAKey) JWKSet.parse(jwks).getKeys().get(0);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(publicKey.getKeyID()).build(),
                claims(UUID.randomUUID().toString(), List.of("ADMIN")).build());
        jwt.sign(new MACSigner(publicKey.toRSAPublicKey().getEncoded()));

        assertRejected(jwt.serialize());
    }

    @Test
    void correctSignatureButWrongIssuerAudienceOrMissingSessionIsRejected() throws Exception {
        String cid = UUID.randomUUID().toString();

        assertRejected(signedByServer(serverClaims(cid).issuer("evil-issuer").build()));
        assertRejected(signedByServer(serverClaims(cid).audience(List.of("another-api")).build()));
        assertRejected(signedByServer(serverClaims(cid).audience(List.of("securebank-api-mfa")).build())); // token de MFA
        assertRejected(signedByServer(serverClaims(cid).claims(c -> c.remove("sid")).build()));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant now = Instant.now();

        assertRejected(signedByServer(serverClaims(UUID.randomUUID().toString())
                .issuedAt(now.minusSeconds(1000)).expiresAt(now.minusSeconds(100)).build()));
    }

    @Test
    void unknownRoleGrantsNothing() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        String token = signedByServer(serverClaims(login.customerId()).claim("roles", List.of("ROOT", "SUPERUSER")).build());

        mvc.perform(bearer(token, get("/api/v1/customers/me"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void jwksExposesOnlyThePublicKeyAndMatchesTheTokenKeyId() throws Exception {
        TestUsers.Login login = TestUsers.registerAndLogin(mvc);

        String jwks = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat((String) JsonPath.read(jwks, "$.keys[0].kty")).isEqualTo("RSA");
        assertThat(jwks).contains("\"n\"", "\"e\"").doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
        String header = new String(Base64.getUrlDecoder().decode(login.accessToken().split("\\.")[0]));
        assertThat((String) JsonPath.read(header, "$.alg")).isEqualTo("RS256");
        assertThat((String) JsonPath.read(header, "$.kid")).isEqualTo(JsonPath.read(jwks, "$.keys[0].kid"));
    }
}
