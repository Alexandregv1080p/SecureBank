-- Pedidos que passaram da alçada de quem os fez e esperam a decisão de um gerente (limite de cartão acima da alçada
-- do analista de Crédito; desbloqueio de conta pedido por analista de Risco).
CREATE TABLE approval_requests (
    id             UUID          PRIMARY KEY,
    kind           VARCHAR(20)   NOT NULL CHECK (kind IN ('CREDIT_LIMIT', 'ACCOUNT_UNBLOCK')),
    subject_id     UUID          NOT NULL,                  -- cartão (limite) ou conta (desbloqueio)
    amount         NUMERIC(19,2),                           -- só no limite
    note           VARCHAR(140),
    requested_by   UUID          NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ   NOT NULL,
    status         VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED')),
    decided_by     UUID          REFERENCES users (id),
    decision_note  VARCHAR(140),
    decided_at     TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    CHECK ((kind = 'CREDIT_LIMIT') = (amount IS NOT NULL)),
    CHECK ((status = 'PENDING') = (decided_by IS NULL))
);

-- um pedido em aberto por alvo e tipo: dois analistas não disputam o mesmo cartão ou a mesma conta
CREATE UNIQUE INDEX uq_approval_pending ON approval_requests (kind, subject_id) WHERE status = 'PENDING';
CREATE INDEX idx_approval_status ON approval_requests (status, created_at DESC);
CREATE INDEX idx_approval_requester ON approval_requests (requested_by, created_at DESC);
