-- ADR-0016: la capacidad clínica no es un rol de membresía.

-- La restricción de V23 (ADMIN | DENTIST | RECEPTIONIST) rechazaría MEMBER: se quita ANTES de convertir.
ALTER TABLE memberships DROP CONSTRAINT ck_memberships_role;

-- Mismo motivo que en V23: el FORCE de RLS se suelta solo durante el UPDATE de datos.
ALTER TABLE memberships NO FORCE ROW LEVEL SECURITY;
UPDATE memberships SET role = 'MEMBER' WHERE role IN ('USER', 'DENTIST', 'RECEPTIONIST');
ALTER TABLE memberships FORCE ROW LEVEL SECURITY;

ALTER TABLE memberships
    ADD CONSTRAINT ck_memberships_role CHECK (role IN ('ADMIN', 'MEMBER'));

ALTER TABLE invitations DROP CONSTRAINT invitations_role_check;
ALTER TABLE invitations
    ADD CONSTRAINT invitations_role_check CHECK (role = 'MEMBER');
