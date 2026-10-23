-- Cartão de débito virtual: o número fica cifrado (AES-GCM) e o CVV nunca é gravado (é derivado com chave do servidor).
-- A compra sai direto do saldo da conta (lançamento CARD_PURCHASE) e consome o limite próprio CARD.

CREATE TABLE cards (
    id                  UUID         PRIMARY KEY,
    customer_id         UUID         NOT NULL REFERENCES customers (id),
    account_id          UUID         NOT NULL REFERENCES accounts (id),
    holder_name         VARCHAR(40)  NOT NULL,
    last4               VARCHAR(4)   NOT NULL,
    number_encrypted    VARCHAR(200) NOT NULL,
    number_fingerprint  VARCHAR(43)  NOT NULL UNIQUE,   -- SHA-256 do número: dois cartões não repetem o mesmo número
    expiry_month        INTEGER      NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year         INTEGER      NOT NULL,
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CANCELED')),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_cards_customer ON cards (customer_id, created_at) WHERE status <> 'CANCELED';
CREATE INDEX idx_cards_account ON cards (account_id) WHERE status <> 'CANCELED';

CREATE TABLE card_purchases (
    id              UUID          PRIMARY KEY,
    card_id         UUID          NOT NULL REFERENCES cards (id),
    account_id      UUID          NOT NULL REFERENCES accounts (id),
    amount          NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)    NOT NULL,
    merchant        VARCHAR(60)   NOT NULL,
    transaction_id  UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_card_purchases_card ON card_purchases (card_id, created_at DESC);

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND', 'PIGGY_IN', 'PIGGY_OUT',
                    'PIX_OUT', 'PIX_IN', 'PIX_RETURN_OUT', 'PIX_RETURN_IN', 'INVEST_OUT', 'INVEST_IN',
                    'FX_BUY', 'FX_SELL', 'CARD_PURCHASE'));

-- Limite próprio do cartão (a compra consome o limite diário): contas existentes recebem o padrão.
ALTER TABLE limits DROP CONSTRAINT limits_type_check;
ALTER TABLE limits ADD CONSTRAINT limits_type_check CHECK (type IN ('WITHDRAW', 'TRANSFER', 'PAYMENT', 'PIX', 'FX', 'CARD'));
INSERT INTO limits (account_id, type, per_operation, daily, currency, updated_at)
SELECT id, 'CARD', 3000.00, 5000.00, currency, now() FROM accounts
ON CONFLICT (account_id, type) DO NOTHING;
