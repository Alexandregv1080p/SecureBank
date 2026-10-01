-- Core Banking: clientes, contas, lançamentos, transferências, pagamentos e limites.
-- Dinheiro sempre NUMERIC(19,2). Regras críticas também existem no banco (defesa em profundidade).

CREATE TABLE customers (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    document    VARCHAR(11)  NOT NULL UNIQUE,
    email       VARCHAR(254) NOT NULL UNIQUE,
    phone       VARCHAR(16)  NOT NULL,
    status      VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'SUSPENDED', 'CLOSED')),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE SEQUENCE account_number_seq START 100000;

CREATE TABLE accounts (
    id              UUID          PRIMARY KEY,
    customer_id     UUID          NOT NULL REFERENCES customers (id),
    branch          VARCHAR(4)    NOT NULL,
    account_number  VARCHAR(14)   NOT NULL,
    type            VARCHAR(10)   NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS')),
    status          VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    balance         NUMERIC(19,2) NOT NULL CHECK (balance >= 0),   -- saldo nunca negativo
    currency        VARCHAR(3)    NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,              -- controle otimista de concorrência
    UNIQUE (branch, account_number)
);
CREATE INDEX idx_accounts_customer ON accounts (customer_id);

CREATE TABLE transactions (
    id             UUID          PRIMARY KEY,
    account_id     UUID          NOT NULL REFERENCES accounts (id),
    type           VARCHAR(10)   NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND')),
    direction      VARCHAR(6)    NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT')),
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(19,2) NOT NULL CHECK (balance_after >= 0),
    currency       VARCHAR(3)    NOT NULL,
    status         VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')),
    reference      VARCHAR(64),
    created_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_transactions_statement ON transactions (account_id, created_at DESC);

-- Livro-razão append-only: nada é apagado e só o status pode mudar (seção 30: rastreabilidade).
CREATE FUNCTION forbid_ledger_tampering() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'transactions are append-only: DELETE is not allowed';
    END IF;
    IF (NEW.id, NEW.account_id, NEW.type, NEW.direction, NEW.amount, NEW.balance_after, NEW.currency,
        NEW.reference, NEW.created_at)
       IS DISTINCT FROM
       (OLD.id, OLD.account_id, OLD.type, OLD.direction, OLD.amount, OLD.balance_after, OLD.currency,
        OLD.reference, OLD.created_at) THEN
        RAISE EXCEPTION 'transactions are immutable except for status';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_append_only
    BEFORE UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_tampering();

CREATE TABLE transfers (
    id                     UUID          PRIMARY KEY,
    source_account_id      UUID          NOT NULL REFERENCES accounts (id),
    destination_account_id UUID          NOT NULL REFERENCES accounts (id),
    amount                 NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)    NOT NULL,
    description            VARCHAR(140),
    idempotency_key        VARCHAR(128)  NOT NULL,
    status                 VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    debit_transaction_id   UUID          REFERENCES transactions (id),
    credit_transaction_id  UUID          REFERENCES transactions (id),
    failure_reason         VARCHAR(255),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    CHECK (source_account_id <> destination_account_id),
    UNIQUE (source_account_id, idempotency_key)   -- rede de segurança: a mesma chave nunca executa duas vezes
);
CREATE INDEX idx_transfers_source ON transfers (source_account_id, created_at DESC);

CREATE TABLE payments (
    id               UUID          PRIMARY KEY,
    account_id       UUID          NOT NULL REFERENCES accounts (id),
    amount           NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)    NOT NULL,
    barcode          VARCHAR(48)   NOT NULL,
    description      VARCHAR(140),
    idempotency_key  VARCHAR(128)  NOT NULL,
    status           VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    transaction_id   UUID          REFERENCES transactions (id),
    failure_reason   VARCHAR(255),
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    UNIQUE (account_id, idempotency_key)
);
CREATE INDEX idx_payments_account ON payments (account_id, created_at DESC);

CREATE TABLE limits (
    account_id     UUID          NOT NULL REFERENCES accounts (id),
    type           VARCHAR(10)   NOT NULL CHECK (type IN ('WITHDRAW', 'TRANSFER', 'PAYMENT')),
    per_operation  NUMERIC(19,2) NOT NULL CHECK (per_operation > 0),
    daily          NUMERIC(19,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (account_id, type),
    CHECK (daily >= per_operation)
);
