package com.securebank.authentication.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Parâmetros de autenticação (prefixo {@code securebank.auth}); os padrões seguem a seção 17 da spec. */
@ConfigurationProperties("securebank.auth")
public record AuthSettings(
        @DefaultValue("securebank") String issuer,
        @DefaultValue("securebank-api") String audience,
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P7D") Duration refreshTokenTtl,
        @DefaultValue("P30D") Duration sessionMaxLifetime,
        @DefaultValue("PT5M") Duration mfaChallengeTtl,
        @DefaultValue("5") int loginMaxFailures,
        @DefaultValue("PT15M") Duration loginFailureWindow,
        @DefaultValue("5") int mfaMaxFailures,
        @DefaultValue("PT5M") Duration mfaFailureWindow,
        @DefaultValue("PT5S") Duration clockSkew) {

    public String mfaAudience() {
        return audience + "-mfa";
    }
}
