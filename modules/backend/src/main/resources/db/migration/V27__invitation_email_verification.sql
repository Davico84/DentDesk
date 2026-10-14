ALTER TABLE invitations
    ADD COLUMN verification_code_hash VARCHAR(64),
    ADD COLUMN verification_expires_at TIMESTAMPTZ,
    ADD COLUMN verification_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN verified_at TIMESTAMPTZ;
