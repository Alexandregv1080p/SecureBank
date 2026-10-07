-- Câmbio simulado: cotação por moeda (com spread), carteiras em moeda estrangeira e recibos das operações.
-- Comprar tira reais da conta (FX_BUY) e coloca moeda na carteira; vender faz o inverso (FX_SELL).

CREATE TABLE fx_rates (
    currency    VARCHAR(3)    PRIMARY KEY,
    mid_rate    NUMERIC(12,6) NOT NULL CHECK (mid_rate > 0),     -- reais por 1 unidade da moeda
    spread      NUMERIC(5,4)  NOT NULL CHECK (spread >= 0 AND spread < 1),
    updated_at  TIMESTAMPTZ   NOT NULL
);
INSERT INTO fx_rates (currency, mid_rate, spread, updated_at) VALUES
    ('USD', 5.200000, 0.0150, now()),
    ('EUR', 5.650000, 0.0150, now());

CREATE TABLE fx_wallets (
    id           UUID          PRIMARY KEY,
    customer_id  UUID          NOT NULL REFERENCES customers (id),
    currency     VARCHAR(3)    NOT NULL REFERENCES fx_rates (currency),
    balance      NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (customer_id, currency)
);

CREATE TABLE fx_operations (
    id              UUID          PRIMARY KEY,
    customer_id     UUID          NOT NULL REFERENCES customers (id),
    account_id      UUID          NOT NULL REFERENCES accounts (id),
    side            VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    currency        VARCHAR(3)    NOT NULL REFERENCES fx_rates (currency),
    foreign_amount  NUMERIC(19,2) NOT NULL CHECK (foreign_amount > 0),
    rate            NUMERIC(12,6) NOT NULL CHECK (rate > 0),
    brl_amount      NUMERIC(19,2) NOT NULL CHECK (brl_amount > 0),
    created_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_fx_operations_customer ON fx_operations (customer_id, created_at DESC);

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND', 'PIGGY_IN', 'PIGGY_OUT',
                    'PIX_OUT', 'PIX_IN', 'PIX_RETURN_OUT', 'PIX_RETURN_IN', 'INVEST_OUT', 'INVEST_IN',
                    'FX_BUY', 'FX_SELL'));

-- Limite próprio de câmbio (a compra consome o limite diário): contas existentes recebem o padrão.
ALTER TABLE limits DROP CONSTRAINT limits_type_check;
ALTER TABLE limits ADD CONSTRAINT limits_type_check CHECK (type IN ('WITHDRAW', 'TRANSFER', 'PAYMENT', 'PIX', 'FX'));
INSERT INTO limits (account_id, type, per_operation, daily, currency, updated_at)
SELECT id, 'FX', 10000.00, 20000.00, currency, now() FROM accounts
ON CONFLICT (account_id, type) DO NOTHING;
