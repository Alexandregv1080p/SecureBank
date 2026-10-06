-- Pix (simulado dentro do próprio banco): chaves e transferências, com limite e lançamentos próprios.

CREATE TABLE pix_keys (
    id           UUID         PRIMARY KEY,
    customer_id  UUID         NOT NULL REFERENCES customers (id),
    account_id   UUID         NOT NULL REFERENCES accounts (id),
    type         VARCHAR(10)  NOT NULL CHECK (type IN ('CPF', 'EMAIL', 'PHONE', 'RANDOM')),
    value        VARCHAR(80)  NOT NULL UNIQUE,   -- uma chave aponta para uma conta só, em todo o banco
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_pix_keys_customer ON pix_keys (customer_id);

CREATE TABLE pix_transfers (
    id                     UUID          PRIMARY KEY,
    source_account_id      UUID          NOT NULL REFERENCES accounts (id),
    destination_account_id UUID          NOT NULL REFERENCES accounts (id),
    amount                 NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)    NOT NULL,
    message                VARCHAR(140),
    destination_key        VARCHAR(80)   NOT NULL,
    source_name            VARCHAR(120)  NOT NULL,   -- nomes já mascarados (ver PixMasks)
    destination_name       VARCHAR(120)  NOT NULL,
    end_to_end_id          VARCHAR(32)   NOT NULL UNIQUE,
    debit_transaction_id   UUID,
    credit_transaction_id  UUID,
    created_at             TIMESTAMPTZ   NOT NULL,
    CHECK (source_account_id <> destination_account_id)
);
CREATE INDEX idx_pix_source ON pix_transfers (source_account_id, created_at DESC);
CREATE INDEX idx_pix_destination ON pix_transfers (destination_account_id, created_at DESC);

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND', 'PIGGY_IN', 'PIGGY_OUT', 'PIX_OUT', 'PIX_IN'));

-- Limite próprio do Pix (o limite diário soma os lançamentos do tipo): contas existentes recebem o padrão.
ALTER TABLE limits DROP CONSTRAINT limits_type_check;
ALTER TABLE limits ADD CONSTRAINT limits_type_check CHECK (type IN ('WITHDRAW', 'TRANSFER', 'PAYMENT', 'PIX'));
INSERT INTO limits (account_id, type, per_operation, daily, currency, updated_at)
SELECT id, 'PIX', 5000.00, 10000.00, currency, now() FROM accounts
ON CONFLICT (account_id, type) DO NOTHING;
