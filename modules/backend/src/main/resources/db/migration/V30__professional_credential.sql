-- Credencial profesional y firma con credencial (update-staff-professional-credential).
--
-- La capacidad clínica es una credencial, no un rol (ADR-0016 D7): la habilitación COP vigente
-- (ADR-0018 D6). Esta migración:
--   1. crea la credencial del usuario, con RLS que solo deja escribir la propia (design.md D1);
--   2. agrega el retiro por clínica a la membresía, con una guarda en la base (D2);
--   3. agrega la foto de la credencial a los tres registros que se firman, y una restricción que
--      hace imposible un sellado nuevo sin ella sin reescribir lo ya sellado (D5).
-- No modifica filas existentes.

-- ---------------------------------------------------------------------------------------------
-- 1. Credencial profesional (una por usuario; vale en todas sus clínicas)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE professional_credential (
    user_id         BIGINT PRIMARY KEY REFERENCES users (id),
    -- Solo dígitos; 10 es una cota de almacenamiento, no el formato del COP (design.md, Open Questions).
    cop_number      VARCHAR(10) NOT NULL CHECK (cop_number ~ '^[0-9]{1,10}$'),
    -- Fecha de Perú: la vigencia se evalúa en America/Lima (design.md D3).
    licensed_until  DATE        NOT NULL,
    -- Momento en que el profesional aceptó la declaración jurada de la última declaración.
    sworn_at        TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Con nombre propio: el servicio la reconoce por él y responde un 409 de texto fijo, sin la
    -- causa SQL, que trae el número en conflicto (design.md D1).
    CONSTRAINT ux_professional_credential_cop_number UNIQUE (cop_number)
);

-- ¿El usuario objetivo es miembro de la organización activa, y quien consulta es su ADMIN activo?
-- SECURITY INVOKER (por defecto): la lectura de memberships corre con su propia RLS, cuya rama de
-- administrador (V28) ya deja ver las membresías de la organización activa a su ADMIN. No hace
-- falta saltarla, y memberships no consulta esta tabla, así que no hay recursión.
CREATE FUNCTION current_user_is_admin_of_member(target_user_id BIGINT)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM memberships m
        WHERE m.user_id = target_user_id
          AND m.organization_id = NULLIF(current_setting('app.organization_id', true), '')::BIGINT
          AND current_user_is_org_admin(m.organization_id)
    )
$$;

ALTER TABLE professional_credential ENABLE ROW LEVEL SECURITY;
ALTER TABLE professional_credential FORCE ROW LEVEL SECURITY;

-- Una policy por comando (no FOR ALL): la rama de lectura del ADMIN no se cuela en la escritura.
CREATE POLICY professional_credential_select ON professional_credential
    FOR SELECT
    USING (
        user_id = current_setting('app.user_id')::BIGINT
        OR current_user_is_admin_of_member(user_id)
    );

CREATE POLICY professional_credential_insert ON professional_credential
    FOR INSERT
    WITH CHECK (user_id = current_setting('app.user_id')::BIGINT);

-- USING además de WITH CHECK: con solo WITH CHECK, un UPDATE sobre una fila ajena podría
-- reescribir su user_id al propio y pasar el control (design.md D1).
CREATE POLICY professional_credential_update ON professional_credential
    FOR UPDATE
    USING (user_id = current_setting('app.user_id')::BIGINT)
    WITH CHECK (user_id = current_setting('app.user_id')::BIGINT);

-- Sin policy de DELETE: el rol de la app no borra credenciales (y además no tiene el privilegio).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE ON professional_credential TO odontorisas_app';
        -- Los DEFAULT PRIVILEGES de V6 conceden DELETE a toda tabla nueva: acá se quita.
        EXECUTE 'REVOKE DELETE ON professional_credential FROM odontorisas_app';
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------------
-- 2. Retiro de la capacidad clínica, por membresía (solo afecta a esa clínica)
-- ---------------------------------------------------------------------------------------------

ALTER TABLE memberships
    ADD COLUMN clinical_capacity_revoked_at TIMESTAMPTZ,
    ADD COLUMN clinical_capacity_revoked_by BIGINT REFERENCES users (id),
    ADD CONSTRAINT memberships_clinical_capacity_revoked_coherent CHECK (
        (clinical_capacity_revoked_at IS NULL) = (clinical_capacity_revoked_by IS NULL)
    );

-- La policy de memberships (V28) vale para todos los comandos y su USING hace de WITH CHECK: un
-- usuario puede actualizar su propia membresía. Sin esta guarda, un miembro podría levantarse su
-- propio retiro con una escritura directa. "Otorgar nunca, retirar sí" vive también acá (D2):
--   - INSERT: la membresía nace sin retiro;
--   - UPDATE que cambia el retiro: se valida el estado FINAL (NEW): quien escribe es ADMIN activo de
--     esa organización, la fila no es la suya y NEW.role es MEMBER (cambiar rol y retiro a la vez
--     no se cuela validando el rol viejo).
-- Sin "OF <columnas>": no depende de qué nombre el SET. SQLSTATE 42501 (insufficient_privilege).
CREATE FUNCTION memberships_clinical_capacity_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.clinical_capacity_revoked_at IS NOT NULL OR NEW.clinical_capacity_revoked_by IS NOT NULL THEN
            RAISE EXCEPTION 'Una membresía no nace con la capacidad clínica retirada'
                USING ERRCODE = '42501';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.clinical_capacity_revoked_at IS DISTINCT FROM OLD.clinical_capacity_revoked_at
       OR NEW.clinical_capacity_revoked_by IS DISTINCT FROM OLD.clinical_capacity_revoked_by THEN
        IF NOT current_user_is_org_admin(NEW.organization_id)
           OR NEW.user_id = current_setting('app.user_id')::BIGINT
           OR NEW.role <> 'MEMBER' THEN
            RAISE EXCEPTION 'Solo el administrador de la clínica retira o restituye la capacidad clínica de un miembro'
                USING ERRCODE = '42501';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

-- "zz_": los triggers BEFORE de una tabla corren en orden alfabético; este corre último y valida lo
-- que dejen otros que pudieran agregarse después.
CREATE TRIGGER zz_memberships_clinical_capacity_guard
    BEFORE INSERT OR UPDATE ON memberships
    FOR EACH ROW EXECUTE FUNCTION memberships_clinical_capacity_guard();

-- ---------------------------------------------------------------------------------------------
-- 3. Foto de la credencial en lo que se firma (ADR-0016 D1, ADR-0018 D6)
-- ---------------------------------------------------------------------------------------------

ALTER TABLE historia_clinica
    ADD COLUMN completed_by          BIGINT REFERENCES users (id),
    ADD COLUMN signer_full_name      VARCHAR(120),
    ADD COLUMN signer_cop_number     VARCHAR(10),
    ADD COLUMN signer_licensed_until DATE;

ALTER TABLE dental_chart_block
    ADD COLUMN sealed_by             BIGINT REFERENCES users (id),
    ADD COLUMN signer_full_name      VARCHAR(120),
    ADD COLUMN signer_cop_number     VARCHAR(10),
    ADD COLUMN signer_licensed_until DATE;

ALTER TABLE consultation_note
    ADD COLUMN signer_full_name      VARCHAR(120),
    ADD COLUMN signer_cop_number     VARCHAR(10),
    ADD COLUMN signer_licensed_until DATE;

-- NOT VALID: PostgreSQL la aplica a todo INSERT/UPDATE nuevo y no la verifica sobre las filas que ya
-- existen. Lo sellado antes de este cambio queda como está ("firmado antes del registro de
-- credenciales", que se deriva: sellado y sin foto) y ya no admite ningún UPDATE; un sellado nuevo
-- sin foto es imposible aun por SQL directo (design.md D5).
ALTER TABLE historia_clinica
    ADD CONSTRAINT historia_clinica_signer_required CHECK (
        status <> 'COMPLETED'
        OR (completed_by IS NOT NULL AND signer_full_name IS NOT NULL
            AND signer_cop_number IS NOT NULL AND signer_licensed_until IS NOT NULL)
    ) NOT VALID;

ALTER TABLE dental_chart_block
    ADD CONSTRAINT dental_chart_block_signer_required CHECK (
        status <> 'SEALED'
        OR (sealed_by IS NOT NULL AND signer_full_name IS NOT NULL
            AND signer_cop_number IS NOT NULL AND signer_licensed_until IS NOT NULL)
    ) NOT VALID;

ALTER TABLE consultation_note
    ADD CONSTRAINT consultation_note_signer_required CHECK (
        status <> 'SELLADA'
        OR (sealed_by IS NOT NULL AND signer_full_name IS NOT NULL
            AND signer_cop_number IS NOT NULL AND signer_licensed_until IS NOT NULL)
    ) NOT VALID;
