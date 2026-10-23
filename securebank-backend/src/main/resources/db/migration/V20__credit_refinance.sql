-- Parcelamento da fatura: o saldo da fatura é quitado por um "pagamento" sem dinheiro saindo da conta (REFINANCE) e vira
-- uma compra parcelada com juros nas faturas seguintes.

ALTER TABLE credit_payments ALTER COLUMN transaction_id DROP NOT NULL;
ALTER TABLE credit_payments ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'CASH' CHECK (kind IN ('CASH', 'REFINANCE'));

ALTER TABLE credit_purchases ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'PURCHASE' CHECK (kind IN ('PURCHASE', 'REFINANCE'));
