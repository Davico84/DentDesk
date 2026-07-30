-- Capacidad: add-clinical-history — diagnósticos de la Historia Clínica (design.md D2/D2b/D5/D11).
--
-- Misma disciplina de aislamiento que historia_clinica (D2b): organization_id propio, RLS propia
-- (no se hereda de historia_clinica por la FK). Norma MINSA HIS (D11): tipo_diagnostico
-- obligatorio, created_at/created_by inmutables (la fila no se reemplaza, se agrega/elimina —
-- design.md D5, solo mientras la Historia esté en DRAFT).

CREATE TABLE clinical_history_diagnosis (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations (id),
    historia_id       BIGINT NOT NULL REFERENCES historia_clinica (id),

    cie10_code        VARCHAR(20) NOT NULL REFERENCES cie10_code (code),
    ubicacion         VARCHAR(20) NOT NULL CHECK (ubicacion IN ('GENERAL', 'PIEZA')),
    -- FDI: obligatorio si ubicacion = PIEZA, ausente si GENERAL (validado en la app, D5 — un CHECK
    -- de SQL no puede referenciar el conjunto completo de piezas válidas con legibilidad razonable).
    fdi_number        INTEGER,
    tipo_diagnostico  VARCHAR(20) NOT NULL CHECK (tipo_diagnostico IN ('PRESUNTIVO', 'DEFINITIVO', 'REPETIDO')),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT NOT NULL REFERENCES users (id),
    updated_by        BIGINT NOT NULL REFERENCES users (id),

    -- Ubicación y FDI van EMPAREJADOS en el sentido correcto (D5): PIEZA exige fdi, GENERAL lo prohíbe.
    CONSTRAINT clinical_history_diagnosis_location_paired CHECK (
        (ubicacion = 'PIEZA' AND fdi_number IS NOT NULL)
        OR (ubicacion = 'GENERAL' AND fdi_number IS NULL)
    )
);

CREATE INDEX idx_clinical_history_diagnosis_organization ON clinical_history_diagnosis (organization_id);
CREATE INDEX idx_clinical_history_diagnosis_historia ON clinical_history_diagnosis (historia_id);

ALTER TABLE clinical_history_diagnosis ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_history_diagnosis FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON clinical_history_diagnosis
    USING (organization_id = current_setting('app.organization_id')::bigint);
