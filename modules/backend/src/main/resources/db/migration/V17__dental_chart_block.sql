-- Capacidad: add-dental-chart — el odontograma como documento fechado (design.md D1/D3/D5/D9).
--
-- Un bloque = un odontograma sellado en un momento del expediente. NTS N°188 lo genera POR EVENTO
-- CLÍNICO (numerales 5.10/5.11) — inicio y fin del plan, hallazgo nuevo, reingreso, pedido
-- judicial —, NO uno por día (ADR-0017 D3, corrige el bloque diario de ADR-0012 D4). En v1 solo
-- existe el tipo INITIAL; la evolución llega con update-dental-chart.
--
-- Mismo patrón de aislamiento que V14__historia_clinica.sql: organization_id + índice +
-- FORCE ROW LEVEL SECURITY + policy organization_isolation, sin missing_ok.

-- FK COMPUESTA por tenant (design.md D1, hallazgo de revisión externa 2026-09-23): una FK por id
-- sola permite que una fila declare una organización y apunte a un paciente de otra. patients
-- (V9) no tenía esta UNIQUE porque nada la necesitaba todavía — se agrega ahora, de forma aditiva,
-- para sostener la FK compuesta de abajo. No toca datos existentes ni cambia comportamiento.
ALTER TABLE patients ADD CONSTRAINT ux_patients_org_id UNIQUE (organization_id, id);

CREATE TABLE dental_chart_block (
    id                       BIGSERIAL PRIMARY KEY,
    organization_id          BIGINT NOT NULL REFERENCES organizations (id),
    patient_id               BIGINT NOT NULL,

    type                     VARCHAR(20) NOT NULL DEFAULT 'INITIAL'
                                 CHECK (type IN ('INITIAL')),
    status                   VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                                 CHECK (status IN ('OPEN', 'SEALED')),

    -- Fecha del evento clínico que generó el bloque (NTS 188 5.10/5.11), no la fecha de creación
    -- de la fila.
    examined_on              DATE NOT NULL,
    sealed_at                TIMESTAMPTZ,

    -- Dos campos de texto DISTINTOS (ADR-0017 D6): especificaciones detalla lo que SÍ está en el
    -- catálogo pero no cabe dibujado (5.14); observaciones es la vía para lo que la nomenclatura
    -- NO contempla (5.15). Pares revisado/detalle, mismo patrón que V14 línea 26
    -- (alergias_revisado): "revisado y sin nada que anotar" es un dato clínico válido, distinto de
    -- "nunca revisado" (design.md D5).
    specifications           TEXT,
    specifications_reviewed  BOOLEAN NOT NULL DEFAULT false,
    observations             TEXT,
    observations_reviewed    BOOLEAN NOT NULL DEFAULT false,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               BIGINT NOT NULL REFERENCES users (id),
    updated_by               BIGINT NOT NULL REFERENCES users (id),

    -- Permite la FK compuesta de dental_chart_finding.
    CONSTRAINT ux_dental_chart_block_org_id UNIQUE (organization_id, id),

    CONSTRAINT fk_dental_chart_block_patient FOREIGN KEY (organization_id, patient_id)
        REFERENCES patients (organization_id, id),

    -- Coherencia del sellado (design.md D9): SEALED sin fecha, u OPEN con fecha, son estados
    -- imposibles como dato — no dependen de que el servicio los evite.
    CONSTRAINT dental_chart_block_seal_coherent CHECK (
        (status = 'OPEN'   AND sealed_at IS NULL)
        OR (status = 'SEALED' AND sealed_at IS NOT NULL)
    ),

    -- "Revisado=false + texto" es imposible (design.md D5): no tiene sentido tener contenido en
    -- una sección que nunca se marcó como revisada.
    CONSTRAINT dental_chart_block_specifications_paired CHECK (
        specifications IS NULL OR specifications_reviewed
    ),
    CONSTRAINT dental_chart_block_observations_paired CHECK (
        observations IS NULL OR observations_reviewed
    )
);

CREATE INDEX idx_dental_chart_block_organization ON dental_chart_block (organization_id);

-- Índices ÚNICOS PARCIALES (design.md D3): un exists previo pierde ante peticiones concurrentes
-- (precedente pagado: add-clinical-history, 8 hilos reales contra Postgres).
--   ux_..._open:    un bloque ABIERTO por paciente — la cronología de ADR-0012 D7; ya NO es "por
--                   día" (ADR-0017 D3). Casi redundante con el de INITIAL mientras solo exista ese
--                   tipo; deja de serlo cuando update-dental-chart traiga varios bloques por
--                   paciente, así que se mantiene desde ahora.
--   ux_..._initial: un solo INITIAL por paciente, para siempre (no se reabre, D9).
CREATE UNIQUE INDEX ux_dental_chart_block_open    ON dental_chart_block (patient_id) WHERE status = 'OPEN';
CREATE UNIQUE INDEX ux_dental_chart_block_initial ON dental_chart_block (patient_id) WHERE type = 'INITIAL';

ALTER TABLE dental_chart_block ENABLE ROW LEVEL SECURITY;
ALTER TABLE dental_chart_block FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON dental_chart_block
    USING (organization_id = current_setting('app.organization_id')::bigint);
