-- Pix agendado: a autorização é dada na criação; o dinheiro só se move na data.

CREATE TABLE pix_schedules (
    id                 UUID          PRIMARY KEY,
    customer_id        UUID          NOT NULL REFERENCES customers (id),
    source_account_id  UUID          NOT NULL REFERENCES accounts (id),
    pix_key            VARCHAR(80)   NOT NULL,
    destination_name   VARCHAR(120)  NOT NULL,          -- já mascarado
    amount             NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3)    NOT NULL,
    message            VARCHAR(140),
    scheduled_for      DATE          NOT NULL,          -- dia de America/Sao_Paulo
    status             VARCHAR(10)   NOT NULL CHECK (status IN ('SCHEDULED', 'EXECUTED', 'FAILED', 'CANCELED')),
    failure_reason     VARCHAR(60),
    executed_pix_id    UUID,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CHECK ((status = 'EXECUTED') = (executed_pix_id IS NOT NULL))
);
CREATE INDEX idx_pix_schedules_due ON pix_schedules (scheduled_for) WHERE status = 'SCHEDULED';
CREATE INDEX idx_pix_schedules_customer ON pix_schedules (customer_id, scheduled_for DESC);
