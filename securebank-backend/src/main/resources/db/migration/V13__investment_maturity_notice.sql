-- Aviso de vencimento: cada aplicação com prazo é avisada uma vez só (maturity_notified_at marca que já foi).
ALTER TABLE investments ADD COLUMN maturity_notified_at TIMESTAMPTZ;

-- O que já venceu antes desta migração não deve virar uma enxurrada de avisos.
UPDATE investments SET maturity_notified_at = now() WHERE matures_at IS NOT NULL AND matures_at <= now();

CREATE INDEX idx_investments_maturity ON investments (matures_at)
    WHERE status = 'ACTIVE' AND matures_at IS NOT NULL AND maturity_notified_at IS NULL;
