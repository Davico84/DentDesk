-- Capacidad: update-clinical-history (fase 2, corte A) — la nota de consulta y lo que cuelga de ella
-- (design.md D1–D8, ADR-0020).
--
-- Cuatro tablas, todas con el mismo aislamiento que V17–V19: organization_id propio, FK COMPUESTA
-- por tenant (una fila no puede declarar una organización y apuntar a una fila de otra), índice por
-- organización y FORCE ROW LEVEL SECURITY con la policy organization_isolation.
--
--   consultation_note             una por atención posterior a la admisión (ABIERTA -> SELLADA)
--   consultation_note_diagnosis   CIE10 + P/D/R, misma forma que clinical_history_diagnosis (V15)
--   consultation_note_correction  error material corregido como registro nuevo (DS 009 art. 47 c)
--   clinical_background_entry     antecedentes nuevos como asientos de alta y baja
--
-- La inmutabilidad del sellado vive en V22 (triggers). Acá solo la coherencia de cada fila.

CREATE TABLE consultation_note (
    id                      BIGSERIAL PRIMARY KEY,
    organization_id         BIGINT NOT NULL REFERENCES organizations (id),
    patient_id              BIGINT NOT NULL,

    status                  VARCHAR(20) NOT NULL DEFAULT 'ABIERTA'
                                CHECK (status IN ('ABIERTA', 'SELLADA')),

    -- Cuándo ocurrió la atención (lo ingresa el odontólogo) — distinto de created_at, que es cuándo
    -- se escribió (design.md D4).
    attended_at             TIMESTAMPTZ NOT NULL,

    -- Contenido de NTS 139 §5.2.1 (1), unión de 1.1 d y 1.2 (design.md D3). Todos nulos: la nota
    -- abierta es un borrador; el checklist de sellado vive en el servicio.
    reason                  TEXT,
    illness_duration        TEXT,
    symptoms_and_signs      TEXT,
    previous_treatment      TEXT,
    biological_functions    TEXT,
    examination             TEXT,
    treatment               TEXT,
    auxiliary_exams         TEXT,
    referral                TEXT,
    -- Posterior al día de la atención: lo valida el servicio, en la zona horaria de la clínica (un
    -- CHECK en UTC se equivocaría con las atenciones de la noche).
    next_appointment_on     DATE,

    sealed_at               TIMESTAMPTZ,
    sealed_by               BIGINT REFERENCES users (id),

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              BIGINT NOT NULL REFERENCES users (id),
    updated_by              BIGINT NOT NULL REFERENCES users (id),

    CONSTRAINT ux_consultation_note_org_id UNIQUE (organization_id, id),

    CONSTRAINT fk_consultation_note_patient FOREIGN KEY (organization_id, patient_id)
        REFERENCES patients (organization_id, id),

    -- SELLADA sin fecha o sin firmante, o ABIERTA con ellos, son estados imposibles como dato.
    CONSTRAINT consultation_note_seal_coherent CHECK (
        (status = 'ABIERTA' AND sealed_at IS NULL AND sealed_by IS NULL)
        OR (status = 'SELLADA' AND sealed_at IS NOT NULL AND sealed_by IS NOT NULL)
    )
);

CREATE INDEX idx_consultation_note_organization ON consultation_note (organization_id);
CREATE INDEX idx_consultation_note_patient ON consultation_note (patient_id, attended_at);

-- Una nota ABIERTA por paciente (design.md D1/D2). Índice parcial y no un exists previo: el exists
-- pierde ante peticiones concurrentes (precedente: add-clinical-history, V17).
CREATE UNIQUE INDEX ux_consultation_note_open ON consultation_note (patient_id) WHERE status = 'ABIERTA';

ALTER TABLE consultation_note ENABLE ROW LEVEL SECURITY;
ALTER TABLE consultation_note FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON consultation_note
    USING (organization_id = current_setting('app.organization_id')::bigint);


CREATE TABLE consultation_note_diagnosis (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations (id),
    note_id           BIGINT NOT NULL,

    cie10_code        VARCHAR(20) NOT NULL REFERENCES cie10_code (code),
    ubicacion         VARCHAR(20) NOT NULL CHECK (ubicacion IN ('GENERAL', 'PIEZA')),
    fdi_number        INTEGER,
    tipo_diagnostico  VARCHAR(20) NOT NULL CHECK (tipo_diagnostico IN ('PRESUNTIVO', 'DEFINITIVO', 'REPETIDO')),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT NOT NULL REFERENCES users (id),
    updated_by        BIGINT NOT NULL REFERENCES users (id),

    CONSTRAINT fk_consultation_note_diagnosis_note FOREIGN KEY (organization_id, note_id)
        REFERENCES consultation_note (organization_id, id),

    CONSTRAINT consultation_note_diagnosis_location_paired CHECK (
        (ubicacion = 'PIEZA' AND fdi_number IS NOT NULL)
        OR (ubicacion = 'GENERAL' AND fdi_number IS NULL)
    )
);

CREATE INDEX idx_consultation_note_diagnosis_organization ON consultation_note_diagnosis (organization_id);
CREATE INDEX idx_consultation_note_diagnosis_note ON consultation_note_diagnosis (note_id);

ALTER TABLE consultation_note_diagnosis ENABLE ROW LEVEL SECURITY;
ALTER TABLE consultation_note_diagnosis FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON consultation_note_diagnosis
    USING (organization_id = current_setting('app.organization_id')::bigint);


-- Corrección de error material (DS 009-2017-SA art. 47 c; NTS 139 §5.3.3 j): registro NUEVO que
-- indica el campo (la "ubicación del error"), el texto corregido y el motivo. Nunca se modifica ni
-- se borra (V22). Los diagnósticos no figuran entre los campos: el art. 47 c los excluye.
CREATE TABLE consultation_note_correction (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations (id),
    note_id           BIGINT NOT NULL,

    field             VARCHAR(30) NOT NULL CHECK (field IN (
                          'REASON', 'ILLNESS_DURATION', 'SYMPTOMS_AND_SIGNS', 'PREVIOUS_TREATMENT',
                          'BIOLOGICAL_FUNCTIONS', 'EXAMINATION', 'TREATMENT', 'AUXILIARY_EXAMS',
                          'REFERRAL')),
    corrected_text    TEXT NOT NULL CHECK (btrim(corrected_text) <> ''),
    reason            TEXT NOT NULL CHECK (btrim(reason) <> ''),

    -- updated_* existen por la regla de autoría completa (docs/backend.md §8.6); como la fila nunca
    -- se actualiza (V22), siempre valen lo mismo que created_*.
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT NOT NULL REFERENCES users (id),
    updated_by        BIGINT NOT NULL REFERENCES users (id),

    CONSTRAINT fk_consultation_note_correction_note FOREIGN KEY (organization_id, note_id)
        REFERENCES consultation_note (organization_id, id)
);

CREATE INDEX idx_consultation_note_correction_organization ON consultation_note_correction (organization_id);
CREATE INDEX idx_consultation_note_correction_note ON consultation_note_correction (note_id);

ALTER TABLE consultation_note_correction ENABLE ROW LEVEL SECURITY;
ALTER TABLE consultation_note_correction FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON consultation_note_correction
    USING (organization_id = current_setting('app.organization_id')::bigint);


-- Antecedentes nuevos como libro de asientos (design.md D7, mismo patrón que ADR-0012 D3): un ALTA
-- trae la descripción; una BAJA apunta al alta que cierra. Lo vigente se calcula, no se guarda.
CREATE TABLE clinical_background_entry (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations (id),
    patient_id        BIGINT NOT NULL,
    note_id           BIGINT NOT NULL,

    category          VARCHAR(30) NOT NULL
                          CHECK (category IN ('ALERGIA_RAM', 'ANTECEDENTE_PATOLOGICO', 'MEDICACION')),
    kind              VARCHAR(10) NOT NULL CHECK (kind IN ('ALTA', 'BAJA')),
    description       TEXT,
    closes_entry_id   BIGINT,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT NOT NULL REFERENCES users (id),
    updated_by        BIGINT NOT NULL REFERENCES users (id),

    CONSTRAINT ux_clinical_background_entry_org_id UNIQUE (organization_id, id),

    CONSTRAINT fk_clinical_background_entry_patient FOREIGN KEY (organization_id, patient_id)
        REFERENCES patients (organization_id, id),
    CONSTRAINT fk_clinical_background_entry_note FOREIGN KEY (organization_id, note_id)
        REFERENCES consultation_note (organization_id, id),
    CONSTRAINT fk_clinical_background_entry_closes FOREIGN KEY (organization_id, closes_entry_id)
        REFERENCES clinical_background_entry (organization_id, id),

    -- ALTA: descripción y sin destino. BAJA: destino; la descripción es opcional (por qué se da de baja).
    CONSTRAINT clinical_background_entry_kind_paired CHECK (
        (kind = 'ALTA' AND closes_entry_id IS NULL AND description IS NOT NULL AND btrim(description) <> '')
        OR (kind = 'BAJA' AND closes_entry_id IS NOT NULL)
    )
);

CREATE INDEX idx_clinical_background_entry_organization ON clinical_background_entry (organization_id);
CREATE INDEX idx_clinical_background_entry_patient ON clinical_background_entry (patient_id);
CREATE INDEX idx_clinical_background_entry_note ON clinical_background_entry (note_id);

-- Un alta se da de baja una sola vez (design.md D7).
CREATE UNIQUE INDEX ux_clinical_background_entry_closes ON clinical_background_entry (closes_entry_id)
    WHERE closes_entry_id IS NOT NULL;

ALTER TABLE clinical_background_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_background_entry FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON clinical_background_entry
    USING (organization_id = current_setting('app.organization_id')::bigint);
