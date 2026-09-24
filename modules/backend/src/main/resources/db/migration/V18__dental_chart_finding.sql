-- Capacidad: add-dental-chart — hallazgos del odontograma (design.md D2/D3/D6/D9).
--
-- Cada fila es UN hallazgo clínico observado, localizado según una de cinco formas (ADR-0017 D4).
-- Mismo aislamiento que dental_chart_block: organization_id propio, RLS propia — la RLS de
-- Postgres NO ES TRANSITIVA; que esta tabla cuelgue de block_id no la protege (hallazgo pagado en
-- add-clinical-history, repetido acá adrede como recordatorio de por qué cada tabla declara la suya).

CREATE TABLE dental_chart_finding (
    id                       BIGSERIAL PRIMARY KEY,
    organization_id          BIGINT NOT NULL REFERENCES organizations (id),
    block_id                 BIGINT NOT NULL,

    -- Clave del catálogo (V16). subtype = '' cuando el tipo no tiene subtipos — no NULL, porque
    -- la PK del catálogo es (type, subtype) y NULL rompería la comparación de igualdad de la FK.
    type                     VARCHAR(48) NOT NULL,
    subtype                  VARCHAR(8)  NOT NULL DEFAULT '',

    -- Único dato de color que SE PERSISTE (ADR-0017 D1): los tipos SEGUN_ESTADO no derivan su
    -- color de nada más. Que 'condition' sea obligatoria quien tiene color_behavior=SEGUN_ESTADO
    -- es INV-11: se valida en el boundary (@ValidFindingLocation, design.md D9), porque un CHECK
    -- de SQL no puede consultar el catálogo de otra tabla.
    condition                VARCHAR(8)  CHECK (condition IS NULL OR condition IN ('GOOD', 'POOR')),

    -- Solo la movilidad lo exige (6.1.19: sigla M + dígito arábigo). Igual que 'condition', que
    -- solo la movilidad lo requiera es invariante de boundary, no de esta tabla.
    grade                    SMALLINT    CHECK (grade IS NULL OR grade BETWEEN 1 AND 3),

    -- Localización polimórfica: CINCO variantes (ADR-0017 D4), discriminador explícito + columnas
    -- nullable. Piezas como INTEGER validadas contra FdiTooth.VALID en la app — mismo patrón que
    -- clinical_history_diagnosis.fdi_number (V15): un CHECK que enumere el conjunto FDI completo
    -- no es legible.
    location_kind            VARCHAR(16) NOT NULL
                                 CHECK (location_kind IN
                                     ('SURFACE', 'TOOTH', 'BETWEEN_TEETH', 'TOOTH_RANGE', 'ARCH')),
    tooth_a                  INTEGER,
    tooth_b                  INTEGER,
    surface                  VARCHAR(4)  CHECK (surface IS NULL OR surface IN ('M','D','V','P','L','O','I')),
    arch                     VARCHAR(16) CHECK (arch IS NULL OR arch IN ('SUPERIOR', 'INFERIOR')),

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               BIGINT NOT NULL REFERENCES users (id),
    updated_by               BIGINT NOT NULL REFERENCES users (id),

    -- Permite la FK compuesta de dental_chart_finding_tooth (los rangos).
    CONSTRAINT ux_dental_chart_finding_org_id UNIQUE (organization_id, id),

    -- FK COMPUESTA por tenant (design.md D1): sin esto, una fila podría declarar una organización
    -- y apuntar a un bloque de otra. La RLS aísla lecturas y escrituras; esto ata la integridad.
    CONSTRAINT fk_dental_chart_finding_block FOREIGN KEY (organization_id, block_id)
        REFERENCES dental_chart_block (organization_id, id),

    -- El par (type, subtype) tiene que existir en el catálogo. Rechaza por construcción un subtipo
    -- ausente, uno prohibido para ese tipo, o uno que pertenece a otro tipo — los tres casos que
    -- un CHECK manual de 38 pares tendría que enumerar (design.md D6).
    CONSTRAINT fk_dental_chart_finding_catalog FOREIGN KEY (type, subtype)
        REFERENCES dental_finding_catalog (type, subtype),

    -- El CHECK de localización, A PRUEBA DE NULL (design.md D2, hallazgo de revisión externa
    -- 2026-09-23): en PostgreSQL un CHECK que evalúa a NULL se considera SATISFECHO. Con cinco
    -- columnas nullable, una expresión que pueda dar NULL no valida nada, en silencio. Por eso
    -- cada rama usa IS NULL / IS NOT NULL explícitos y NIEGA las columnas que no le corresponden.
    CONSTRAINT dental_chart_finding_location_paired CHECK (
        CASE location_kind
            WHEN 'SURFACE'       THEN tooth_a IS NOT NULL AND surface IS NOT NULL
                                      AND tooth_b IS NULL AND arch IS NULL
            WHEN 'TOOTH'         THEN tooth_a IS NOT NULL
                                      AND tooth_b IS NULL AND surface IS NULL AND arch IS NULL
            -- Orden canónico tooth_a < tooth_b (design.md D2): sin esto, (11,21) y (21,11) serían
            -- dos hallazgos distintos para el mismo diastema. Se normaliza al construir el VO Y se
            -- exige en la base, porque el orden es invariante de DATO, no solo del objeto Java.
            WHEN 'BETWEEN_TEETH' THEN tooth_a IS NOT NULL AND tooth_b IS NOT NULL
                                      AND tooth_a < tooth_b
                                      AND surface IS NULL AND arch IS NULL
            -- TOOTH_RANGE no puebla ninguna columna propia: su conjunto de piezas vive en la tabla
            -- hija dental_chart_finding_tooth (V19). Cardinalidad (≥2 piezas, sin repetidas) y "el
            -- padre es TOOTH_RANGE" no los puede expresar un CHECK — se validan en el servicio con
            -- el finding bloqueado (design.md D2).
            WHEN 'TOOTH_RANGE'   THEN tooth_a IS NULL AND tooth_b IS NULL
                                      AND surface IS NULL AND arch IS NULL
            WHEN 'ARCH'          THEN arch IS NOT NULL
                                      AND tooth_a IS NULL AND tooth_b IS NULL AND surface IS NULL
        END
    )
);

CREATE INDEX idx_dental_chart_finding_organization ON dental_chart_finding (organization_id);
CREATE INDEX idx_dental_chart_finding_block ON dental_chart_finding (block_id);
CREATE INDEX idx_dental_chart_finding_tooth_a ON dental_chart_finding (tooth_a) WHERE tooth_a IS NOT NULL;

-- Unicidad de HALLAZGOS (design.md D3, hallazgo de revisión externa 2026-09-23): el spec exige
-- rechazar el mismo type+subtype en la misma localización, y no había NINGUNA constraint que lo
-- garantizara — solo estaba la unicidad de bloques. Un índice único por variante, porque la
-- localización está repartida entre columnas nullable y un índice común no colisionaría con NULL.
--
-- TOOTH_RANGE queda FUERA a propósito: su identidad vive en la tabla hija, y un índice único no
-- puede abarcar un conjunto de filas. Es la ÚNICA variante cuya unicidad no la garantiza la base;
-- se resuelve con el finding bloqueado en la misma transacción (design.md D2/D3).
CREATE UNIQUE INDEX ux_dental_chart_finding_surface ON dental_chart_finding
    (block_id, type, subtype, tooth_a, surface) WHERE location_kind = 'SURFACE';
CREATE UNIQUE INDEX ux_dental_chart_finding_tooth ON dental_chart_finding
    (block_id, type, subtype, tooth_a) WHERE location_kind = 'TOOTH';
CREATE UNIQUE INDEX ux_dental_chart_finding_between ON dental_chart_finding
    (block_id, type, subtype, tooth_a, tooth_b) WHERE location_kind = 'BETWEEN_TEETH';
CREATE UNIQUE INDEX ux_dental_chart_finding_arch ON dental_chart_finding
    (block_id, type, subtype, arch) WHERE location_kind = 'ARCH';

ALTER TABLE dental_chart_finding ENABLE ROW LEVEL SECURITY;
ALTER TABLE dental_chart_finding FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON dental_chart_finding
    USING (organization_id = current_setting('app.organization_id')::bigint);
