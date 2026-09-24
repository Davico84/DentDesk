-- Capacidad: add-dental-chart — piezas de los hallazgos de RANGO (design.md D1/D2).
--
-- TOOTH_RANGE (prótesis parcial fija, ortodoncia fija) comprende un CONJUNTO ORDENADO de piezas,
-- de cardinalidad variable — no cabe en columnas planas. 'position' SÍ es dato (identifica los
-- pilares, ADR-0017 D4 nota final de D2): NO se normaliza como el orden de BETWEEN_TEETH.
--
-- RLS PROPIA: la de dental_chart_finding no la protege (la RLS de Postgres no se hereda por FK —
-- mismo hallazgo pagado en add-clinical-history, y repetido en block y en finding de este change).
--
-- AUTORÍA COMPLETA, aunque estas filas nazcan y mueran con su finding (sin edición independiente):
-- el candado ArchUnit business_entities_with_tenant_id_must_declare_authorship es INCONDICIONAL
-- para toda entidad con @TenantId, sin excepción por "el padre ya la cubre".

CREATE TABLE dental_chart_finding_tooth (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations (id),
    finding_id        BIGINT NOT NULL,

    -- Empieza en 1, sin huecos exigidos por la base (el servicio los evita al construir el rango,
    -- pero la BD solo garantiza "positivo y no repetido" — ver invariantes de cardinalidad abajo).
    position          INTEGER NOT NULL CHECK (position >= 1),
    tooth             INTEGER NOT NULL,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT NOT NULL REFERENCES users (id),
    updated_by        BIGINT NOT NULL REFERENCES users (id),

    -- FK COMPUESTA por tenant (design.md D1): ata el tenant de la pieza al de su hallazgo.
    CONSTRAINT fk_dental_chart_finding_tooth_finding FOREIGN KEY (organization_id, finding_id)
        REFERENCES dental_chart_finding (organization_id, id),

    -- Invariantes de la tabla hija (design.md D2, hallazgo de revisión externa 2026-09-23):
    --   ux_..._position: 'position' no se repite dentro del mismo hallazgo.
    --   ux_..._tooth:    la MISMA PIEZA no se repite dentro del mismo rango — faltaba.
    CONSTRAINT ux_dental_chart_finding_tooth_position UNIQUE (finding_id, position),
    CONSTRAINT ux_dental_chart_finding_tooth_tooth    UNIQUE (finding_id, tooth)
);

-- La consulta "¿qué hallazgos tocan la pieza 36?" es la razón de ser de esta tabla frente a un
-- array o una lista separada por comas — y no tenía índice para responderla (design.md D2).
CREATE INDEX idx_dental_chart_finding_tooth_organization ON dental_chart_finding_tooth (organization_id);
CREATE INDEX idx_dental_chart_finding_tooth_finding ON dental_chart_finding_tooth (finding_id);
CREATE INDEX idx_dental_chart_finding_tooth_tooth ON dental_chart_finding_tooth (tooth);

-- Dos invariantes que NINGÚN CHECK puede expresar aquí, porque son de cardinalidad entre filas
-- (un CHECK en esta tabla no ve el location_kind del padre; uno en el padre no ve las filas
-- hijas): que solo un TOOTH_RANGE tenga filas, y que un rango tenga al menos dos piezas. Se
-- validan en el servicio con el finding bloqueado (SELECT ... FOR UPDATE) antes de confirmar el
-- conjunto completo, en la misma transacción en la que el finding nace (design.md D2).

ALTER TABLE dental_chart_finding_tooth ENABLE ROW LEVEL SECURITY;
ALTER TABLE dental_chart_finding_tooth FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_isolation ON dental_chart_finding_tooth
    USING (organization_id = current_setting('app.organization_id')::bigint);
