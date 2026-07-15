-- Capacidad: patients — la ficha clínico-administrativa de la persona atendida.
-- PRIMERA tabla de negocio aislada por ORGANIZACIÓN (las anteriores: memberships por
-- identidad; branding/settings sin RLS por ser metadato del tenant).
--
-- Aislamiento (docs/backend.md §8.1): columna organization_id + índice + FORCE ROW LEVEL
-- SECURITY con política organization_isolation SIN missing_ok (fallo ruidoso). El contexto
-- lo fija SOLO TenantAwareTransactionManager; aquí no se escribe código de aislamiento.
-- El GRANT al rol de app lo hereda del ALTER DEFAULT PRIVILEGES de V6.

CREATE TABLE patients (
    id                             BIGSERIAL PRIMARY KEY,
    organization_id                BIGINT NOT NULL REFERENCES organizations (id),

    -- Datos personales (fullName y birthDate son los únicos obligatorios; ver design.md OQ1).
    full_name                      VARCHAR(200) NOT NULL,
    document                       VARCHAR(50),            -- opcional; único POR CLÍNICA (ADR-0004)
    birth_date                     DATE NOT NULL,          -- de ella se deriva isMinor (no se persiste)
    sex                            VARCHAR(20),

    -- Contacto.
    phone                          VARCHAR(40),
    email                          VARCHAR(255),
    address                        VARCHAR(500),

    -- Responsable / tutor (obligatorio cuando el paciente es menor; el documento del tutor es opcional).
    guardian_name                  VARCHAR(200),
    guardian_relationship          VARCHAR(50),
    guardian_document              VARCHAR(50),
    guardian_phone                 VARCHAR(40),

    -- Contacto de emergencia.
    emergency_contact_name         VARCHAR(200),
    emergency_contact_phone        VARCHAR(40),
    emergency_contact_relationship VARCHAR(50),

    -- Anamnesis como JSONB en la propia fila (design.md D7): la RLS de patients la cubre por
    -- completo; una tabla hija sin organization_id/RLS sería un agujero de aislamiento.
    -- NOT NULL DEFAULT '[]': la anamnesis vacía es una lista vacía, no ausente.
    allergies                      JSONB NOT NULL DEFAULT '[]',
    conditions                     JSONB NOT NULL DEFAULT '[]',
    current_medication             JSONB NOT NULL DEFAULT '[]',

    -- Baja lógica (design.md D4): "dar de baja" archiva, no borra. La reactivación vuelve a ACTIVE.
    status                         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- El índice sobre organization_id es PARTE de la política de aislamiento (docs/backend.md §8.1),
-- no una optimización: sin él RLS filtra con seq scan y el listado se degrada por cada clínica.
CREATE INDEX idx_patients_organization ON patients (organization_id);

-- Unicidad del documento POR CLÍNICA (ADR-0004), NUNCA sobre document a secas. INCONDICIONAL
-- (design.md D1): un documento = una ficha por clínica para siempre; la baja obliga a reactivar,
-- no a duplicar. PostgreSQL trata los NULL como distintos, así que varios pacientes sin documento
-- conviven sin chocar (el documento es opcional).
CREATE UNIQUE INDEX uq_patients_organization_document ON patients (organization_id, document);

ALTER TABLE patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE patients FORCE ROW LEVEL SECURITY;

-- Sin WITH CHECK explícito, la expresión de USING gobierna también INSERT/UPDATE: una escritura
-- solo se permite si su organization_id coincide con el del contexto. Sin missing_ok: si
-- app.organization_id no está fijado, current_setting LANZA (fail-closed ruidoso).
CREATE POLICY organization_isolation ON patients
    USING (organization_id = current_setting('app.organization_id')::bigint);
