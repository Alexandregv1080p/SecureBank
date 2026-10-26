-- Equipe com departamento e hierarquia: o papel vem do departamento e o nível (analista/gerente) separa quem
-- pede de quem decide. Papéis novos: RISK, CREDIT, FINANCE e AUDITOR (SUPPORT é o Atendimento).
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('CUSTOMER', 'SUPPORT', 'RISK', 'CREDIT', 'FINANCE', 'AUDITOR', 'ADMIN'));

ALTER TABLE users ADD COLUMN department VARCHAR(12);
ALTER TABLE users ADD COLUMN level VARCHAR(8);

-- quem já existia: Atendimento (analista) e Diretoria (gerente)
UPDATE users SET department = 'ATENDIMENTO', level = 'ANALYST' WHERE role = 'SUPPORT';
UPDATE users SET department = 'DIRETORIA', level = 'MANAGER' WHERE role = 'ADMIN';

ALTER TABLE users ADD CONSTRAINT users_department_check
    CHECK (department IN ('ATENDIMENTO', 'RISCO', 'CREDITO', 'FINANCEIRO', 'AUDITORIA', 'DIRETORIA'));
ALTER TABLE users ADD CONSTRAINT users_level_check CHECK (level IN ('ANALYST', 'MANAGER'));
-- cliente não tem departamento; equipe sempre tem departamento e nível; a Diretoria só tem gerentes
ALTER TABLE users ADD CONSTRAINT users_staff_position_check CHECK (
    (role = 'CUSTOMER' AND department IS NULL AND level IS NULL)
    OR (role <> 'CUSTOMER' AND department IS NOT NULL AND level IS NOT NULL
        AND (department <> 'DIRETORIA' OR level = 'MANAGER')));

-- mudar a posição de alguém derruba as sessões abertas dela (o token antigo ainda teria as permissões de antes)
ALTER TABLE sessions DROP CONSTRAINT sessions_revocation_check;
ALTER TABLE sessions ADD CONSTRAINT sessions_revocation_check
    CHECK (revocation IN ('LOGOUT', 'USER_REVOKED', 'PASSWORD_CHANGED', 'REUSE_DETECTED', 'USER_DISABLED', 'ROLE_CHANGED'));
