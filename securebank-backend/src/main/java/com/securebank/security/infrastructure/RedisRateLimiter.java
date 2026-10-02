package com.securebank.security.infrastructure;

import com.securebank.shared.application.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Janela fixa em Redis. INCR + PEXPIRE rodam num script Lua (atômico): sem ele, uma queda entre os dois comandos
 * deixaria a chave sem expiração e bloquearia o usuário para sempre.
 * Se o Redis estiver fora, a exceção sobe e quem chamou falha fechado (nega) em vez de liberar sem limite.
 */
@Component
class RedisRateLimiter implements RateLimiter {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final DefaultRedisScript<List> HIT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return {c, redis.call('PTTL', KEYS[1])}", List.class);

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decision hit(String key, int limit, Duration window) {
        List<Long> result = redis.execute(HIT, List.of(key), String.valueOf(window.toMillis()));
        long count = result.get(0);
        long ttlMillis = Math.max(result.get(1), 0);
        return new Decision(count <= limit, count, Duration.ofMillis(ttlMillis));
    }

    @Override
    public long count(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    @Override
    public void reset(String key) {
        redis.delete(key);
    }
}
