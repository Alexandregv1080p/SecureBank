-- Devolução de Pix: um Pix que devolve (parte de) outro, e os dois novos tipos de lançamento.

ALTER TABLE pix_transfers ADD COLUMN refund_of_id UUID REFERENCES pix_transfers (id);
ALTER TABLE pix_transfers ADD CONSTRAINT pix_not_self_refund CHECK (refund_of_id IS NULL OR refund_of_id <> id);
CREATE INDEX idx_pix_refund_of ON pix_transfers (refund_of_id) WHERE refund_of_id IS NOT NULL;

-- PIX_RETURN_OUT / PIX_RETURN_IN não cabem nos 10 caracteres de antes.
ALTER TABLE transactions ALTER COLUMN type TYPE VARCHAR(16);
ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER', 'PAYMENT', 'REFUND', 'PIGGY_IN', 'PIGGY_OUT',
                    'PIX_OUT', 'PIX_IN', 'PIX_RETURN_OUT', 'PIX_RETURN_IN'));
