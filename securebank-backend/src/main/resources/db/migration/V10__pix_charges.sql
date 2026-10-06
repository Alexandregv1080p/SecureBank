-- Cobranças Pix (QR dinâmico): valor fixo, validade, uso único.

CREATE TABLE pix_charges (
    txid          VARCHAR(35)   PRIMARY KEY CHECK (txid ~ '^[A-Za-z0-9]{26,35}$'),
    customer_id   UUID          NOT NULL REFERENCES customers (id),
    account_id    UUID          NOT NULL REFERENCES accounts (id),
    amount        NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency      VARCHAR(3)    NOT NULL,
    description   VARCHAR(140),
    status        VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'PAID', 'CANCELED')),   -- EXPIRED é derivado do prazo
    expires_at    TIMESTAMPTZ   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    paid_at       TIMESTAMPTZ,
    paid_pix_id   UUID,
    version       BIGINT        NOT NULL DEFAULT 0,        -- controle otimista: um pagamento só
    CHECK ((status = 'PAID') = (paid_pix_id IS NOT NULL))
);
CREATE INDEX idx_pix_charges_customer ON pix_charges (customer_id, created_at DESC);

-- Rede de segurança: mesmo que a aplicação falhasse, o banco não deixa a mesma cobrança ser paga duas vezes.
ALTER TABLE pix_transfers ADD COLUMN charge_txid VARCHAR(35) UNIQUE REFERENCES pix_charges (txid);
