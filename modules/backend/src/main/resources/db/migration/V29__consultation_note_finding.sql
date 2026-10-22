-- Hallazgos del odontograma atendidos en una nota de consulta (update-consultation-note-findings,
-- design.md D1). Hija de la nota: misma RLS por clínica, FK compuestas por tenant y la misma guarda de
-- sellado que diagnósticos y antecedentes (V22), en INSERT, UPDATE y DELETE.
--
-- La descripción (pieza, cara, hallazgo, estado) se guarda como texto propio: la nota sellada se lee
-- sola, sin depender de leer el odontograma (D2). "Mismo paciente" lo garantiza el servicio: el
-- hallazgo no tiene patient_id propio, solo block_id; la base garantiza la clínica.

CREATE TABLE consultation_note_finding (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations (id),
    note_id           BIGINT NOT NULL,
    finding_id        BIGINT NOT NULL,

    description       VARCHAR(300) NOT NULL CHECK (btrim(description) <> ''),

    -- updated_* por la regla de autoría completa (docs/backend.md §8.6); la API no actualiza un
    -- vínculo (se quita y se vuelve a marcar), así que valen lo mismo que created_*.
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT NOT NULL REFERENCES users (id),
    updated_by        BIGINT NOT NULL REFERENCES users (id),

    CONSTRAINT fk_consultation_note_finding_note FOREIGN KEY (organization_id, note_id)
        REFERENCES consultation_note (organization_id, id),
    CONSTRAINT fk_consultation_note_finding_finding FOREIGN KEY (organization_id, finding_id)
        REFERENCES dental_chart_finding (organization_id, id),
    CONSTRAINT ux_consultation_note_finding UNIQUE (note_id, finding_id)
);

CREATE INDEX ix_consultation_note_finding_finding ON consultation_note_finding (finding_id);

ALTER TABLE consultation_note_finding ENABLE ROW LEVEL SECURITY;
ALTER TABLE consultation_note_finding FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON consultation_note_finding
    USING (organization_id = current_setting('app.organization_id')::bigint);

-- Con la nota SELLADA, la base rechaza cualquier escritura aun por fuera del servicio (V22).
CREATE TRIGGER trg_consultation_note_finding_seal_guard
    BEFORE INSERT OR UPDATE OR DELETE ON consultation_note_finding
    FOR EACH ROW EXECUTE FUNCTION consultation_note_child_seal_guard();

-- Los DEFAULT PRIVILEGES de V6 ya lo cubren; el bloque es idempotente y protege una base rehecha (como V26).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON consultation_note_finding TO odontorisas_app';
        EXECUTE 'GRANT USAGE, SELECT ON SEQUENCE consultation_note_finding_id_seq TO odontorisas_app';
    END IF;
END $$;
