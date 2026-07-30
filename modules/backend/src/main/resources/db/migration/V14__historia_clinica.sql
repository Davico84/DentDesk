-- Capacidad: add-clinical-history — Historia Clínica (design.md D1/D2/D2b/D3).
--
-- Entidad de negocio bajo RLS, MISMO patrón que patients (V9__patients.sql) — el hecho de colgar
-- de patient_id NO exime del aislamiento: RLS es por tabla, no se hereda por FK (D2b, hallazgo de
-- review). organization_id + índice + FORCE ROW LEVEL SECURITY + policy organization_isolation,
-- sin missing_ok. Autoría completa (@CreatedBy/@LastModifiedBy vía AuditorAware) — exigida por el
-- candado ArchUnit en cuanto la entidad declara @TenantId; sin backfill que resolver (entidad
-- nueva, a diferencia de patients/V10), así que created_by/updated_by son NOT NULL desde ya.

CREATE TABLE historia_clinica (
    id                              BIGSERIAL PRIMARY KEY,
    organization_id                 BIGINT NOT NULL REFERENCES organizations (id),
    patient_id                      BIGINT NOT NULL UNIQUE REFERENCES patients (id),

    status                          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                                         CHECK (status IN ('DRAFT', 'COMPLETED')),

    -- Documento inicial (design.md D1-D5, ADR-0011).
    motivo_consulta                 TEXT,
    enfermedad_actual               TEXT,
    antecedentes_personales         TEXT,
    examen_clinico                  TEXT,
    pronostico                      TEXT,
    signos_vitales                  JSONB,

    -- "Campo vacío ≠ revisado" (design.md D3): pares revisado/detalle para las 3 secciones donde
    -- "nada" es un dato clínico, no un olvido. revisado=true + detalle=null => "sin alergias
    -- conocidas"; revisado=false => no revisado (el checklist de sellado exige revisado=true).
    alergias_revisado               BOOLEAN NOT NULL DEFAULT false,
    alergias_detalle                TEXT,
    medicacion_revisado              BOOLEAN NOT NULL DEFAULT false,
    medicacion_detalle              TEXT,
    antecedentes_familiares_revisado BOOLEAN NOT NULL DEFAULT false,
    antecedentes_familiares_detalle  TEXT,

    completed_at                    TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                      BIGINT NOT NULL REFERENCES users (id),
    updated_by                      BIGINT NOT NULL REFERENCES users (id)
);

CREATE INDEX idx_historia_clinica_organization ON historia_clinica (organization_id);

ALTER TABLE historia_clinica ENABLE ROW LEVEL SECURITY;
ALTER TABLE historia_clinica FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON historia_clinica
    USING (organization_id = current_setting('app.organization_id')::bigint);
