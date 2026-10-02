-- Fase 5: idempotência genérica e Transactional Outbox.

-- Uma linha por (usuário, Idempotency-Key). Guarda a resposta da 1ª execução para devolvê-la em retries.
CREATE TABLE idempotency_keys (
    scope            VARCHAR(64)  NOT NULL,              -- usuário dono da chave: chaves não colidem entre usuários
    idem_key         VARCHAR(128) NOT NULL,
    fingerprint      VARCHAR(64)  NOT NULL,              -- SHA-256 de método + caminho + corpo
    state            VARCHAR(12)  NOT NULL CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    response_status  INTEGER,
    response_body    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (scope, idem_key)
);
CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);

-- Eventos gravados na MESMA transação da operação; um relay os publica depois (at-least-once, em ordem).
CREATE TABLE outbox_events (
    id              BIGSERIAL    PRIMARY KEY,            -- a ordem de publicação é a ordem do id
    event_type      VARCHAR(60)  NOT NULL,
    aggregate_type  VARCHAR(30)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    payload         TEXT         NOT NULL,               -- JSON
    occurred_at     TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_pending ON outbox_events (id) WHERE published_at IS NULL;
