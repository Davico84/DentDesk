-- Capacidad: add-appointments — la agenda interna de la clínica (design.md D1, D2, D5, D7).
--
-- appointments: misma regla de aislamiento que V21 (organization_id propio, FK COMPUESTAS por tenant,
-- índice por organización y FORCE ROW LEVEL SECURITY con organization_isolation). La garantía de "sin
-- choques" vive en la base como restricción de exclusión, no solo en el servicio (D2).
--
-- organization_settings.business_hours: metadato del tenant, sin RLS como el resto de la fila (D5). El
-- DEFAULT de la columna da el horario inicial a las clínicas existentes y a las nuevas.

-- Trusted desde PostgreSQL 13: la crea el dueño de la base sin ser superusuario (verificado en Neon,
-- tarea 1.1).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE appointments (
    id                    BIGSERIAL PRIMARY KEY,
    organization_id       BIGINT NOT NULL REFERENCES organizations (id),
    patient_id            BIGINT NOT NULL,
    -- La pertenencia del odontólogo a la clínica y su capacidad clínica las valida el servicio (D4).
    dentist_user_id       BIGINT NOT NULL REFERENCES users (id),

    -- Instantes (docs/backend.md §8.2). Se persiste el fin y no la duración: la exclusión necesita el
    -- rango como expresión indexable (D1).
    starts_at             TIMESTAMPTZ NOT NULL,
    ends_at               TIMESTAMPTZ NOT NULL,

    status                VARCHAR(20) NOT NULL DEFAULT 'AGENDADA'
                              CHECK (status IN ('AGENDADA', 'ATENDIDA', 'NO_ASISTIO', 'CANCELADA')),
    reason                VARCHAR(500),
    cancellation_reason   VARCHAR(500),
    consultation_note_id  BIGINT,

    status_changed_at     TIMESTAMPTZ,
    status_changed_by     BIGINT REFERENCES users (id),

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            BIGINT NOT NULL REFERENCES users (id),
    updated_by            BIGINT NOT NULL REFERENCES users (id),

    CONSTRAINT ux_appointments_org_id UNIQUE (organization_id, id),
    -- Una nota se enlaza a una sola cita (D7).
    CONSTRAINT ux_appointments_consultation_note UNIQUE (consultation_note_id),

    CONSTRAINT fk_appointments_patient FOREIGN KEY (organization_id, patient_id)
        REFERENCES patients (organization_id, id),
    -- Descartar la nota vacía desengancha la cita sin tocar organization_id (PostgreSQL >= 15, D7).
    CONSTRAINT fk_appointments_consultation_note FOREIGN KEY (organization_id, consultation_note_id)
        REFERENCES consultation_note (organization_id, id) ON DELETE SET NULL (consultation_note_id),

    CONSTRAINT appointments_interval CHECK (ends_at > starts_at),
    -- Coherencia de estado como dato (D1): AGENDADA sin cambio de estado registrado, cualquier otro con él.
    CONSTRAINT appointments_status_change_coherent CHECK (
        (status = 'AGENDADA') = (status_changed_at IS NULL AND status_changed_by IS NULL)
    ),
    CONSTRAINT appointments_cancellation_reason_only_cancelled CHECK (
        cancellation_reason IS NULL OR status = 'CANCELADA'
    ),
    CONSTRAINT appointments_note_only_attended CHECK (
        consultation_note_id IS NULL OR status = 'ATENDIDA'
    ),

    -- Sin choques en la agenda del odontólogo (D2): rango semiabierto, así 10:00-10:30 y 10:30-11:00 no
    -- chocan; CANCELADA y NO_ASISTIO salen del WHERE y liberan el hueco.
    CONSTRAINT ex_appointments_dentist_overlap EXCLUDE USING gist (
        organization_id WITH =,
        dentist_user_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('AGENDADA', 'ATENDIDA'))
);

CREATE INDEX idx_appointments_organization ON appointments (organization_id);
-- La consulta semanal (D8).
CREATE INDEX idx_appointments_org_starts ON appointments (organization_id, starts_at);
CREATE INDEX idx_appointments_patient ON appointments (patient_id);

ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON appointments
    USING (organization_id = current_setting('app.organization_id')::bigint);

-- Los DEFAULT PRIVILEGES de V6 ya lo cubren; el bloque es idempotente y protege una base rehecha (como V29).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON appointments TO odontorisas_app';
        EXECUTE 'GRANT USAGE, SELECT ON SEQUENCE appointments_id_seq TO odontorisas_app';
    END IF;
END $$;

-- Capacidad clínica de los miembros de la clínica activa, para agendar (design.md D4). La RLS de V28/V30
-- deja que un MEMBER vea solo SU membresía y SU credencial; la recepción necesita saber si el odontólogo
-- elegido está habilitado, sin ver los datos de su credencial. La función devuelve solo lo que la regla
-- necesita (estado de la membresía, si se retiró la capacidad, "habilitado hasta"), sin número COP, y solo a
-- un miembro activo de la clínica del token. La regla misma sigue en Java (ClinicalCapacityService).
--
-- Las dos tablas tienen FORCE ROW LEVEL SECURITY, que vale también para su dueño: en Neon el dueño
-- (odontorisas_admin) no es superusuario y un SECURITY DEFINER suyo seguiría filtrado. Por eso el dueño
-- gana una política de SOLO LECTURA para sí mismo (TO CURRENT_USER, el rol que migra). La app se conecta
-- como odontorisas_app, que no la recibe: lo que la app ve directamente no cambia.
CREATE POLICY migration_owner_read ON memberships FOR SELECT TO CURRENT_USER USING (true);
CREATE POLICY migration_owner_read ON professional_credential FOR SELECT TO CURRENT_USER USING (true);

CREATE FUNCTION clinic_capacity_facts(target_user_id BIGINT)
RETURNS TABLE (
    user_id            BIGINT,
    full_name          VARCHAR,
    membership_status  VARCHAR,
    capacity_revoked   BOOLEAN,
    licensed_until     DATE
)
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT m.user_id, u.full_name, m.status, m.clinical_capacity_revoked_at IS NOT NULL, c.licensed_until
    FROM memberships m
    JOIN users u ON u.id = m.user_id
    LEFT JOIN professional_credential c ON c.user_id = m.user_id
    -- Estrictos (sin missing_ok): sin identidad ni clínica activa, la función falla cerrada.
    WHERE m.organization_id = current_setting('app.organization_id')::BIGINT
      AND (target_user_id IS NULL OR m.user_id = target_user_id)
      AND EXISTS (
          SELECT 1 FROM memberships caller
          WHERE caller.user_id = current_setting('app.user_id')::BIGINT
            AND caller.organization_id = m.organization_id
            AND caller.status = 'ACTIVE'
      )
$$;

REVOKE ALL ON FUNCTION clinic_capacity_facts(BIGINT) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app') THEN
        EXECUTE 'GRANT EXECUTE ON FUNCTION clinic_capacity_facts(BIGINT) TO odontorisas_app';
    END IF;
END $$;

-- Horario de atención por día, cero o más tramos (D5). Lunes a viernes 09:00-18:00, sábado 09:00-13:00,
-- domingo cerrado.
ALTER TABLE organization_settings ADD COLUMN business_hours JSONB NOT NULL DEFAULT '{
  "MONDAY":    [{"opens": "09:00", "closes": "18:00"}],
  "TUESDAY":   [{"opens": "09:00", "closes": "18:00"}],
  "WEDNESDAY": [{"opens": "09:00", "closes": "18:00"}],
  "THURSDAY":  [{"opens": "09:00", "closes": "18:00"}],
  "FRIDAY":    [{"opens": "09:00", "closes": "18:00"}],
  "SATURDAY":  [{"opens": "09:00", "closes": "13:00"}],
  "SUNDAY":    []
}'::jsonb;
