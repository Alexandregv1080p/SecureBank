-- Porquinhos: reservas com nome e meta guardadas dentro de uma conta.
-- O dinheiro sai do saldo da conta (lançamento PIGGY_IN) e entra aqui; o resgate faz o inverso (PIGGY_OUT).

CREATE TABLE piggies (
    id           UUID          PRIMARY KEY,
    customer_id  UUID          NOT NULL REFERENCES customers (id),
    account_id   UUID          NOT NULL REFERENCES accounts (id),
    name         VARCHAR(40)   NOT NULL,
    goal         NUMERIC(19,2) CHECK (goal IS NULL OR goal > 0),
    balance      NUMERIC(19,2) NOT NULL CHECK (balance >= 0),   -- nunca negativo
    currency     VARCHAR(3)    NOT NULL,
    status       VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0              -- controle otimista de concorrência
);
CREATE INDEX idx_piggies_customer ON piggies (customer_id, created_at) WHERE status = 'ACTIVE';

-- Os novos tipos de lançamento (a coluna segue em 10 caracteres: PIGGY_IN e PIGGY_OUT cabem).
ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND', 'PIGGY_IN', 'PIGGY_OUT'));
