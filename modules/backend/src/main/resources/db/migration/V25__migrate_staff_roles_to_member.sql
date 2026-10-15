-- ADR-0016: la capacidad clínica no es un rol de membresía.
UPDATE memberships SET role = 'MEMBER' WHERE role IN ('USER', 'DENTIST', 'RECEPTIONIST');

ALTER TABLE memberships DROP CONSTRAINT ck_memberships_role;
ALTER TABLE memberships
    ADD CONSTRAINT ck_memberships_role CHECK (role IN ('ADMIN', 'MEMBER'));

ALTER TABLE invitations DROP CONSTRAINT invitations_role_check;
ALTER TABLE invitations
    ADD CONSTRAINT invitations_role_check CHECK (role = 'MEMBER');
