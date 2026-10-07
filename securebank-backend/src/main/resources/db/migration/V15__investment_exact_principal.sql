-- Resgate parcial exato: o principal passa a guardar 8 casas (a tela continua mostrando o centavo).
-- O valor que já existe é de 2 casas e continua igual.
ALTER TABLE investments ALTER COLUMN principal TYPE NUMERIC(19,8);
