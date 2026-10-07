-- Investimentos (renda fixa simulada, tipo CDB). O dinheiro sai da conta (INVEST_OUT) e volta no resgate (INVEST_IN)
-- com o rendimento líquido de IR. Produtos ficam numa tabela: a taxa e o prazo são copiados para cada aplicação.

CREATE TABLE investment_products (
    code         VARCHAR(30)   PRIMARY KEY,
    name         VARCHAR(60)   NOT NULL,
    kind         VARCHAR(5)    NOT NULL CHECK (kind IN ('DAILY', 'TERM')),
    annual_rate  NUMERIC(7,4)  NOT NULL CHECK (annual_rate > 0),     -- 0.1050 = 10,50% a.a.
    term_days    INTEGER       CHECK (term_days IS NULL OR term_days > 0),
    min_amount   NUMERIC(19,2) NOT NULL CHECK (min_amount > 0),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    CHECK ((kind = 'TERM') = (term_days IS NOT NULL))
);

INSERT INTO investment_products (code, name, kind, annual_rate, term_days, min_amount) VALUES
    ('CDB_DAILY', 'CDB Liquidez Diária', 'DAILY', 0.1050, NULL, 1.00),
    ('CDB_90',    'CDB 90 dias',         'TERM',  0.1150, 90,   100.00),
    ('CDB_365',   'CDB 365 dias',        'TERM',  0.1275, 365,  100.00);

CREATE TABLE investments (
    id              UUID          PRIMARY KEY,
    customer_id     UUID          NOT NULL REFERENCES customers (id),
    account_id      UUID          NOT NULL REFERENCES accounts (id),
    product_code    VARCHAR(30)   NOT NULL REFERENCES investment_products (code),
    product_name    VARCHAR(60)   NOT NULL,
    principal       NUMERIC(19,2) NOT NULL CHECK (principal > 0),
    currency        VARCHAR(3)    NOT NULL,
    annual_rate     NUMERIC(7,4)  NOT NULL CHECK (annual_rate > 0),
    term_days       INTEGER       CHECK (term_days IS NULL OR term_days > 0),
    applied_at      TIMESTAMPTZ   NOT NULL,
    matures_at      TIMESTAMPTZ,
    status          VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'REDEEMED')),
    redeemed_at     TIMESTAMPTZ,
    redeemed_gross  NUMERIC(19,2),
    redeemed_tax    NUMERIC(19,2),
    version         BIGINT        NOT NULL DEFAULT 0,
    CHECK ((status = 'REDEEMED') = (redeemed_at IS NOT NULL)),
    CHECK ((term_days IS NULL) = (matures_at IS NULL))
);
CREATE INDEX idx_investments_customer ON investments (customer_id, applied_at DESC);
CREATE INDEX idx_investments_active ON investments (customer_id) WHERE status = 'ACTIVE';

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND', 'PIGGY_IN', 'PIGGY_OUT',
                    'PIX_OUT', 'PIX_IN', 'PIX_RETURN_OUT', 'PIX_RETURN_IN', 'INVEST_OUT', 'INVEST_IN'));
