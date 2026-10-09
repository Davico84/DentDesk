## Context

`ADR-0012` decidió el **modelo de dominio** del odontograma (aggregate, invariantes, value objects, semántica de proyección) y las nueve decisiones que lo sostienen. Este documento **no las repite**: resuelve el *cómo* implementarlas sobre el stack real.

> 🔴 **Realineado el 2026-08-03 con [`ADR-0017`](../../../docs/adr/0017-catalogo-odontograma-nts-188.md)**, escrito ya con el **texto oficial** de NTS 188 a la vista. `ADR-0012` y `ADR-0013` se habían apoyado en análisis derivados de la norma. Lo que cambió acá: **D2** (cinco formas de localización, no tres), **D6** (catálogo `tipo` + `subtipo` con sigla, y tres comportamientos de color), **D10** (colores corregidos, sin regla de contagio) y las Open Questions 1–3, ya cerradas.

Estado del que se parte: `clinical-history` está construida, archivada y testeada (sellado `DRAFT→COMPLETED` con checklist server-side); `FdiTooth` ya existe con el conjunto real de 52 piezas; el aislamiento por organización es automático por transacción (`TenantAwareTransactionManager`) pero **cada tabla declara la suya**. `treatments` y `treatment-plans` no existen.

Restricciones duras verificadas en el repo, no de memoria:
- ArchUnit: `@Async`/`@Scheduled` prohibidos · toda entidad con `@TenantId` debe declarar autoría · `presentation` no depende de `persistence` · `persistence` no depende de `service` ni `presentation`.
- `service → service` **sí** está permitido (`LayeredArchitectureTest` no lo restringe) — es lo que habilita el coordinador de D4.

## Goals / Non-Goals

**Goals:**
- Persistir el bloque inicial y sus hallazgos cumpliendo las invariantes de `ADR-0012`, con las de unicidad **garantizadas por la base**, no por chequeo previo.
- Sellar el odontograma inicial **atómicamente** con la Historia Clínica, sin crear dependencia entre los dos módulos.
- Registrar hallazgos con las **cinco** formas de localización (`ADR-0017` D4), con coherencia validada en el boundary.
- Una representación gráfica que un odontólogo reconozca como un odontograma.

**Non-Goals:** los de `proposal.md` (evolución, comparación entre fechas, cierre automático, integración con `treatments`, retención, COP). El **catálogo** ya no es un non-goal: quedó **cerrado** en `ADR-0017` D7 con los 38 hallazgos de 6.1.1–6.1.38.

## Decisions

### D1 — Dos tablas: bloque + hallazgos

`dental_chart_block` (el aggregate root) y `dental_chart_finding` (los hallazgos). No una sola tabla con JSONB: los hallazgos se consultan por pieza y por tipo (el render agrupa por pieza), y JSONB perdería los `CHECK` de integridad.

Ambas con `organization_id` NOT NULL + índice + `ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `organization_isolation`, **cada una la suya**. La RLS de Postgres **no es transitiva**: que `dental_chart_finding` tenga FK a un bloque aislado no la protege. Es el hallazgo que costó una auditoría entera en `add-clinical-history`.

#### Las FK son COMPUESTAS por tenant, no por id

> 🔴 **Agregado tras una revisión externa (2026-09-23).** Faltaba por completo, y es el hueco más serio que encontró.

Una FK por id sola **permite cruzar organizaciones**:

```sql
-- INSUFICIENTE: nada ata el tenant del hallazgo al de su bloque
organization_id BIGINT NOT NULL REFERENCES organizations (id),
block_id        BIGINT NOT NULL REFERENCES dental_chart_block (id)
```

Con eso, una fila puede declarar `organization_id = A` y apuntar a un bloque de la organización B. La forma correcta:

```sql
-- en el padre
CONSTRAINT ux_dental_chart_block_org_id UNIQUE (organization_id, id),
-- en el hijo
CONSTRAINT fk_finding_block FOREIGN KEY (organization_id, block_id)
  REFERENCES dental_chart_block (organization_id, id)
```

Aplica a **las tres** relaciones nuevas: `finding → block`, `finding_tooth → finding` y `block → patient`.

**Por qué la RLS no lo cubre.** Son dos cosas distintas y conviene no confundirlas:

| | Qué garantiza | Qué NO |
|---|---|---|
| **RLS** | que una sesión **no vea ni escriba** filas de otro tenant | que las filas existentes sean **internamente coherentes** |
| **FK compuesta** | que un hijo **no pueda apuntar** a un padre de otro tenant | el aislamiento de lectura |

⚠️ Y hay un motivo extra para no apoyarse en la FK como barrera: **las comprobaciones internas de FK de PostgreSQL no respetan la RLS** — corren con privilegios del sistema. Una FK **no es** un control de autorización; es una garantía de integridad. Se necesitan las dos.

🔴 **`clinical-history` arrastra esta misma deuda, y está construida.** `V15__clinical_history_diagnosis.sql` declara `organization_id REFERENCES organizations (id)` e `historia_id REFERENCES historia_clinica (id)` como FK **separadas**; `V14` hace lo mismo con `patient_id`. Hoy no es explotable — la RLS tapa la lectura y el servicio fija el tenant desde el contexto — pero es estructural. **No se arregla en este change**: va como `update-clinical-history`, porque tocar el esquema de una capacidad construida para corregir algo que no la rompe no pertenece a un change de otra capacidad. ⚠️ **Lo que sí cambia acá es que el patrón no se copia.**

*Alternativa descartada:* hallazgos como JSONB dentro del bloque (menos tablas, sin FK) — pierde los `CHECK` de coherencia de localización, complica las consultas por pieza y repetiría el error que `add-patients` evitó a propósito con la anamnesis (allí JSONB **sí** fue correcto porque no se consulta por dentro; acá sí se consulta).

### D2 — `Location` polimórfico: discriminador explícito + `CHECK`, no columnas implícitas

El `sealed interface Location` se persiste como un `@Embeddable` con **discriminador explícito**. Son **cinco** variantes (`ADR-0017` D4), no las tres de `ADR-0012` D8:

```
location_kind   VARCHAR  NOT NULL  CHECK (location_kind IN
                  ('SURFACE','TOOTH','BETWEEN_TEETH','TOOTH_RANGE','ARCH'))
tooth_a         INT      NULL      -- pieza principal (o la menor, si BETWEEN_TEETH)
tooth_b         INT      NULL      -- solo BETWEEN_TEETH
surface         VARCHAR  NULL      -- solo SURFACE
arch            VARCHAR  NULL      -- solo ARCH: 'SUPERIOR' | 'INFERIOR'
```

Más un `CHECK` que empareja discriminador y columnas:

| `location_kind` | Columnas pobladas | Ejemplo |
|---|---|---|
| `SURFACE` | `tooth_a` + `surface` | caries 36-O · restauración · sellante |
| `TOOTH` | `tooth_a` | corona · movilidad · ausente · implante |
| `BETWEEN_TEETH` | `tooth_a` + `tooth_b` | diastema · supernumerario · fusión |
| `TOOTH_RANGE` | ninguna de las de arriba — ver abajo | prótesis parcial fija · ortodoncia fija |
| `ARCH` | `arch` | edéntulo total · prótesis completa |

**`TOOTH_RANGE` no cabe en columnas planas**: comprende un **conjunto ordenado** de piezas (6.1.29 dibuja los pilares), de cardinalidad variable. Va en **tabla hija** `dental_chart_finding_tooth (finding_id, position, tooth)` con `UNIQUE (finding_id, position)` — no un array ni una lista separada por comas: se consulta por pieza ("¿qué hallazgos tocan la 36?") y esa consulta debe poder usar un índice. La tabla hija lleva **su propia RLS**; la de la tabla padre no la protege (mismo hallazgo de D1).

**Precedente exacto en el repo**: `clinical_history_diagnosis` ya hace el emparejamiento discriminador↔columnas con `ubicacion` + `fdi_number` y la constraint `clinical_history_diagnosis_location_paired`. Se replica el patrón, no se inventa uno.

#### El `CHECK` completo, y por qué se escribe así

> ⚠️ **Ampliado tras una revisión externa (2026-09-23).** La redacción anterior decía "más un `CHECK` que empareja discriminador y columnas" y dejaba la mitad de las invariantes sin escribir.

🔴 **La trampa principal: en PostgreSQL un `CHECK` que evalúa a `NULL` se considera SATISFECHO.** Con cinco columnas nullable, un `CHECK` escrito con expresiones que puedan dar `NULL` **no valida nada** y lo hace en silencio. Por eso cada rama se escribe con `IS NULL` / `IS NOT NULL` explícitos, que siempre dan `true` o `false`:

```sql
CONSTRAINT dental_chart_finding_location_paired CHECK (
  CASE location_kind
    WHEN 'SURFACE'       THEN tooth_a IS NOT NULL AND surface IS NOT NULL
                              AND tooth_b IS NULL AND arch IS NULL
    WHEN 'TOOTH'         THEN tooth_a IS NOT NULL
                              AND tooth_b IS NULL AND surface IS NULL AND arch IS NULL
    WHEN 'BETWEEN_TEETH' THEN tooth_a IS NOT NULL AND tooth_b IS NOT NULL
                              AND tooth_a < tooth_b
                              AND surface IS NULL AND arch IS NULL
    WHEN 'TOOTH_RANGE'   THEN tooth_a IS NULL AND tooth_b IS NULL
                              AND surface IS NULL AND arch IS NULL
    WHEN 'ARCH'          THEN arch IS NOT NULL
                              AND tooth_a IS NULL AND tooth_b IS NULL AND surface IS NULL
  END
)
```

Dos detalles que no son adorno:

- **Cada rama niega explícitamente las columnas que NO le corresponden.** Sin eso, un `SURFACE` con `arch` poblado pasaría, y el dato quedaría ambiguo para siempre.
- **`tooth_a < tooth_b` va en la base**, no solo en el VO. El orden canónico de `BETWEEN_TEETH` (más abajo) es una invariante de dato: si solo vive en Java, un `UPDATE` directo o una ruta futura la rompe.

**Los conjuntos cerrados también se cierran en la base**, no solo en el enum:

```sql
arch    VARCHAR NULL CHECK (arch    IS NULL OR arch    IN ('SUPERIOR','INFERIOR'))
surface VARCHAR NULL CHECK (surface IS NULL OR surface IN ('M','D','V','P','L','O','I'))
```

⚠️ Cada uno lleva su `IS NULL OR` por la misma razón de arriba. Y las piezas se validan contra `FdiTooth`, que ya existe y ya tiene el conjunto real (D7).

#### Invariantes de la tabla hija

```sql
CREATE TABLE dental_chart_finding_tooth (
  id              BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL,            -- RLS propia (D1)
  finding_id      BIGINT NOT NULL,
  position        INT    NOT NULL CHECK (position >= 1),
  tooth           INT    NOT NULL,
  CONSTRAINT ux_finding_tooth_position UNIQUE (finding_id, position),
  CONSTRAINT ux_finding_tooth_tooth    UNIQUE (finding_id, tooth)
);
CREATE INDEX ix_finding_tooth_tooth ON dental_chart_finding_tooth (tooth);
```

| Invariante | Cómo |
|---|---|
| `position` empieza en 1 y no se repite | `CHECK (position >= 1)` + `UNIQUE (finding_id, position)` |
| Una pieza no se repite dentro del rango | `UNIQUE (finding_id, tooth)` — **faltaba** |
| La consulta *"¿qué hallazgos tocan la 36?"* usa índice | `ix_finding_tooth_tooth` — **faltaba**, y era la razón de ser de la tabla |

🔴 **Dos invariantes que un `CHECK` NO puede expresar**, porque son de cardinalidad entre filas:

1. **Solo un `TOOTH_RANGE` tiene filas hijas.** Un `CHECK` en la tabla padre no ve las hijas, y uno en la hija no ve el `location_kind` del padre.
2. **Un rango tiene al menos dos piezas.** Un rango de una sola pieza es un `TOOTH` mal modelado, y uno de cero no es nada.

Se resuelven **en el servicio, con el `finding` bloqueado** (`SELECT ... FOR UPDATE`) y validando el conjunto completo antes de confirmar. ⚠️ **Es una excepción consciente a D3**: acá el chequeo previo **sí** alcanza, porque no hay carrera posible — las filas hijas de un `finding` solo se tocan con ese `finding` bloqueado, y el `finding` nace y se completa en la misma transacción. Lo que D3 prohíbe es fiarse de un `exists` contra filas que **otra** transacción puede crear en paralelo.

*Alternativa descartada:* **trigger** que valide la cardinalidad. Funciona, pero mete lógica de dominio en la base, no es testeable desde el servicio y este proyecto no tiene ni un trigger todavía — el primero debería entrar por una razón más fuerte que ésta. Se reconsidera si aparece una ruta de escritura que no pase por el servicio.

*Alternativas descartadas:* (a) sin discriminador, infiriendo la variante de qué columnas están llenas — frágil e imposible de constrainear con claridad; (b) tabla por variante — cinco joins para leer un odontograma; (c) JSONB — ver D1; (d) **expandir un rango o un arco en N hallazgos de pieza** — inventaría hasta 16 registros donde la norma pide uno, y haría indistinguible una prótesis completa de 16 coronas (`ADR-0017` D4).

**Orden canónico en `BETWEEN_TEETH`**: se persiste siempre `tooth_a < tooth_b`, normalizado al construir el VO. Sin esto, `(11,21)` y `(21,11)` serían dos hallazgos distintos para el mismo diastema (INV-12). **En `TOOTH_RANGE` el orden sí es dato** —identifica los pilares— y por eso se conserva en `position`, sin normalizar.

### D3 — Las invariantes de unicidad van a la base, no al servicio

`INV-01` (un solo bloque abierto por paciente) e `INV-02` (un solo `INITIAL`) **no** se resuelven con un `existsBy...` previo: dos solicitudes concurrentes leen "no hay", y ambas crean.

Se implementan con **índices únicos parciales**:

```sql
CREATE UNIQUE INDEX ux_dental_chart_block_open   ON dental_chart_block (patient_id) WHERE status = 'OPEN';
CREATE UNIQUE INDEX ux_dental_chart_block_initial ON dental_chart_block (patient_id) WHERE type = 'INITIAL';
```

La `DataIntegrityViolationException` se traduce a la excepción de negocio correspondiente, igual que `HistoriaClinicaAlreadyExistsException` traduce el choque de `UNIQUE(patient_id)`. **Precedente pagado**: `add-clinical-history` tiene un test de concurrencia con 8 hilos reales contra PostgreSQL que existe exactamente porque el chequeo previo no alcanzaba.

#### La unicidad de HALLAZGOS también va a la base

> 🔴 **Agregado tras la revisión externa (2026-09-23).** El spec exige rechazar dos hallazgos con el mismo `type` + `location` y **no había ninguna constraint** que lo garantizara: solo estaba la unicidad de *bloques*. Es la misma clase de error que D3 corrige, un nivel más abajo.

El problema es más duro que el de los bloques, porque la localización **no es una columna**: está repartida entre `tooth_a`, `tooth_b`, `surface`, `arch` y — para `TOOTH_RANGE` — una tabla hija. Un índice único común no puede abarcar eso, y varias columnas nullable hacen que `UNIQUE` **no colisione** cuando hay `NULL` (en PostgreSQL dos `NULL` no son iguales, salvo `NULLS NOT DISTINCT`, de PG 15).

**Decisión: índices únicos parciales por variante**, uno por `location_kind`:

```sql
CREATE UNIQUE INDEX ux_finding_surface ON dental_chart_finding
  (block_id, type, subtype, tooth_a, surface) WHERE location_kind = 'SURFACE';
CREATE UNIQUE INDEX ux_finding_tooth   ON dental_chart_finding
  (block_id, type, subtype, tooth_a)          WHERE location_kind = 'TOOTH';
CREATE UNIQUE INDEX ux_finding_between ON dental_chart_finding
  (block_id, type, subtype, tooth_a, tooth_b) WHERE location_kind = 'BETWEEN_TEETH';
CREATE UNIQUE INDEX ux_finding_arch    ON dental_chart_finding
  (block_id, type, subtype, arch)             WHERE location_kind = 'ARCH';
```

Cada índice cubre **solo** las columnas que su variante puebla, así que no hay `NULL` dentro de la clave y la colisión funciona.

⚠️ **`TOOTH_RANGE` queda fuera y es deliberado.** Su identidad vive en la tabla hija, y un índice único no puede abarcar un conjunto de filas. Se resuelve con el mismo bloqueo del `finding` que valida la cardinalidad (D2), en la misma transacción. **Es la única variante cuya unicidad no la garantiza la base**, y queda escrito para que nadie lo descubra por accidente.

*Alternativa descartada:* **una clave de localización canónica** persistida (por ejemplo `'SURFACE:36:O'`) con un único índice único sobre `(block_id, type, subtype, location_key)`. Es más simple y cubre las cinco variantes — incluido el rango — pero **persiste un dato derivado**: si cambia la forma de serializar, las filas viejas dejan de colisionar con las nuevas y la invariante se rompe en silencio. Se reconsidera si el bloqueo del rango resulta incómodo en la práctica.

⚠️ **Sobre los dos índices de bloque**: en v1 solo existe `INITIAL`, así que `ux_dental_chart_block_open` es **casi redundante** con `ux_dental_chart_block_initial`. Se mantienen los dos igual: el de `OPEN` deja de ser redundante cuando `update-dental-chart` traiga la evolución con varios bloques por paciente, y quitarlo ahora obliga a volver a ponerlo.

### D4 — El coordinador vive en `service`, sobre ambos módulos

```
service/admission/CloseAdmissionService        @Transactional
   ├── service/clinicalhistory/ClinicalHistoryService.complete(...)
   └── service/dentalchart/DentalChartService.sealInitial(...)
```

Ninguno de los dos servicios de dominio conoce al otro. El coordinador depende de ambos; `service → service` no está restringido por ArchUnit.

**Transaccionalidad**: el `@Transactional` del coordinador abre la transacción; los métodos de cada servicio se unen a ella (propagación `REQUIRED` por defecto). 

#### 🔴 El orden de bloqueo es único y obligatorio

> **Agregado tras la revisión externa (2026-09-23).** El riesgo no está en `service → service`: está en que dos rutas bloqueen los mismos dos agregados en orden distinto.

**Toda** ruta que toque Historia y odontograma juntos bloquea en este orden, sin excepción:

```
1. paciente
2. historia clínica      (SELECT ... FOR UPDATE)
3. odontograma           (SELECT ... FOR UPDATE)
4. validar el checklist completo
5. sellar ambos
```

Si una ruta bloquea el odontograma antes que la Historia y otra al revés, dos sellados concurrentes se **deadlockean**. Postgres lo detecta y mata una transacción, así que no corrompe datos — pero produce un error intermitente que no se reproduce en un test secuencial.

⚠️ **Se verifica con un test de concurrencia con hilos reales**, no solo con uno de rollback. Precedente: el test de 8 hilos de `add-clinical-history`.

**`sealInitial()` no es invocable por sí solo.** Si se pudiera sellar el odontograma sin la Historia, caería la invariante que este change existe para crear (`ADR-0012` D5: se sellan **atómicamente**). Queda **package-private** o explícitamente rechazada como ruta pública: el único punto de entrada es el coordinador. ⚠️ Un método público que rompe una invariante del agregado es una puerta que alguien va a usar.

> **Decisión final al implementar:** `sealInitial()` es **`public`**, porque el coordinador vive en otro paquete (`service.admission`) y Java no tiene una visibilidad "solo para este paquete y aquel". La restricción la sostienen tres cosas: **ningún endpoint** lo expone (`DentalChartController` no tiene ruta de sellado; se sella por `POST /patients/{id}/clinical-history/complete`); una regla de **ArchUnit** (`AdmissionSealingArchitectureTest`, tarea 2.8e) hace fallar la build si otra clase de producción lo llama; y los **triggers de `V20`** impiden, a nivel de dato, tocar un odontograma ya sellado.Si el sellado del odontograma falla, revierte también el de la Historia. Importante para este proyecto: como el contexto de tenant se fija **al comenzar la transacción** (`TenantAwareTransactionManager.doBegin`), abrirla en el coordinador lo enlaza una sola vez para toda la operación.

**Ruta sin cambios, comportamiento sí**: el endpoint existente `POST /patients/{id}/clinical-history/complete` **conserva su ruta** y pasa a delegar en el coordinador. El **contrato HTTP** no cambia, pero **el flujo de usuario sí**: hasta ahora el odontólogo podía completar la Historia sin más; ahora necesita haber creado y revisado el odontograma antes. ⚠️ Decir *"el frontend no se toca"* sería falso — lo que no cambia es la **ruta**, no la experiencia. La pantalla de Historia Clínica debe reflejar el nuevo requisito (mostrar qué falta, enlazar al odontograma) o el usuario chocará contra un rechazo sin entender por qué. Está cubierto por el checklist con `missingFields`, que el frontend ya consume.

*Alternativa descartada:* endpoint nuevo `POST /patients/{id}/admission/close`, más honesto con el concepto pero rompe el cliente ya generado sin ganancia funcional.

### D5 — Qué exige el checklist de sellado (y qué no)

Al sellar, el checklist de `clinical-history` gana un ítem: **existe un odontograma inicial con especificaciones y observaciones registradas** — son dos campos distintos y uno no cubre al otro (`ADR-0017` D6). La Historia sola ya no basta.

#### 🔴 "Revisado y vacío" no cabe en dos columnas `TEXT`

> **Agregado tras la revisión externa (2026-09-23).** El spec ya exigía la distinción — *"registrar que no hay nada que anotar es una afirmación válida, dejarlas sin revisar no lo es"* — con dos scenarios de rechazo. Pero el esquema propuesto eran dos columnas `TEXT` y nada más, donde `NULL`, `''` y *"revisado y sin nada que anotar"* son **indistinguibles**.

**El proyecto ya resolvió esto y no se reusó.** `V14__historia_clinica.sql`, línea 26:

```sql
-- "Campo vacío ≠ revisado" (design.md D3): pares revisado/detalle para las 3 secciones donde
-- "nada" es un dato clínico, no un olvido. revisado=true + detalle=null => "sin alergias
-- conocidas"; revisado=false => no revisado (el checklist de sellado exige revisado=true).
alergias_revisado                BOOLEAN NOT NULL DEFAULT false,
```

Se replica exacto:

```sql
specifications          TEXT    NULL,
specifications_reviewed BOOLEAN NOT NULL DEFAULT false,
observations            TEXT    NULL,
observations_reviewed   BOOLEAN NOT NULL DEFAULT false
```

| Estado | `reviewed` | `texto` | Significa |
|---|---|---|---|
| no revisado | `false` | `NULL` | **el sellado se rechaza** |
| revisado, sin nada que anotar | `true` | `NULL` | afirmación válida — sella |
| revisado con contenido | `true` | *texto* | sella |
| ⚠️ imposible | `false` | *texto* | `CHECK (texto IS NULL OR reviewed)` |

⚠️ **Editar el texto NO vuelve a poner `reviewed = false`.** La revisión externa lo sugirió y se descarta: quien escribe está revisando, y resetear la marca al escribir convertiría cada corrección de una coma en un bloqueo del sellado. El `reviewed` afirma *"un profesional miró esta sección"*, y eso no se deshace al mirarla otra vez.

Deliberadamente **no** se exige un número mínimo de hallazgos: una boca sana es un odontograma legítimo y vacío de patología. Exigir "al menos un hallazgo" empujaría a inventar uno. Lo que sí se exige es que el odontograma **exista y haya sido revisado** — mismo criterio afirmativo que `alergiasRevisado` en `clinical-history`: la ausencia de hallazgos es un dato clínico, no un campo sin llenar.

### D6 — El catálogo: `tipo` + `subtipo` como enums, `CHECK` en la BD, atributos solo en código

Conjunto cerrado ⇒ **enum Java + `CHECK` en Postgres + contrato** (regla del proyecto, `add-patients` D8). Los atributos del tipo viven **solo en el enum Java**, no en columnas: son derivables del tipo, y almacenarlos permitiría que se desincronicen. La BD solo restringe el conjunto de valores válidos.

**Dos niveles, no ~40 tipos planos** (`ADR-0017` D5). El texto agrupa hallazgos bajo un mismo concepto con siglas distintas según material, profundidad o causa: la lesión de caries es `MB`/`CE`/`CD`/`CDP`, la corona es `CM`/`CF`/`CMC`/`CV`/`CLM`. El **comportamiento de color y el alcance pertenecen al concepto** (toda corona es `SEGUN_ESTADO` y de alcance pieza); el subtipo solo precisa cuál es. Aplanarlo repetiría esos atributos en cada variante, con riesgo de que diverjan.

```
type      VARCHAR  NOT NULL   CHECK (type IN (...38 valores...))
subtype   VARCHAR  NULL       -- obligatorio si el tipo tiene subtipos, prohibido si no
```

Más un `CHECK` que valida **el par**, no cada columna por separado: un `subtype = 'CD'` con `type = 'CORONA'` es inválido aunque ambos valores existan.

Atributos del **tipo**, alineados a `ADR-0017`:

| Atributo | Para qué |
|---|---|
| `colorBehavior` | `SIEMPRE_AZUL` · `SIEMPRE_ROJO` · `SEGUN_ESTADO` (`ADR-0017` D1) |
| `scope` | superficie / pieza / entre piezas / **rango** / **arco** — determina la variante de `Location` admitida |
| `subtypes` | conjunto cerrado de subtipos admitidos; vacío si el tipo no los tiene |
| `requiresGrade` | solo movilidad (`M1`/`M2`/`M3`) |
| `requiresSpecification` | el tipo obliga a llenar una entrada en **especificaciones**: corona ⇒ color del metal (6.1.3) · corona temporal ⇒ material (6.1.4) · fluorosis ⇒ clasificación (6.1.5). Con uno registrado, el sellado exige texto no vacío (`DentalChartChecklist`). ⚠️ La **movilidad no**: su grado va en la sigla (`M1`/`M2`/`M3`, 6.1.19), no en especificaciones — corregido al verificar el catálogo contra el PDF (tarea 0.7, comentario de `V16`) |

Y del **subtipo**: la **`sigla` oficial de NTS 188**, dato normativo que el render debe pintar exacto. Si el tipo no tiene subtipos, la sigla —cuando existe— cuelga del tipo. Se guarda como atributo, **no como nombre del enum** (`ADR-0013` D3): el nombre del enum es descriptivo y en español; la sigla es lo que se imprime.

⚠️ **Tres comportamientos, no una naturaleza clínica.** El texto (5.13) califica **el hallazgo**, no el tipo ni la pieza: *"azul indica un hallazgo clínico en buen estado y rojo un hallazgo en mal estado, temporal o con características patológicas"*. Por eso los `SEGUN_ESTADO` llevan además un campo **`condition`** (`GOOD`/`POOR`) en la fila del hallazgo: es el único dato de color que sí se persiste, porque no es derivable. **"Temporal" no es un eje aparte**: la corona temporal es un tipo distinto (`CT` ≠ `CM`) que simplemente declara `SIEMPRE_ROJO` (`ADR-0017` D1).

El frontend recibe tipo y subtipo por el contrato y **espeja** los atributos, igual que ya espeja `VALID_FDI_TEETH` desde `FdiTooth`.

#### 🔴 El catálogo pasa a **tabla cargada por migración**

> **Cambiado tras la revisión externa (2026-09-23).** La objeción: un `CHECK` con 38 tipos y todos sus pares *"crecerá mal y será difícil de auditar"*, y los atributos que determinan invariantes viven solo en Java, así que **la base aceptaría combinaciones que el dominio considera imposibles**. Es correcta, y además **el proyecto ya tiene el patrón**: `Cie10CatalogLoader` carga un catálogo por migración. Un `CHECK` de 38 pares es justo lo que ese patrón existe para evitar.

```sql
CREATE TABLE dental_finding_catalog (
  type                   VARCHAR NOT NULL,
  subtype                VARCHAR NOT NULL,   -- '' si el tipo no tiene subtipos
  official_numeral       VARCHAR NOT NULL,   -- COMO LO IMPRIME LA NORMA: '6.1.19', '5.2.23', '6.115'
  numeral_by_position    VARCHAR NOT NULL,   -- el corregido: '6.1.19', '6.1.23', '6.1.15'
  official_acronym       VARCHAR NULL,
  scope                  VARCHAR NOT NULL,
  color_behavior         VARCHAR NOT NULL,
  requires_grade         BOOLEAN NOT NULL,
  requires_specification BOOLEAN NOT NULL,
  PRIMARY KEY (type, subtype)
);
```

Y el `finding` referencia el par en vez de repetir un `CHECK`:

```sql
CONSTRAINT fk_finding_catalog FOREIGN KEY (type, subtype)
  REFERENCES dental_finding_catalog (type, subtype)
```

**Qué gana**: la BD rechaza por construcción un subtipo ausente, uno prohibido y uno que pertenece a otro tipo — los tres casos que el `CHECK` manual tenía que enumerar. Y el catálogo se vuelve **consultable y auditable**: un auditor puede pedir la tabla y compararla con la norma, sin leer Java.

⚠️ **Los enums siguen en código.** La tabla es la fuente de **integridad**; el enum es la fuente de **tipos**. No se invierte: el código no lee el catálogo para saber qué tipos existen. Un test pinea que enum y tabla coincidan — misma idea que la tarea 1.5b con las siglas.

⚠️ **El numeral se guarda dos veces, y es a propósito.** `ADR-0017` D7 documenta **dos typos de la propia norma**: la pieza en erupción se imprime `5.2.23` y el implante `6.115`. Si se guarda solo el corregido, el sistema deja de coincidir con el documento que un auditor tiene en la mano; si se guarda solo el impreso, no se puede ordenar ni validar la secuencia. Se guardan los dos.

#### 🔴 La sigla NO es única: ningún índice único sobre ella

Descubierto al re-verificar el catálogo (tarea `0.7`): **hay dos colisiones reales en la norma**.

| Sigla | Un tipo | Otro tipo | Se distinguen por |
|---|---|---|---|
| **`M`** | Movilidad patológica (6.1.19), 🔴 | Mesializado, subtipo de Posición anormal (6.1.28), 🔵 | la movilidad lleva **dígito de grado** |
| **`S`** | Pieza supernumeraria (6.1.26), entre piezas | Sellante (6.1.35), superficie | **alcance** |

⚠️ Por eso `official_acronym` **no lleva `UNIQUE`**, y la sigla **no identifica** un hallazgo. La clave es el par `(type, subtype)`; la sigla es un atributo de salida. Un índice único sobre ella habría parecido razonable y **rechazaría registros válidos**.

### D7 — Notación FDI y solo FDI, en todo el dominio

El dominio trabaja **exclusivamente** en notación **FDI / ISO 3950** (dos dígitos: cuadrante + posición). **Nunca** Universal Numbering System (1-32, EE.UU.) ni Palmer. Esto vale para el modelo, la persistencia, el contrato y el frontend.

No es una obviedad que se pueda dar por supuesta: las librerías de odontograma de origen estadounidense usan Universal por defecto, y cuando lleguen integraciones (aseguradoras, laboratorios, sistemas de terceros) va a haber presión para aceptar otra notación "solo en ese borde". Si eso pasa alguna vez, **la conversión ocurre en el adaptador de la integración y nunca entra al dominio** — una pieza tiene un solo identificador acá dentro.

`FdiTooth` ya es la fuente única del conjunto válido, construida con `add-clinical-history` y compartida con el diagnóstico CIE10. No se duplica ni se reinterpreta.

### D8 — El odontograma y el diagnóstico CIE10 no se referencian entre sí

`clinical-history` registra diagnósticos CIE10 que pueden ubicarse en una pieza por su número FDI; `dental-chart` registra hallazgos en piezas y superficies. Ambos pueden hablar de la pieza 36 al mismo tiempo. **La relación entre los dos es deliberadamente ninguna**: sin FK, sin campo de correlación, sin validación cruzada.

- **`ADR-0011` ya lo decidió para el lado del diagnóstico**: ubica por número FDI, *"no por una fila de `ToothCondition` del odontograma"*, para no acoplar las dos capacidades. Este change mantiene la simetría desde el otro lado.
- **Describen cosas distintas.** El CIE10 es **nomenclatura de enfermedad** (`K02.1` = caries de la dentina); el hallazgo es **topografía de estado** (caries en 36-O). Se corresponden a menudo, pero no siempre: hay diagnósticos generales sin pieza (`K05.3` periodontitis crónica) y hallazgos sin diagnóstico propio (una obturación en buen estado no es una enfermedad).
- **Granularidad distinta**: el diagnóstico llega a la pieza; el hallazgo, a la superficie. Forzar una correspondencia obligaría a inventar datos en un lado o a perder precisión en el otro.

**No se valida coherencia entre ambos al sellar** (por ejemplo, exigir que todo diagnóstico ubicado en una pieza tenga un hallazgo en esa pieza). Sería acoplamiento más allá de la coordinación del sellado, y rechazaría casos clínicos legítimos. Si más adelante se quiere ayudar al odontólogo, el camino es un **aviso no bloqueante** —mismo criterio que la incoherencia dentición↔edad—, nunca un rechazo.

### D9 — Validación: constraint de objeto en el boundary, invariantes de estado en el servicio

| Invariante | Dónde |
|---|---|
| INV-10 (`scope` ↔ variante de `Location`, ahora cinco), INV-11 (`requiresGrade` ⇔ `grade`), INV-13 (superficie aplicable a la pieza), INV-14 (FDI real), **par `type`↔`subtype` válido** y **`condition` exigida solo en `SEGUN_ESTADO`** | `@ValidFindingLocation` — constraint Bean Validation **a nivel de objeto** sobre el DTO, mismo patrón que `@ValidDiagnosisLocation` (`@Constraint` + `ConstraintValidator` + `addPropertyNode` para reportar sobre el campo correcto) |
| INV-02, INV-04 | índice único parcial + traducción de la violación (D3) |
| ~~INV-01 (un bloque abierto **por día**)~~ | **Cae**: NTS 188 genera odontograma **por evento**, no por día (`ADR-0017` D3). Sobrevive como *un bloque abierto por paciente* — la cronología de `ADR-0012` D7, misma implementación de D3 |
| INV-03 (sellado inmutable), INV-06/07/08 (asientos), INV-09 (pieza ausente sin superficies) | servicio — requieren estado ya persistido |
| INV-15 (dentición ↔ edad) | servicio, como **aviso** en la respuesta; nunca rechaza |

INV-13 (qué superficies admite cada pieza) necesita saber si la pieza es anterior o posterior: se deriva del número FDI en `FdiTooth`, extendiéndolo — no se crea un catálogo nuevo.

#### 🔴 El sellado se protege también en la persistencia

> **Agregado tras la revisión externa (2026-09-23).** El spec exige que *"un odontograma sellado no pueda modificarse ni por API directa"*, y la invariante vivía **solo en el servicio**. Una ruta olvidada, un `save()` de JPA accidental o una operación concurrente la esquivan.

**Primero, la coherencia del propio estado**, que ningún `CHECK` cubría:

```sql
CONSTRAINT dental_chart_block_seal_coherent CHECK (
  (status = 'OPEN'   AND sealed_at IS NULL) OR
  (status = 'SEALED' AND sealed_at IS NOT NULL)
)
```

Eso hace **imposibles como dato** un `SEALED` sin fecha y un `OPEN` con fecha — dos estados que hoy el esquema aceptaba.

**Y las transiciones que hay que fijar explícitamente:**

| Transición | Regla |
|---|---|
| `OPEN → SEALED` | única permitida |
| `SEALED → OPEN` | **imposible** — es lo que hace del odontograma un documento |
| `SEALED → SEALED` | no reescribe `sealed_at`; el segundo intento se rechaza |
| hallazgos y filas hijas de un bloque sellado | **no se modifican**, ni siquiera si el padre cambia de estado en paralelo |

**Cómo se garantiza la última fila**, que es la difícil: el servicio bloquea el bloque (`SELECT ... FOR UPDATE`) **antes** de tocar cualquier hallazgo y verifica que siga `OPEN`. Sin ese bloqueo hay una carrera real: leer `status='OPEN'`, y mientras se inserta el hallazgo, otra transacción sella. Es la misma clase de carrera que `add-clinical-history` encontró en revisión al resetear el contador de intentos de login.

⚠️ **Y una defensa más, que ya está decidida en otro ADR**: [`ADR-0014`](../../../docs/adr/0014-ciclo-de-vida-del-documento-clinico.md) **D4** revoca `UPDATE`/`DELETE` al rol de aplicación (`odontorisas_app`). Cuando eso se implemente, la inmutabilidad deja de depender del código. **No es tarea de este change**, pero conviene no diseñar nada que la contradiga.

> **Implementado tras la segunda revisión externa (2026-10-08):** la migración `V20__dental_chart_seal_guard.sql` agrega triggers que hacen del sellado un hecho de la base. Un bloque `SEALED` no se modifica ni se borra: ni reapertura, ni segundo sellado, ni cambios de texto. Sus hallazgos y piezas de rango tampoco se insertan, modifican ni borran (`SQLSTATE 55000`). Probado con SQL crudo por la conexión owner en `DentalChartSealGuardIT`. El servicio sigue siendo la primera barrera (bloqueo + `OPEN`, tareas 2.8a/2.8f); los triggers, la última. No contradicen `ADR-0014` D4.

*Alternativa descartada:* **trigger** que rechace `UPDATE` sobre filas de un bloque sellado. Es la defensa más fuerte y no se descarta por mala: se descarta porque `ADR-0014` D4 ya resuelve lo mismo a nivel de permisos, de forma transversal a todas las capacidades, y meter el primer trigger del proyecto para un caso que otra decisión ya cubre acopla este change a un mecanismo que nadie más usa.

### D10 — Frontend: SVG inline, con colores **normativos** (no del tema)

La representación es **SVG inline**, no canvas ni imágenes: cada pieza y cada superficie deben ser **individualmente enfocables y accionables** (teclado y lector de pantalla), y con canvas eso hay que reconstruirlo a mano. Un odontograma donde solo se puede hacer clic con mouse deja fuera a parte de los usuarios y no pasaría una auditoría de accesibilidad.

**⚠️ Los colores del odontograma NO salen de los tokens del tema** (`ADR-0013` D5). NTS 188 fija los valores exactos —rojo `#FF0000`, azul `#0000FF`— y son parte del documento legal, no de la identidad visual de la clínica.

- El odontograma se renderiza **siempre sobre fondo blanco con los hex exactos**, aunque el resto de la aplicación esté en modo oscuro. Se comporta como un documento embebido, no como una superficie de UI: **lo que el odontólogo ve debe ser idénticamente lo que se audita**.
- Es la **única excepción** a la regla de `frontend-guard` de no hardcodear colores. Hay que dejarlo comentado en el código, porque a simple vista parece la violación que esa regla persigue.
- Los colores derivan del `colorBehavior` del tipo y —para los `SEGUN_ESTADO`— de la `condition` del hallazgo. **Nada más**: la regla de **contagio** de `ADR-0012` D2 **se elimina** (`ADR-0017` D2), no existe en la norma. Cada marcador se pinta por su propio estado, sin mirar la pieza entera. Un diente con caries **y** giroversión lleva la caries en rojo y la flecha en azul (6.1.13).
- ⚠️ **Colores corregidos por el texto oficial**: la **movilidad** es **roja** (6.1.19), no azul; **extruida** e **intruida** son **azules** (6.1.24/6.1.25), no rojas. `ADR-0013` las tenía al revés.
- **El color nunca es el único portador de significado**: la sigla oficial y la forma también lo comunican, que es justamente lo que la norma exige. Eso mantiene la accesibilidad aunque el contraste no se adapte al tema.

*Riesgo asumido:* es la pieza de frontend más grande construida hasta ahora en el proyecto. Ver Riesgos.

## Risks / Trade-offs

**[El odontograma SVG es la pieza de UI más grande del proyecto]** → Se acota a lo que la norma exige: dentición según edad, superficies clicables, marcadores por tipo. Sin animaciones, sin zoom, sin arrastrar. Si aun así desborda el change, el corte natural es entregar primero el registro (piezas completas) y las superficies después — pero **eso degradaría la precisión cara-por-cara que `ADR-0012` D8 declara innegociable por auditoría de SUSALUD**, así que sería un corte a discutir, no a tomar sobre la marcha.

**[Romper el sellado de una capacidad construida]** → Los tests existentes de `clinical-history` que sellan la Historia sin odontograma **van a fallar, por diseño**. Es trabajo previsto, no un efecto colateral: se actualizan al nuevo contrato en el mismo change. El riesgo real sería *no* verlos fallar — significaría que el checklist no se está aplicando.

**[Dentición mixta y piezas fuera de lo esperado]** → El modelo localiza por FDI, así que temporales y permanentes coexisten sin caso especial. La incoherencia con la edad **avisa pero no bloquea** (INV-15): una temporal retenida en un adulto es rara y real, y bloquearla impediría registrar un caso legítimo.

**[~~Afirmaciones normativas sin validar documentalmente~~ — resuelto]** → Era el riesgo mayor, heredado de `ADR-0012`: código de color, localizaciones y especificaciones venían de análisis derivados, no del texto. **Cerrado el 2026-08-03** contra el texto oficial (`ADR-0017`), que encontró tres colores mal, una regla inexistente y cuatro huecos estructurales. `docs/compliance/regulatory-matrix.md` cita ahora el artículo (6.1.x / 5.x), no un documento intermedio.

**[Riesgo que queda: la sigla es dato normativo, no etiqueta de UI]** → Las 38 siglas se imprimen **exactas** en el recuadro de la pieza. Un typo en el enum no lo atrapa ningún test de comportamiento: hay que verificarlas contra `ADR-0017` D7 una por una al escribir el catálogo, y dejar un test que las fije para que un rename no las arrastre.

**[El bloque inicial abierto es editable, y eso parece contradecir append-only]** → No lo contradice: mientras el bloque está `OPEN` se puede corregir libremente, igual que un `DRAFT` de `clinical-history`. La regla append-only protege lo **sellado** y, en fase 2, el cierre de hallazgos de bloques anteriores. En v1 solo hay un bloque en borrador, así que la distinción todavía no se nota — conviene tenerla escrita para que nadie "arregle" esto después.

## Migration Plan

Migraciones nuevas a partir de `V16` (la última en `dev` es `V15__clinical_history_diagnosis.sql` — **verificar el número real al escribirlas**, por si otro change se adelanta).

1. `V16__dental_chart_block.sql` — bloque + RLS + índices únicos parciales.
2. `V17__dental_chart_finding.sql` — hallazgos + RLS + `CHECK` de coherencia de localización.

**Sin backfill**: no hay odontogramas previos. Las Historias Clínicas ya selladas **quedan como están** — no se les exige odontograma retroactivamente (sería reescribir un documento sellado, justo lo que el modelo prohíbe). El checklist nuevo aplica solo a los sellados posteriores.

**Rollback**: las migraciones solo agregan tablas; revertirlas es dropearlas. Lo que **no** es trivial de revertir es el cambio en el sellado de `clinical-history` — si se revierte, las Historias selladas por el flujo coordinado quedan con un odontograma sellado que ya nadie exige, pero **sin inconsistencia de datos** (ambos quedan sellados y legibles).

## Open Questions

> ✅ **Las tres primeras se cerraron** el 2026-08-03 con el texto oficial de NTS 188 ([`ADR-0017`](../../../docs/adr/0017-catalogo-odontograma-nts-188.md)) — eran las tareas bloqueantes `0.1`, `0.2` y `0.3`.

1. ~~**El catálogo de `TipoCondicion` es tentativo**~~ — **cerrado** (`ADR-0017` D5 + D7): 38 hallazgos de 6.1.1–6.1.38, con sigla, subtipo, alcance y comportamiento de color. De lo que estaba por confirmar: la **recesión gingival** es hallazgo propio (6.1.31), la **giroversión** es azul **sin condicional** (6.1.13) y `NON_RESTORABLE` no existe en la norma — el término correcto es **remanente radicular** `RR` (6.1.32).
2. ~~**Índice de Higiene Oral (IHO)**~~ — **cerrado** (`ADR-0017` D8): **no aparece en NTS 188**. No pertenece al odontograma. Si resulta obligatorio será por NTS 139, en la Ficha Odonto-Estomatológica, y con su propio change.
3. ~~**Registro periodontal (sondaje, furca)**~~ — **cerrado** (`ADR-0017` D8): **no aparecen en NTS 188**. Lo periodontal que la norma sí exige ya entra como hallazgo del catálogo (movilidad 6.1.19, recesión 6.1.31); el **periodontograma** completo es documento aparte, con su propia grilla de seis sitios por pieza. Fuera de alcance, ahora por decisión y no por omisión.
4. **Alcance del SVG en v1** — ⚠️ **acotada el 2026-09-23, no cerrada.** La revisión externa propuso el criterio correcto: **abierta para la UI, cerrada para el contrato y el modelo**. El backend ya puede persistir las cinco localizaciones, la sigla y el marcador asociado a un hallazgo, así que la duda es de render, no de datos. **Mínimo exigible en v1**, y el resto queda para después:

   | Entra en v1 | Queda fuera |
   |---|---|
   | SVG inline | geometría visual sofisticada |
   | piezas y superficies **enfocables por teclado** | animaciones, zoom, paneo |
   | marcador de **rango** como **un solo** elemento accesible | dibujo fiel del trazo de la norma |
   | marcador de **arco** como **un solo** elemento accesible | |
   | lectura correcta de **tipo, sigla y localización** por lector de pantalla | |

   🔴 **Y una regla que no se negocia**: la geometría se **deriva en el frontend**. Ni coordenadas ni SVG persistido en el esquema — sería un dato derivado dentro de un documento médico-legal inmutable, que envejece con el diseño mientras el documento no puede cambiar.
