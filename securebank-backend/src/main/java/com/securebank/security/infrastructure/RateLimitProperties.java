package com.securebank.security.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Limites por requisição HTTP (prefixo {@code securebank.ratelimit}); o bloqueio por e-mail no login é separado. */
@ConfigurationProperties("securebank.ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int loginPerMinuteIp,
        @DefaultValue("10") int registerPerHourIp,
        @DefaultValue("20") int mfaPerMinuteIp,
        @DefaultValue("60") int refreshPerMinuteIp,
        @DefaultValue("30") int moneyPerMinuteUser) {}
