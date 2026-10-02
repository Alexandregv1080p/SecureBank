package com.securebank.security.infrastructure;

import com.securebank.authentication.application.AuthSettings;
import com.securebank.authentication.application.SessionDenylist;
import com.securebank.shared.domain.SessionId;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisSessionDenylist implements SessionDenylist {

    private static final String PREFIX = "session:revoked:";

    private final StringRedisTemplate redis;
    /** Vale enquanto algum access token da sessão ainda puder estar válido (TTL + tolerância de relógio + folga). */
    private final Duration ttl;

    RedisSessionDenylist(StringRedisTemplate redis, AuthSettings settings) {
        this.redis = redis;
        this.ttl = settings.accessTokenTtl().plus(settings.clockSkew()).plusMinutes(1);
    }

    @Override
    public void revoke(SessionId sessionId) {
        redis.opsForValue().set(PREFIX + sessionId, "1", ttl);
    }

    @Override
    public boolean isRevoked(SessionId sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + sessionId));
    }
}
