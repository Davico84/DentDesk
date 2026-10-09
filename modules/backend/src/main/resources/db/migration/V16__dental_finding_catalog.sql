-- Capacidad: add-dental-chart — catálogo de hallazgos del odontograma (design.md D6).
--
-- Las 38 entradas de la nomenclatura de NTS N°188-MINSA/DGIESP-2022, numerales 6.1.1–6.1.38,
-- verificadas ENTRADA POR ENTRADA contra el texto oficial el 2026-09-23 (tarea 0.7; el PDF está
-- versionado en docs/compliance/normas/). ADR-0017 D7 tiene la misma tabla en prosa.
--
-- POR QUÉ ES UNA TABLA Y NO UN CHECK (design.md D6): un CHECK con 38 tipos y todos sus pares crece
-- mal y no se puede auditar sin leer el código. Con tabla, la BD rechaza por construcción el subtipo
-- ausente, el prohibido y el que pertenece a otro tipo — los tres casos que el CHECK tendría que
-- enumerar. Mismo patrón que V13__cie10_catalog.sql: catálogo de referencia cargado por migración.
--
-- NO lleva organization_id ni RLS: es catálogo normativo, idéntico para todas las clínicas, igual
-- que cie10_code. La RLS protege datos de pacientes, no la nomenclatura del MINSA.

CREATE TABLE dental_finding_catalog (
    -- '' (cadena vacía) cuando el tipo no tiene subtipos. No NULL: forma parte de la PK, y en
    -- PostgreSQL dos NULL no son iguales, así que un NULL aquí rompería la unicidad del par.
    type                   VARCHAR(48) NOT NULL,
    subtype                VARCHAR(8)  NOT NULL,

    -- El numeral SE GUARDA DOS VECES, a propósito (design.md D6): la norma tiene DOS TYPOS y los
    -- imprime así. Si se guardara solo el corregido, el sistema dejaría de coincidir con el
    -- documento que un auditor tiene en la mano; si solo el impreso, no se puede ordenar ni validar
    -- la secuencia. Los dos typos verificados: pieza en erupción sale '5.2.23' (pág. 14) y el
    -- implante dental sale '6.115' (pág. 11).
    official_numeral       VARCHAR(12) NOT NULL,
    numeral_by_position    VARCHAR(12) NOT NULL,

    -- Sigla oficial. DATO NORMATIVO: se imprime exacta, no es una etiqueta de UI.
    -- ⚠️ SIN UNIQUE, y no es un descuido: la sigla COLISIONA en el catálogo oficial.
    --   'M' es Movilidad patológica (6.1.19, rojo, con dígito de grado) y también Mesializado
    --       (subtipo de Posición anormal, 6.1.28, azul, sola).
    --   'S' es Pieza supernumeraria (6.1.26, entre piezas) y también Sellante (6.1.35, superficie).
    -- Un índice único sobre esta columna parecería razonable y RECHAZARÍA REGISTROS VÁLIDOS.
    official_acronym       VARCHAR(8),

    scope                  VARCHAR(16) NOT NULL
                                CHECK (scope IN ('SURFACE', 'TOOTH', 'BETWEEN_TEETH',
                                                 'TOOTH_RANGE', 'ARCH')),

    -- Tres comportamientos (ADR-0017 D1). 'SIEMPRE_ROJO' incluye lo temporal: la norma no tiene un
    -- eje "temporal" aparte, son tipos distintos que declaran rojo.
    color_behavior         VARCHAR(16) NOT NULL
                                CHECK (color_behavior IN ('SIEMPRE_AZUL', 'SIEMPRE_ROJO',
                                                          'SEGUN_ESTADO')),

    requires_grade         BOOLEAN NOT NULL DEFAULT false,
    requires_specification BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT pk_dental_finding_catalog PRIMARY KEY (type, subtype)
);

COMMENT ON TABLE dental_finding_catalog IS
    'Nomenclatura del odontograma, NTS N°188-MINSA/DGIESP-2022 numerales 6.1.1-6.1.38. '
    'Catálogo normativo: sin organization_id ni RLS.';

-- Solo la movilidad lleva grado: 6.1.19 dice "la sigla M seguida del número arábigo que representa
-- el grado de movilidad". Ningún otro tipo del catálogo lo admite.
CREATE INDEX idx_dental_finding_catalog_scope ON dental_finding_catalog (scope);

-- ---------------------------------------------------------------------------------------------
-- Las 38 entradas, en el ORDEN ALFABÉTICO de la norma.
--
-- Ese orden no es cosmético: es lo que hace demostrable que un hallazgo NO está en el catálogo.
-- La recesión gingival, por ejemplo, tendría que estar entre 6.1.31 (Prótesis dental parcial
-- removible) y 6.1.32 (Remanente radicular) — "Rece" ordena antes de "Rema" — y ahí no hay hueco.
-- Por eso ADR-0017 D8 pudo concluir que no existe en NTS 188, en vez de suponerlo.
--
-- requires_specification se marca SOLO donde el texto oficial lo dice explícitamente:
--   6.1.3  corona          — "se registra el color del metal de la corona, dorada o plateada"
--   6.1.4  corona temporal — "se coloca la característica o material utilizado"
--   6.1.5  DDE             — "Fluorosis: se detalla en el ítem especificaciones"
-- ⚠️ ADR-0017 D6 daba la movilidad como ejemplo de requiresSpecification. No se marca: 6.1.19 pone
-- el grado EN LA SIGLA (M1/M2/M3), no en especificaciones. Si se quiere exigir además una
-- clasificación en texto, es una decisión de producto y necesita apoyo en el texto.
-- ---------------------------------------------------------------------------------------------

INSERT INTO dental_finding_catalog
    (type, subtype, official_numeral, numeral_by_position, official_acronym,
     scope, color_behavior, requires_grade, requires_specification)
VALUES
-- 6.1.1 / 6.1.2 — Aparato ortodóntico. Fijo abarca un RANGO (la norma dibuja los extremos unidos);
-- removible es del ARCO completo (línea en zig-zag a la altura de los ápices).
    ('APARATO_ORTODONTICO_FIJO',      '',    '6.1.1',  '6.1.1',  NULL,  'TOOTH_RANGE',   'SEGUN_ESTADO', false, false),
    ('APARATO_ORTODONTICO_REMOVIBLE', '',    '6.1.2',  '6.1.2',  NULL,  'ARCH',          'SEGUN_ESTADO', false, false),

-- 6.1.3 — Corona, con cinco materiales.
    ('CORONA',                        'CM',  '6.1.3',  '6.1.3',  'CM',  'TOOTH',         'SEGUN_ESTADO', false, true),
    ('CORONA',                        'CF',  '6.1.3',  '6.1.3',  'CF',  'TOOTH',         'SEGUN_ESTADO', false, true),
    ('CORONA',                        'CMC', '6.1.3',  '6.1.3',  'CMC', 'TOOTH',         'SEGUN_ESTADO', false, true),
    ('CORONA',                        'CV',  '6.1.3',  '6.1.3',  'CV',  'TOOTH',         'SEGUN_ESTADO', false, true),
    ('CORONA',                        'CLM', '6.1.3',  '6.1.3',  'CLM', 'TOOTH',         'SEGUN_ESTADO', false, true),

-- 6.1.4 — Corona temporal. SIEMPRE roja: lo temporal no es un eje, es un tipo propio.
    ('CORONA_TEMPORAL',               '',    '6.1.4',  '6.1.4',  'CT',  'TOOTH',         'SIEMPRE_ROJO', false, true),

-- 6.1.5 — Defectos de desarrollo del esmalte. Por superficie.
    ('DEFECTOS_DESARROLLO_ESMALTE',   'O',   '6.1.5',  '6.1.5',  'O',   'SURFACE',       'SIEMPRE_ROJO', false, true),
    ('DEFECTOS_DESARROLLO_ESMALTE',   'PE',  '6.1.5',  '6.1.5',  'PE',  'SURFACE',       'SIEMPRE_ROJO', false, true),

-- 6.1.6 — Diastema. Entre piezas: no tiene número FDI propio.
    ('DIASTEMA',                      '',    '6.1.6',  '6.1.6',  NULL,  'BETWEEN_TEETH', 'SIEMPRE_AZUL', false, false),

-- 6.1.7 — Edéntulo total. Del arco.
    ('EDENTULO_TOTAL',                '',    '6.1.7',  '6.1.7',  NULL,  'ARCH',          'SIEMPRE_AZUL', false, false),

    ('ESPIGO_MUNON',                  '',    '6.1.8',  '6.1.8',  NULL,  'TOOTH',         'SEGUN_ESTADO', false, false),
    ('FOSAS_FISURAS_PROFUNDAS',       '',    '6.1.9',  '6.1.9',  'FFP', 'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('FRACTURA_DENTAL',               '',    '6.1.10', '6.1.10', NULL,  'TOOTH',         'SIEMPRE_ROJO', false, false),

-- 6.1.11 — Fusión. Entre piezas: son dos piezas unidas.
    ('FUSION',                        '',    '6.1.11', '6.1.11', NULL,  'BETWEEN_TEETH', 'SIEMPRE_AZUL', false, false),
    ('GEMINACION',                    '',    '6.1.12', '6.1.12', NULL,  'TOOTH',         'SIEMPRE_AZUL', false, false),

-- 6.1.13 — Giroversión. AZUL, SIN CONDICIONAL: el texto dice "se dibuja una flecha curva de color
-- azul siguiendo el sentido de la giroversión" y no menciona ninguna regla de contagio. La que
-- ADR-0012 D2 había supuesto NO EXISTE en la norma (ADR-0017 D2).
    ('GIROVERSION',                   '',    '6.1.13', '6.1.13', NULL,  'TOOTH',         'SIEMPRE_AZUL', false, false),

    ('IMPACTACION',                   '',    '6.1.14', '6.1.14', 'I',   'TOOTH',         'SIEMPRE_AZUL', false, false),

-- 6.115 [sic] — TYPO DE LA NORMA (pág. 11). Por posición corresponde a 6.1.15.
-- El implante COEXISTE con la ausencia de la pieza, no la implica: "sólo se registra cuando se
-- observe clínicamente la presencia del implante". Una invariante que lo forzara habría rechazado
-- registros válidos (ADR-0017 D7, hueco D2 de la matriz).
    ('IMPLANTE_DENTAL',               '',    '6.115',  '6.1.15', 'IMP', 'TOOTH',         'SEGUN_ESTADO', false, false),

-- 6.1.16 — Lesión de caries dental, por profundidad. Por SUPERFICIE.
    ('LESION_CARIES',                 'MB',  '6.1.16', '6.1.16', 'MB',  'SURFACE',       'SIEMPRE_ROJO', false, false),
    ('LESION_CARIES',                 'CE',  '6.1.16', '6.1.16', 'CE',  'SURFACE',       'SIEMPRE_ROJO', false, false),
    ('LESION_CARIES',                 'CD',  '6.1.16', '6.1.16', 'CD',  'SURFACE',       'SIEMPRE_ROJO', false, false),
    ('LESION_CARIES',                 'CDP', '6.1.16', '6.1.16', 'CDP', 'SURFACE',       'SIEMPRE_ROJO', false, false),

    ('MACRODONCIA',                   '',    '6.1.17', '6.1.17', 'MAC', 'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('MICRODONCIA',                   '',    '6.1.18', '6.1.18', 'MIC', 'TOOTH',         'SIEMPRE_AZUL', false, false),

-- 6.1.19 — Movilidad patológica. ROJA, no azul: "se registra en mayúscula y de color rojo, la sigla
-- M seguida del número arábigo que representa el grado". Corrige a ADR-0013, que la había cerrado
-- como azul citando un análisis derivado (ADR-0017, lección registrada).
-- ⚠️ Su sigla 'M' colisiona con el subtipo 'M' (Mesializado) de 6.1.28.
    ('MOVILIDAD_PATOLOGICA',          '',    '6.1.19', '6.1.19', 'M',   'TOOTH',         'SIEMPRE_ROJO', true,  false),

-- 6.1.20 — Pieza ausente, con la causa como subtipo.
    ('PIEZA_AUSENTE',                 'DNE', '6.1.20', '6.1.20', 'DNE', 'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('PIEZA_AUSENTE',                 'DEX', '6.1.20', '6.1.20', 'DEX', 'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('PIEZA_AUSENTE',                 'DAO', '6.1.20', '6.1.20', 'DAO', 'TOOTH',         'SIEMPRE_AZUL', false, false),

    ('PIEZA_EN_CLAVIJA',              '',    '6.1.21', '6.1.21', NULL,  'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('PIEZA_ECTOPICA',                '',    '6.1.22', '6.1.22', 'E',   'TOOTH',         'SIEMPRE_AZUL', false, false),

-- 5.2.23 [sic] — TYPO DE LA NORMA (pág. 14): aparece entre 6.1.22 y 6.1.24.
    ('PIEZA_EN_ERUPCION',             '',    '5.2.23', '6.1.23', NULL,  'TOOTH',         'SIEMPRE_AZUL', false, false),

-- 6.1.24 / 6.1.25 — AZULES, no rojas. El análisis derivado las daba en rojo (ADR-0017 D7).
    ('PIEZA_EXTRUIDA',                '',    '6.1.24', '6.1.24', NULL,  'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('PIEZA_INTRUIDA',                '',    '6.1.25', '6.1.25', NULL,  'TOOTH',         'SIEMPRE_AZUL', false, false),

-- 6.1.26 — Supernumeraria. ENTRE PIEZAS: la norma la localiza "entre los ápices de las piezas
-- dentarias adyacentes", porque no tiene número FDI propio.
-- ⚠️ Su sigla 'S' colisiona con la del sellante (6.1.35).
    ('PIEZA_SUPERNUMERARIA',          '',    '6.1.26', '6.1.26', 'S',   'BETWEEN_TEETH', 'SIEMPRE_AZUL', false, false),

    ('PULPOTOMIA',                    '',    '6.1.27', '6.1.27', 'PP',  'TOOTH',         'SEGUN_ESTADO', false, false),

-- 6.1.28 — Posición anormal dentaria, con la dirección como subtipo.
-- ⚠️ Las siglas coinciden con letras de superficie (M/D/V/P/L) y NO son superficies: acá 'M' es
-- mesializado, no mesial. El alcance del tipo (TOOTH) es lo que las desambigua.
    ('POSICION_ANORMAL',              'M',   '6.1.28', '6.1.28', 'M',   'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('POSICION_ANORMAL',              'D',   '6.1.28', '6.1.28', 'D',   'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('POSICION_ANORMAL',              'V',   '6.1.28', '6.1.28', 'V',   'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('POSICION_ANORMAL',              'P',   '6.1.28', '6.1.28', 'P',   'TOOTH',         'SIEMPRE_AZUL', false, false),
    ('POSICION_ANORMAL',              'L',   '6.1.28', '6.1.28', 'L',   'TOOTH',         'SIEMPRE_AZUL', false, false),

-- 6.1.29 / 6.1.30 / 6.1.31 — Prótesis. La parcial fija abarca un RANGO (los pilares son dato, por
-- eso el orden se conserva); la completa es del ARCO. 6.1.31 es la PARCIAL REMOVIBLE — la matriz y
-- las tasks la daban como "recesión gingival" y era falso (tarea 0.7).
    ('PROTESIS_PARCIAL_FIJA',         '',    '6.1.29', '6.1.29', NULL,  'TOOTH_RANGE',   'SEGUN_ESTADO', false, false),
    ('PROTESIS_COMPLETA',             '',    '6.1.30', '6.1.30', NULL,  'ARCH',          'SEGUN_ESTADO', false, false),
    ('PROTESIS_PARCIAL_REMOVIBLE',    '',    '6.1.31', '6.1.31', NULL,  'TOOTH_RANGE',   'SEGUN_ESTADO', false, false),

    ('REMANENTE_RADICULAR',           '',    '6.1.32', '6.1.32', 'RR',  'TOOTH',         'SIEMPRE_ROJO', false, false),

-- 6.1.33 — Restauración definitiva, con el material como subtipo. Por superficie.
    ('RESTAURACION_DEFINITIVA',       'AM',  '6.1.33', '6.1.33', 'AM',  'SURFACE',       'SEGUN_ESTADO', false, false),
    ('RESTAURACION_DEFINITIVA',       'R',   '6.1.33', '6.1.33', 'R',   'SURFACE',       'SEGUN_ESTADO', false, false),
    ('RESTAURACION_DEFINITIVA',       'IV',  '6.1.33', '6.1.33', 'IV',  'SURFACE',       'SEGUN_ESTADO', false, false),
    ('RESTAURACION_DEFINITIVA',       'IM',  '6.1.33', '6.1.33', 'IM',  'SURFACE',       'SEGUN_ESTADO', false, false),
    ('RESTAURACION_DEFINITIVA',       'IE',  '6.1.33', '6.1.33', 'IE',  'SURFACE',       'SEGUN_ESTADO', false, false),
    ('RESTAURACION_DEFINITIVA',       'C',   '6.1.33', '6.1.33', 'C',   'SURFACE',       'SEGUN_ESTADO', false, false),

    ('RESTAURACION_TEMPORAL',         '',    '6.1.34', '6.1.34', NULL,  'SURFACE',       'SIEMPRE_ROJO', false, false),

-- 6.1.35 — Sellante. COEXISTE con una caries en la misma superficie: la norma dice que si se
-- identifica lesión cariosa "se deben adicionar las siglas correspondientes según lo establecido en
-- el numeral 6.1.16". Dos hallazgos, no uno que sustituye al otro.
    ('SELLANTE',                      '',    '6.1.35', '6.1.35', 'S',   'SURFACE',       'SEGUN_ESTADO', false, false),

-- 6.1.36 — Superficie desgastada. ALCANCE DUAL (ADR-0017 D7): el hallazgo es de SUPERFICIE — esa es
-- la verdad clínica y es lo que se persiste —, y la sigla 'DES' del recuadro de la pieza se DERIVA
-- al renderizar. El modelo no cambia por eso.
    ('SUPERFICIE_DESGASTADA',         '',    '6.1.36', '6.1.36', 'DES', 'SURFACE',       'SIEMPRE_ROJO', false, false),

-- 6.1.37 — Tratamiento de conducto / pulpectomía.
    ('TRATAMIENTO_CONDUCTO',          'TC',  '6.1.37', '6.1.37', 'TC',  'TOOTH',         'SEGUN_ESTADO', false, false),
    ('TRATAMIENTO_CONDUCTO',          'PC',  '6.1.37', '6.1.37', 'PC',  'TOOTH',         'SEGUN_ESTADO', false, false),

-- 6.1.38 — Transposición. Entre piezas: son dos que intercambiaron lugar.
    ('TRANSPOSICION_DENTARIA',        '',    '6.1.38', '6.1.38', NULL,  'BETWEEN_TEETH', 'SIEMPRE_AZUL', false, false);
