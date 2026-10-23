-- Cartão de crédito: o mesmo cartão (número cifrado, CVV derivado, bloquear/cancelar com motivo) com condições de crédito.
-- A compra vira parcelas em faturas mensais; a fatura é paga com o saldo da conta do cartão (lançamento CARD_INVOICE_PAYMENT).

ALTER TABLE cards ADD COLUMN kind VARCHAR(6) NOT NULL DEFAULT 'DEBIT' CHECK (kind IN ('DEBIT', 'CREDIT'));

CREATE TABLE credit_terms (
    card_id       UUID          PRIMARY KEY REFERENCES cards (id),
    credit_limit  NUMERIC(19,2) NOT NULL CHECK (credit_limit > 0),
    currency      VARCHAR(3)    NOT NULL,
    due_day       INTEGER       NOT NULL CHECK (due_day BETWEEN 1 AND 28),
    version       BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE credit_purchases (
    id            UUID          PRIMARY KEY,
    card_id       UUID          NOT NULL REFERENCES cards (id),
    merchant      VARCHAR(60)   NOT NULL,
    total_amount  NUMERIC(19,2) NOT NULL CHECK (total_amount > 0),
    currency      VARCHAR(3)    NOT NULL,
    installments  INTEGER       NOT NULL CHECK (installments BETWEEN 1 AND 12),
    created_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_credit_purchases_card ON credit_purchases (card_id, created_at DESC);

CREATE TABLE credit_installments (
    id             UUID          PRIMARY KEY,
    purchase_id    UUID          NOT NULL REFERENCES credit_purchases (id),
    card_id        UUID          NOT NULL REFERENCES cards (id),
    number         INTEGER       NOT NULL CHECK (number >= 1),
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    invoice_month  VARCHAR(7)    NOT NULL,   -- AAAA-MM da fatura em que a parcela cai
    UNIQUE (purchase_id, number)
);
CREATE INDEX idx_credit_installments_invoice ON credit_installments (card_id, invoice_month);

CREATE TABLE credit_payments (
    id              UUID          PRIMARY KEY,
    card_id         UUID          NOT NULL REFERENCES cards (id),
    invoice_month   VARCHAR(7)    NOT NULL,
    amount          NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)    NOT NULL,
    transaction_id  UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_credit_payments_invoice ON credit_payments (card_id, invoice_month);

-- CARD_INVOICE_PAYMENT tem 20 caracteres: a coluna (16 desde a V9) cresce.
ALTER TABLE transactions ALTER COLUMN type TYPE VARCHAR(24);
ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND', 'PIGGY_IN', 'PIGGY_OUT',
                    'PIX_OUT', 'PIX_IN', 'PIX_RETURN_OUT', 'PIX_RETURN_IN', 'INVEST_OUT', 'INVEST_IN',
                    'FX_BUY', 'FX_SELL', 'CARD_PURCHASE', 'CARD_INVOICE_PAYMENT'));
