-- Enlaces de recuperación de la contraseña (update-authentication-password-recovery design.md D2).
--
-- Sin RLS, como users y refresh_tokens: es de la identidad global, no de una clínica. Se guarda solo
-- el SHA-256 del token, nunca el token. Las filas de más de 24 h de una cuenta se borran al emitirle un
-- enlace nuevo (no hay tarea programada: @Scheduled está prohibido).

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Consumido por un restablecimiento.
    used_at     TIMESTAMPTZ,
    -- Anulado por un enlace nuevo o por el restablecimiento con otro enlace.
    revoked_at  TIMESTAMPTZ
);

-- El límite de 3 en 24 h cuenta por cuenta y fecha de emisión (D3).
CREATE INDEX ix_password_reset_tokens_user_created ON password_reset_tokens (user_id, created_at);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON password_reset_tokens TO odontorisas_app';
        EXECUTE 'GRANT USAGE, SELECT ON SEQUENCE password_reset_tokens_id_seq TO odontorisas_app';
    END IF;
END $$;
