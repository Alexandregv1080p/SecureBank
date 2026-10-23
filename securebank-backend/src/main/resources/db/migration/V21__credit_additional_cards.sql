-- Cartão adicional: tem número e CVV próprios, mas gasta o limite e cai nas faturas do cartão principal.

CREATE TABLE credit_additional_cards (
    card_id       UUID PRIMARY KEY REFERENCES cards (id),
    main_card_id  UUID NOT NULL REFERENCES cards (id)
);
CREATE INDEX idx_credit_additional_main ON credit_additional_cards (main_card_id);

-- Com qual cartão a compra foi feita (nulo = o próprio principal).
ALTER TABLE credit_purchases ADD COLUMN made_with UUID REFERENCES cards (id);
