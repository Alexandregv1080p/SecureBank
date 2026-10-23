-- Quem bloqueia ou cancela o cartão diz por quê (3 a 140 caracteres): o motivo fica no cartão e na auditoria.
ALTER TABLE cards ADD COLUMN status_reason VARCHAR(140);
