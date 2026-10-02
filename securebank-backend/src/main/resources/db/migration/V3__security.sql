-- Segurança: usuários, sessões, refresh tokens, MFA e auditoria.

CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(254) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,                -- Argon2id com parâmetros no próprio hash; nunca a senha
    role           VARCHAR(10)  NOT NULL CHECK (role IN ('CUSTOMER', 'SUPPORT', 'ADMIN')),
    customer_id    UUID         UNIQUE REFERENCES customers (id),
    status         VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    -- cliente tem Customer; equipe não
    CHECK ((role = 'CUSTOMER') = (customer_id IS NOT NULL))
);

CREATE TABLE sessions (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id),
    mfa_verified   BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    last_used_at   TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,               -- vida máxima absoluta
    revoked_at     TIMESTAMPTZ,
    revocation     VARCHAR(20)  CHECK (revocation IN ('LOGOUT', 'USER_REVOKED', 'PASSWORD_CHANGED',
                                                      'REUSE_DETECTED', 'USER_DISABLED')),
    ip             VARCHAR(45),                          -- sempre mascarado
    user_agent     VARCHAR(200)
);
CREATE INDEX idx_sessions_user ON sessions (user_id, created_at DESC);

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES sessions (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,             -- SHA-256 do token; o valor em claro nunca é gravado
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ                              -- rotação: cada token vale uma vez
);
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id);

CREATE TABLE mfa_devices (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL UNIQUE REFERENCES users (id),
    secret_cipher   VARCHAR(255) NOT NULL,               -- segredo TOTP cifrado (AES-GCM)
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'ACTIVE')),
    last_used_step  BIGINT       NOT NULL DEFAULT 0,     -- anti-replay do código
    created_at      TIMESTAMPTZ  NOT NULL,
    confirmed_at    TIMESTAMPTZ
);

-- Auditoria: sem FKs (precisa sobreviver a qualquer exclusão) e append-only.
CREATE TABLE audit_logs (
    id              UUID         PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    event           VARCHAR(40)  NOT NULL,
    user_id         UUID,
    account_id      UUID,
    transaction_id  UUID,
    ip              VARCHAR(45),                          -- mascarado
    trace_id        VARCHAR(64),
    detail          VARCHAR(500)
);
CREATE INDEX idx_audit_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_user ON audit_logs (user_id, occurred_at DESC);
CREATE INDEX idx_audit_event ON audit_logs (event, occurred_at DESC);

CREATE FUNCTION forbid_audit_tampering() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs are append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_tampering();
