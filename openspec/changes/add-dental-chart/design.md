## Context

`ADR-0012` decidió el **modelo de dominio** del odontograma (aggregate, invariantes, value objects, semántica de proyección) y las nueve decisiones que lo sostienen. Este documento **no las repite**: resuelve el *cómo* implementarlas sobre el stack real.

Estado del que se parte: `clinical-history` está construida, archivada y testeada (sellado `DRAFT→COMPLETED` con checklist server-side); `FdiTooth` ya existe con el conjunto real de 52 piezas; el aislamiento por organización es automático por transacción (`TenantAwareTransactionManager`) pero **cada tabla declara la suya**. `treatments` y `treatment-plans` no existen.

Restricciones duras verificadas en el repo, no de memoria:
- ArchUnit: `@Async`/`@Scheduled` prohibidos · toda entidad con `@TenantId` debe declarar autoría · `presentation` no depende de `persistence` · `persistence` no depende de `service` ni `presentation`.
- `service → service` **sí** está permitido (`LayeredArchitectureTest` no lo restringe) — es lo que habilita el coordinador de D4.

## Goals / Non-Goals

**Goals:**
- Persistir el bloque inicial y sus hallazgos cumpliendo las invariantes de `ADR-0012`, con las de unicidad **garantizadas por la base**, no por chequeo previo.
- Sellar el odontograma inicial **atómicamente** con la Historia Clínica, sin crear dependencia entre los dos módulos.
- Registrar hallazgos con las tres formas de localización, con coherencia validada en el boundary.
- Una representación gráfica que un odontólogo reconozca como un odontograma.

**Non-Goals:** los de `proposal.md` (evolución, comparación entre fechas, cierre automático, integración con `treatments`, retención, COP). Además, acá tampoco se decide **el catálogo definitivo de tipos de condición**: la lista de `ADR-0012` es tentativa y debe revisarse con el odontólogo antes de fijarla en la migración (ver Open Questions).

## Decisions

### D1 — Dos tablas: bloque + hallazgos

`dental_chart_block` (el aggregate root) y `dental_chart_finding` (los hallazgos). No una sola tabla con JSONB: los hallazgos se consultan por pieza y por tipo (el render agrupa por pieza, la regla de contagio de color necesita "¿esta pieza tiene algún hallazgo que requiere intervención?"), y JSONB perdería los `CHECK` de integridad.

Ambas con `organization_id` NOT NULL + índice + `ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `organization_isolation`, **cada una la suya**. La RLS de Postgres **no es transitiva**: que `dental_chart_finding` tenga FK a un bloque aislado no la protege. Es el hallazgo que costó una auditoría entera en `add-clinical-history`.

*Alternativa descartada:* hallazgos como JSONB dentro del bloque (menos tablas, sin FK) — pierde los `CHECK` de coherencia de localización, complica las consultas por pieza y repetiría el error que `add-patients` evitó a propósito con la anamnesis (allí JSONB **sí** fue correcto porque no se consulta por dentro; acá sí se consulta).

### D2 — `Location` polimórfico: discriminador explícito + `CHECK`, no columnas implícitas

El `sealed interface Location` de `ADR-0012` (tres variantes) se persiste como un `@Embeddable` con **discriminador explícito**:

```
location_kind   VARCHAR  NOT NULL  CHECK (location_kind IN ('SURFACE','TOOTH','BETWEEN_TEETH'))
tooth_a         INT      NOT NULL              -- pieza principal (o la menor, si BETWEEN_TEETH)
tooth_b         INT      NULL                  -- solo BETWEEN_TEETH
surface         VARCHAR  NULL                  -- solo SURFACE
```

Más un `CHECK` que empareja discriminador y columnas: `SURFACE ⇒ surface NOT NULL AND tooth_b IS NULL`, `TOOTH ⇒ ambos NULL`, `BETWEEN_TEETH ⇒ tooth_b NOT NULL AND surface IS NULL`.

**Precedente exacto en el repo**: `clinical_history_diagnosis` ya hace esto con `ubicacion` + `fdi_number` + la constraint `clinical_history_diagnosis_location_paired`. Se replica el patrón, no se inventa uno.

*Alternativas descartadas:* (a) sin discriminador, infiriendo la variante de qué columnas están llenas — frágil e imposible de constrainear con claridad; (b) tabla por variante — tres joins para leer un odontograma; (c) JSONB — ver D1.

**Orden canónico en `BETWEEN_TEETH`**: se persiste siempre `tooth_a < tooth_b`, normalizado al construir el VO. Sin esto, `(11,21)` y `(21,11)` serían dos hallazgos distintos para el mismo diastema (INV-12).

### D3 — Las invariantes de unicidad van a la base, no al servicio

`INV-01` (un solo bloque abierto por paciente) e `INV-02` (un solo `INITIAL`) **no** se resuelven con un `existsBy...` previo: dos solicitudes concurrentes leen "no hay", y ambas crean.

Se implementan con **índices únicos parciales**:

```sql
CREATE UNIQUE INDEX ux_dental_chart_block_open   ON dental_chart_block (patient_id) WHERE status = 'OPEN';
CREATE UNIQUE INDEX ux_dental_chart_block_initial ON dental_chart_block (patient_id) WHERE type = 'INITIAL';
```

La `DataIntegrityViolationException` se traduce a la excepción de negocio correspondiente, igual que `HistoriaClinicaAlreadyExistsException` traduce el choque de `UNIQUE(patient_id)`. **Precedente pagado**: `add-clinical-history` tiene un test de concurrencia con 8 hilos reales contra PostgreSQL que existe exactamente porque el chequeo previo no alcanzaba.

### D4 — El coordinador vive en `service`, sobre ambos módulos

```
service/admission/CloseAdmissionService        @Transactional
   ├── service/clinicalhistory/ClinicalHistoryService.complete(...)
   └── service/dentalchart/DentalChartService.sealInitial(...)
```

Ninguno de los dos servicios de dominio conoce al otro. El coordinador depende de ambos; `service → service` no está restringido por ArchUnit.

**Transaccionalidad**: el `@Transactional` del coordinador abre la transacción; los métodos de cada servicio se unen a ella (propagación `REQUIRED` por defecto). Si el sellado del odontograma falla, revierte también el de la Historia. Importante para este proyecto: como el contexto de tenant se fija **al comenzar la transacción** (`TenantAwareTransactionManager.doBegin`), abrirla en el coordinador lo enlaza una sola vez para toda la operación.

**Superficie de API sin cambios**: el endpoint existente `POST /patients/{id}/clinical-history/complete` **conserva su ruta** y pasa a delegar en el coordinador. Cambia la semántica, no el contrato HTTP — el frontend no se toca por esto.

*Alternativa descartada:* endpoint nuevo `POST /patients/{id}/admission/close`, más honesto con el concepto pero rompe el cliente ya generado sin ganancia funcional.

### D5 — Qué exige el checklist de sellado (y qué no)

Al sellar, el checklist de `clinical-history` gana un ítem: **existe un odontograma inicial con especificaciones registradas**. La Historia sola ya no basta.

Deliberadamente **no** se exige un número mínimo de hallazgos: una boca sana es un odontograma legítimo y vacío de patología. Exigir "al menos un hallazgo" empujaría a inventar uno. Lo que sí se exige es que el odontograma **exista y haya sido revisado** — mismo criterio afirmativo que `alergiasRevisado` en `clinical-history`: la ausencia de hallazgos es un dato clínico, no un campo sin llenar.

### D6 — El catálogo de tipos: enum en la app, `CHECK` en la BD, atributos solo en código

Conjunto cerrado ⇒ **enum Java + `CHECK` en Postgres + contrato** (regla del proyecto, `add-patients` D8). Pero los tres atributos del tipo (`nature`, `scope`, `requiresGrade`) viven **solo en el enum Java**, no en columnas: son derivables del tipo, y almacenarlos permitiría que se desincronicen. La BD solo restringe el conjunto de valores válidos.

El frontend recibe el tipo por el contrato y **espeja** los atributos, igual que ya espeja `VALID_FDI_TEETH` desde `FdiTooth`.

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
| INV-10 (`scope` ↔ variante de `Location`), INV-11 (`requiresGrade` ⇔ `grade`), INV-13 (superficie aplicable a la pieza), INV-14 (FDI real) | `@ValidFindingLocation` — constraint Bean Validation **a nivel de objeto** sobre el DTO, mismo patrón que `@ValidDiagnosisLocation` (`@Constraint` + `ConstraintValidator` + `addPropertyNode` para reportar sobre el campo correcto) |
| INV-01, INV-02, INV-04 | índice único parcial + traducción de la violación (D3) |
| INV-03 (sellado inmutable), INV-06/07/08 (asientos), INV-09 (pieza ausente sin superficies) | servicio — requieren estado ya persistido |
| INV-15 (dentición ↔ edad) | servicio, como **aviso** en la respuesta; nunca rechaza |

INV-13 (qué superficies admite cada pieza) necesita saber si la pieza es anterior o posterior: se deriva del número FDI en `FdiTooth`, extendiéndolo — no se crea un catálogo nuevo.

### D10 — Frontend: SVG inline, colores desde tokens

La representación es **SVG inline**, no canvas ni imágenes: cada pieza y cada superficie deben ser **individualmente enfocables y accionables** (teclado y lector de pantalla), y con canvas eso hay que reconstruirlo a mano. Un odontograma donde solo se puede hacer clic con mouse deja fuera a parte de los usuarios y no pasaría una auditoría de accesibilidad.

**El color se deriva de `nature` vía tokens semánticos del tema, nunca hexadecimales** — el proyecto tematiza por clínica y un color fijo rompe la marca (regla de `frontend-guard`). La regla de contagio de `ADR-0012` (una pieza con patología pinta todos sus marcadores en rojo) se calcula en el render, no se almacena.

*Riesgo asumido:* es la pieza de frontend más grande construida hasta ahora en el proyecto. Ver Riesgos.

## Risks / Trade-offs

**[El odontograma SVG es la pieza de UI más grande del proyecto]** → Se acota a lo que la norma exige: dentición según edad, superficies clicables, marcadores por tipo. Sin animaciones, sin zoom, sin arrastrar. Si aun así desborda el change, el corte natural es entregar primero el registro (piezas completas) y las superficies después — pero **eso degradaría la precisión cara-por-cara que `ADR-0012` D8 declara innegociable por auditoría de SUSALUD**, así que sería un corte a discutir, no a tomar sobre la marcha.

**[Romper el sellado de una capacidad construida]** → Los tests existentes de `clinical-history` que sellan la Historia sin odontograma **van a fallar, por diseño**. Es trabajo previsto, no un efecto colateral: se actualizan al nuevo contrato en el mismo change. El riesgo real sería *no* verlos fallar — significaría que el checklist no se está aplicando.

**[Dentición mixta y piezas fuera de lo esperado]** → El modelo localiza por FDI, así que temporales y permanentes coexisten sin caso especial. La incoherencia con la edad **avisa pero no bloquea** (INV-15): una temporal retenida en un adulto es rara y real, y bloquearla impediría registrar un caso legítimo.

**[Afirmaciones normativas sin validar documentalmente]** → Heredado de `ADR-0012`: el código de color, la representación de diastema/supernumerario y la obligatoriedad de las especificaciones vienen de la práctica del decisor, no de los textos de la NTS. **Las decisiones técnicas se sostienen igual; lo que no se puede afirmar todavía es que cumplan la norma.** Completar la columna § de `docs/compliance/regulatory-matrix.md` antes de presentar esto como evidencia de cumplimiento.

**[El bloque inicial abierto es editable, y eso parece contradecir append-only]** → No lo contradice: mientras el bloque está `OPEN` se puede corregir libremente, igual que un `DRAFT` de `clinical-history`. La regla append-only protege lo **sellado** y, en fase 2, el cierre de hallazgos de bloques anteriores. En v1 solo hay un bloque en borrador, así que la distinción todavía no se nota — conviene tenerla escrita para que nadie "arregle" esto después.

## Migration Plan

Migraciones nuevas a partir de `V16` (la última en `dev` es `V15__clinical_history_diagnosis.sql` — **verificar el número real al escribirlas**, por si otro change se adelanta).

1. `V16__dental_chart_block.sql` — bloque + RLS + índices únicos parciales.
2. `V17__dental_chart_finding.sql` — hallazgos + RLS + `CHECK` de coherencia de localización.

**Sin backfill**: no hay odontogramas previos. Las Historias Clínicas ya selladas **quedan como están** — no se les exige odontograma retroactivamente (sería reescribir un documento sellado, justo lo que el modelo prohíbe). El checklist nuevo aplica solo a los sellados posteriores.

**Rollback**: las migraciones solo agregan tablas; revertirlas es dropearlas. Lo que **no** es trivial de revertir es el cambio en el sellado de `clinical-history` — si se revierte, las Historias selladas por el flujo coordinado quedan con un odontograma sellado que ya nadie exige, pero **sin inconsistencia de datos** (ambos quedan sellados y legibles).

## Open Questions

1. **El catálogo de `TipoCondicion` es tentativo.** La lista de `ADR-0012` salió de la sesión de modelado, no de una lectura exhaustiva de la norma. **Revisarla con el odontólogo antes de escribir la migración** (tarea 0.1, bloqueante): agregar un tipo después cuesta migración de datos. Fuentes a contrastar: **NTS 150 y sus anexos gráficos**, el Colegio Odontológico y la práctica clínica peruana. Faltan por confirmar al menos: recesión gingival (¿va solo en especificaciones?), giroversión (`ADR-0012` la deja con naturaleza fija y color por contagio), y si `NON_RESTORABLE` es el nombre correcto o debería ser el término de la norma.

2. **⚠️ Índice de Higiene Oral (IHO) — sin decidir, y podría no ser un tipo de condición.** No aparece en ninguna parte del proyecto (verificado: cero menciones en `docs/` y `openspec/`); lo trae la revisión de este change. Si resulta ser **obligatorio** en el examen odontológico inicial, **no encaja como `TipoCondicion`**: es un **índice numérico calculado** sobre superficies con placa, con su propio método de medición y su valor agregado — otra forma de dato, no un hallazgo localizado. Eso lo haría un requirement adicional de esta capacidad, no un valor más del catálogo. **Investigar antes de la migración**, junto con 0.1: si es obligatorio y se omite, el odontograma quedaría incompleto ante auditoría; si se modela mal (como un tipo de hallazgo), corregirlo después cuesta migración.

3. **⚠️ Registro periodontal (sondaje, furca) — fuera de alcance, sin decisión formal.** El modelo cubre **movilidad** (tipo con grado) y **recesión gingival** (texto en especificaciones), pero **sondaje periodontal y compromiso de furca no están contemplados en ningún artefacto** (verificado). En la práctica suelen vivir en un **periodontograma**, documento distinto del odontograma, con su propia grilla de seis sitios por pieza. Queda **fuera de este change** — pero hay que decidir explícitamente si es (a) una capacidad futura propia, (b) parte de `update-dental-chart`, o (c) texto en especificaciones. Dejarlo sin decidir es lo peligroso: es la clase de cosa que después se mete a presión en el modelo equivocado.

4. **Alcance del SVG en v1** — ver Riesgos. Si el odontograma completo desborda, el corte hay que decidirlo explícitamente, no improvisarlo.
