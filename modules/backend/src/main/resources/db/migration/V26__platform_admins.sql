-- Administracion global separada de las membresias de clinica.
CREATE TABLE platform_admins (
    user_id    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- StaffService sigue restringiendo las invitaciones de clinica a MEMBER.
ALTER TABLE invitations DROP CONSTRAINT invitations_role_check;
ALTER TABLE invitations
    ADD CONSTRAINT invitations_role_check CHECK (role IN ('ADMIN', 'MEMBER'));

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON platform_admins TO odontorisas_app';
    END IF;
END $$;
