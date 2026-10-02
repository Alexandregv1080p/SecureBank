package com.securebank.shared.infrastructure.idempotency;

import com.securebank.shared.application.IdempotencyStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Postgres como fonte de verdade da idempotência (um Redis perdido não pode permitir um débito em dobro).
 * Cada comando roda em autocommit, fora de qualquer transação de negócio: a reserva da chave precisa ficar visível
 * para as outras requisições imediatamente, e a resposta guardada não pode ser desfeita por um rollback.
 */
@Component
class JdbcIdempotencyStore implements IdempotencyStore {

    static final Duration RETENTION = Duration.ofHours(24);
    /** Reserva sem conclusão há mais que isto = a JVM caiu no meio; a chave pode ser retomada. */
    static final Duration ABANDONED_AFTER = Duration.ofSeconds(60);

    private record Row(String fingerprint, String state, Integer status, String body, Instant createdAt,
            Instant expiresAt) {}

    private final JdbcTemplate jdbc;

    JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Claim claim(String scope, String key, String fingerprint, Instant now) {
        if (insert(scope, key, fingerprint, now) == 1) {
            return new Claim.Proceed();
        }
        Row row = find(scope, key);
        if (row == null) { // apagada entre o insert e o select: tenta de novo uma vez
            return insert(scope, key, fingerprint, now) == 1 ? new Claim.Proceed() : new Claim.InProgress();
        }
        boolean expired = !now.isBefore(row.expiresAt());
        boolean abandoned = "IN_PROGRESS".equals(row.state()) && row.createdAt().plus(ABANDONED_AFTER).isBefore(now);
        if (expired || abandoned) {
            // retomada atômica: só uma requisição vence (o created_at antigo age como versão)
            int taken = jdbc.update("update idempotency_keys set fingerprint = ?, state = 'IN_PROGRESS',"
                            + " response_status = null, response_body = null, created_at = ?, expires_at = ?"
                            + " where scope = ? and idem_key = ? and created_at = ?",
                    fingerprint, ts(now), ts(now.plus(RETENTION)), scope, key, ts(row.createdAt()));
            return taken == 1 ? new Claim.Proceed() : new Claim.InProgress();
        }
        if (!row.fingerprint().equals(fingerprint)) {
            return new Claim.Mismatch();
        }
        return "COMPLETED".equals(row.state()) ? new Claim.Replay(row.status(), row.body()) : new Claim.InProgress();
    }

    @Override
    public void complete(String scope, String key, int status, String body) {
        jdbc.update("update idempotency_keys set state = 'COMPLETED', response_status = ?, response_body = ?"
                + " where scope = ? and idem_key = ?", status, body, scope, key);
    }

    @Override
    public void release(String scope, String key) {
        jdbc.update("delete from idempotency_keys where scope = ? and idem_key = ?", scope, key);
    }

    private int insert(String scope, String key, String fingerprint, Instant now) {
        return jdbc.update("insert into idempotency_keys (scope, idem_key, fingerprint, state, created_at, expires_at)"
                        + " values (?, ?, ?, 'IN_PROGRESS', ?, ?) on conflict do nothing",
                scope, key, fingerprint, ts(now), ts(now.plus(RETENTION)));
    }

    private Row find(String scope, String key) {
        List<Row> rows = jdbc.query("select fingerprint, state, response_status, response_body, created_at, expires_at"
                        + " from idempotency_keys where scope = ? and idem_key = ?",
                (rs, i) -> new Row(rs.getString(1), rs.getString(2), (Integer) rs.getObject(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()), scope, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
