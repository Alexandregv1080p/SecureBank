-- Fase 6: consumidores idempotentes e notificações.

-- Quem já processou qual evento (id do outbox). Gravado NA MESMA transação do efeito do consumidor:
-- reentrega (at-least-once) vira no-op.
CREATE TABLE processed_events (
    consumer      VARCHAR(40) NOT NULL,
    event_id      BIGINT      NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

CREATE TABLE notifications (
    id           UUID         PRIMARY KEY,
    customer_id  UUID         NOT NULL REFERENCES customers (id),
    type         VARCHAR(40)  NOT NULL,
    title        VARCHAR(120) NOT NULL,
    body         VARCHAR(300) NOT NULL,
    event_id     BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    read_at      TIMESTAMPTZ
);
CREATE INDEX idx_notifications_customer ON notifications (customer_id, created_at DESC);
