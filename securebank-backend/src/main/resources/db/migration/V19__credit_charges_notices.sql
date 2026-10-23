-- Fatura vencida rende multa e juros; o agendador diário lança os encargos e os avisos de fatura.

ALTER TABLE credit_terms ADD COLUMN billed_on DATE;   -- último dia em que o agendador processou o cartão

CREATE TABLE credit_charges (
    id             UUID          PRIMARY KEY,
    card_id        UUID          NOT NULL REFERENCES cards (id),
    invoice_month  VARCHAR(7)    NOT NULL,
    kind           VARCHAR(10)   NOT NULL CHECK (kind IN ('LATE_FEE', 'INTEREST')),
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)    NOT NULL,
    accrued_on     DATE          NOT NULL,   -- o dia a que o encargo se refere: rodar de novo o mesmo dia não duplica
    created_at     TIMESTAMPTZ   NOT NULL,
    UNIQUE (card_id, invoice_month, kind, accrued_on)
);
CREATE INDEX idx_credit_charges_invoice ON credit_charges (card_id, invoice_month);

-- Cada aviso de uma fatura sai uma vez só (a chave primária garante, mesmo com duas instâncias rodando).
CREATE TABLE credit_notices (
    card_id        UUID        NOT NULL REFERENCES cards (id),
    invoice_month  VARCHAR(7)  NOT NULL,
    kind           VARCHAR(10) NOT NULL CHECK (kind IN ('CLOSED', 'DUE_SOON', 'OVERDUE')),
    sent_on        DATE        NOT NULL,
    PRIMARY KEY (card_id, invoice_month, kind)
);
