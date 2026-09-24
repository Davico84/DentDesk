## 0. Antes de escribir la migración (bloquea al grupo 1)

- [x] 0.1 ~~Revisar el catálogo de tipos de condición y fijarlo~~ — **hecho** (2026-08-03) contra el **texto oficial de NTS 188**, no un análisis derivado: [`ADR-0017`](../../../docs/adr/0017-catalogo-odontograma-nts-188.md) cierra los **38 hallazgos** (6.1.1–6.1.38) con sigla, subtipo, alcance y color. Resuelve los dos hallazgos abiertos: **(a)** `DES` se registra como hallazgo **de superficie** y la sigla del recuadro se **deriva** al renderizar (D7); **(b)** `IMP` **no** implica pieza ausente — **coexiste** con ella (un implante sin corona está en una pieza ausente, uno con corona no), así que la invariante propuesta habría rechazado registros válidos (D7)
- [x] 0.2 ~~Decidir si el IHO es obligatorio~~ — **hecho**: **no está en NTS 188**, no es requisito del odontograma. Fuera de alcance (`ADR-0017` **D8**)
- [x] 0.3 ~~Decidir dónde vive el registro periodontal~~ — **hecho**: **no está en NTS 188**. Lo periodontal que la norma sí exige (**movilidad 6.1.19**) ya entra como hallazgo del catálogo. 🔴 **Esta línea decía además "recesión 6.1.31, furca" y tenía dos errores**: 6.1.31 es la **prótesis dental parcial removible** en el texto oficial, y la **furca está fuera de alcance** por `ADR-0017` D8 — decirla dentro contradecía el propio proposal; el **periodontograma** completo es documento aparte, fuera de alcance (`ADR-0017` **D8**)
- [x] 0.4 ~~Completar la columna **§** de `docs/compliance/regulatory-matrix.md`~~ — **hecho** (2026-08-03) con los numerales de NTS 139 (5.4.1 f/i, 6.1.1 a, 6.1.4, 6.6.1), NTS 188 (6.1, 6.2, Anexo N°01), COP y las leyes 29733 / 27269
- [x] 0.5 Verificar el número real de la última migración en `dev` — ✅ **verificado 2026-09-23**: sigue siendo `V15__clinical_history_diagnosis.sql`, ningún change se adelantó. **La primera migración de este change es `V16`.**

### 0.6 Realinear los artefactos con `ADR-0017` (bloquea al grupo 1) — ✅ hecho (2026-08-03)

Proposal, design, spec y tasks se escribieron **antes** de tener el texto oficial. Implementar sobre ellos habría construido el modelo equivocado.

- [x] 0.6.1 `design.md` **D6** — catálogo `tipo` + `subtipo` con la **sigla oficial** como atributo normativo, y tres comportamientos de color (`SIEMPRE_AZUL`, `SIEMPRE_ROJO`, `SEGUN_ESTADO`) en vez de la naturaleza clínica fija. "Temporal" colapsa en `SIEMPRE_ROJO`, sin eje propio (`ADR-0017` D1/D5)
- [x] 0.6.2 `design.md` **D2** — `Location` gana `TOOTH_RANGE` y `ARCH`: cinco formas, no tres. El rango va en **tabla hija** con su propia RLS (un conjunto ordenado de cardinalidad variable no cabe en columnas planas) (`ADR-0017` D4)
- [x] 0.6.3 `design.md` **D10** — colores corregidos (movilidad roja; extruida e intruida azules) y **eliminada la regla de contagio** (`ADR-0017` D2/D7)
- [x] 0.6.4 `spec.md` — el requirement de localización pasa a cinco formas, con scenarios de rango y arco; el de catálogo adopta subtipo, sigla y los tres comportamientos
- [x] 0.6.5 `spec.md` — **Especificaciones** y **Observaciones** como requirement de dos campos distintos, ambos exigidos por el checklist de sellado (`ADR-0017` D6)
- [x] 0.6.6 `spec.md` + `proposal.md` — fuera el **bloque diario**: el odontograma se genera **por evento** (5.10/5.11). El cierre diario es de la nota de evolución de NTS 139, no de este documento (`ADR-0017` D3)
- [x] 0.6.7 `spec.md` — scenarios que fijan que **cada hallazgo se pinta por su propio estado**: la giroversión sigue azul junto a una caries, y el implante **coexiste** con la ausencia sin implicarla (`ADR-0017` D2/D7)
- [x] 0.6.8 `openspec validate add-dental-chart --strict` en verde

### 0.7 Re-verificar el catálogo completo contra el PDF versionado (bloquea el `CHECK` de 1.3)

> 🔴 Abierto el **2026-09-23** por una revisión externa del change. El PDF de NTS 188 **ya está versionado** en `docs/compliance/normas/`, así que esto ya no depende de conseguir nada.
>
> **Por qué hace falta**: se encontró que `ADR-0017` es **internamente inconsistente** — su tabla asigna `6.1.31` a *prótesis dental parcial removible* (✅ correcto según el texto oficial) mientras que el proposal, las tasks y la matriz decían que `6.1.31` era *recesión gingival* (🔴 falso, ya corregido). Si una entrada se desalineó, **las otras 37 no están garantizadas**.

- [x] 0.7.1 Extraer las 24 páginas como imagen y recorrer el catálogo entrada por entrada — ✅ **hecho 2026-09-23**
- [x] 0.7.2 Fijar la tabla definitiva — ✅ **la tabla de `ADR-0017` D7 es CORRECTA en las 38 filas**: numeral, sigla, color y alcance. No hubo que corregir ninguna. Los errores estaban en la matriz (implante atribuido a 6.1.16) y en proposal/tasks (recesión atribuida a 6.1.31), ya corregidos
- [x] 0.7.3 Recesión gingival — ✅ **NO está en NTS 188**. El catálogo es alfabético y entre 6.1.31 (prótesis parcial removible) y 6.1.32 (remanente radicular) no hay hueco. `ADR-0017` D8 corregido: lo único periodontal del catálogo es la **movilidad** (6.1.19)
- [x] 0.7.4 Furca — ✅ **no aparece**. `ADR-0017` D8 tenía razón; la contradicción de este archivo ya está corregida
- [x] 0.7.5 Typos de la norma — ✅ **dos**, ambos anotados `[sic]` en `ADR-0017` D7: `5.2.23` (pieza en erupción, debería ser 6.1.23) y `6.115` (implante dental, debería ser 6.1.15)
- [x] 0.7.6 **Hallazgo nuevo**: la **sigla no es única**. `M` es movilidad (6.1.19, rojo, con dígito) y también mesializado (6.1.28, azul, sola); `S` es supernumeraria (6.1.26) y también sellante (6.1.35). ⚠️ **La sigla no puede ser clave** ni identificar un hallazgo: la clave es `tipo`+`subtipo` y la sigla es atributo de salida (`ADR-0017` D7)

✅ **Cerrado el 2026-09-23. El `CHECK` ya se puede escribir**, con tres avisos que salieron de la verificación:
>
> 1. Los numerales `5.2.23` y `6.115` son **así en la norma**. Si el `CHECK` o el enum los "corrige" a 6.1.23 y 6.1.15, deja de coincidir con el documento que un auditor tiene en la mano. Se guardan como los imprime la norma, con el corregido por posición al lado.
> 2. **Ningún índice único sobre la sigla** (0.7.6).
> 3. El catálogo está en **orden alfabético por nombre**, no por alcance ni por color. Es útil para auditarlo, y explica por qué la ausencia de la recesión gingival es demostrable.

## 1. Modelo y persistencia (backend)

> 🔴 **Ampliado el 2026-09-23 tras una revisión externa del change.** Cinco cosas no estaban: FK compuestas por tenant, el `CHECK` a prueba de `NULL`, los pares `_revisado`, la unicidad de *hallazgos* (solo estaba la de bloques) y la coherencia del sellado. Todas son de esquema, así que entran **antes** de la primera migración — corregirlas después es una migración sobre datos clínicos. Ver `design.md` D1, D2, D3, D5, D6 y D9.

- [x] 1.0a **FK compuestas por tenant** en las tres relaciones nuevas: `UNIQUE (organization_id, id)` en cada padre y `FOREIGN KEY (organization_id, <fk>)` en cada hijo (`finding → block`, `finding_tooth → finding`, `block → patient`). ⚠️ **No copiar el patrón de `V14`/`V15`**, que usan FK separadas y arrastran la deuda (design.md D1)
- [x] 1.0b **Tabla de catálogo** `dental_finding_catalog` con clave `(type, subtype)`, cargada por migración al modo `Cie10CatalogLoader`, con `official_numeral` **y** `numeral_by_position` (la norma tiene dos typos: `5.2.23` y `6.115`). ⚠️ **Sin `UNIQUE` sobre la sigla** — colisiona (`M`, `S`). El `finding` referencia el par por FK en vez de un `CHECK` de 38 pares (design.md D6)
- [x] 1.0c **`CHECK` de localización a prueba de `NULL`**: una rama `CASE` por cada uno de los cinco `location_kind`, con `IS NULL`/`IS NOT NULL` explícitos y negando las columnas que no corresponden. ⚠️ En Postgres un `CHECK` que evalúa a `NULL` **se considera satisfecho**: escrito con expresiones nullables no valida nada, en silencio (design.md D2)
- [x] 1.0d **Conjuntos cerrados en la base**: `arch IS NULL OR arch IN (...)`, `surface IS NULL OR surface IN (...)`, y `tooth_a < tooth_b` para `BETWEEN_TEETH` — el orden canónico es invariante de dato, no solo del VO (design.md D2)
- [x] 1.0e **Pares `<sección>_reviewed`** para especificaciones y observaciones, replicando `V14__historia_clinica.sql:26` (`alergias_revisado`), más `CHECK (texto IS NULL OR reviewed)`. Sin esto, "revisado y vacío" y "nunca revisado" son indistinguibles y el spec exige distinguirlos (design.md D5)
- [x] 1.0f **Coherencia del sellado**: `CHECK ((status='OPEN' AND sealed_at IS NULL) OR (status='SEALED' AND sealed_at IS NOT NULL))` (design.md D9)
- [x] 1.0g **Índices únicos parciales de HALLAZGOS**, uno por variante de localización de cardinalidad fija (`SURFACE`, `TOOTH`, `BETWEEN_TEETH`, `ARCH`). ⚠️ `TOOTH_RANGE` queda fuera por diseño: su identidad vive en la tabla hija y se garantiza con el bloqueo del `finding` (design.md D3)
- [x] 1.0h **Invariantes de la tabla hija**: `CHECK (position >= 1)`, `UNIQUE (finding_id, position)`, `UNIQUE (finding_id, tooth)` e **índice por `tooth`** — la consulta "¿qué hallazgos tocan la 36?" es la razón de ser de la tabla y no tenía índice (design.md D2)


- [x] 1.1 Migración Flyway `dental_chart_block`: `organization_id` NOT NULL + índice, `patient_id`, `type` CHECK `INITIAL`, `status` CHECK `OPEN|SEALED`, `examined_on`, `sealed_at`, **`specifications` y `observations` (dos columnas, `ADR-0017` D6)**, autoría + timestamps · `ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `organization_isolation` (patrón de `V14__historia_clinica.sql`) (design.md D1)
- [x] 1.2 Índices **únicos parciales** en la misma migración: uno por `patient_id WHERE status='OPEN'` (un odontograma abierto por paciente — la cronología de `ADR-0012` D7; **ya no es "por día"**, `ADR-0017` D3), otro por `patient_id WHERE type='INITIAL'` — las invariantes de unicidad las garantiza la base, no un chequeo previo (design.md D3)
- [x] 1.3 Migración Flyway `dental_chart_finding`: `organization_id` NOT NULL + índice, FK al bloque, `location_kind` CHECK con **cinco** valores (`SURFACE|TOOTH|BETWEEN_TEETH|TOOTH_RANGE|ARCH`), `tooth_a`, `tooth_b`, `surface`, **`arch`**, `type` + **`subtype`** CHECK contra el catálogo de `ADR-0017` D7, `grade` nullable, **`condition` nullable** (buen/mal estado — solo en los `SEGUN_ESTADO`), autoría · RLS propia igual que 1.1 (design.md D1/D2)
- [x] 1.4 `CHECK` de coherencia de localización en `dental_chart_finding` (discriminador ↔ columnas pobladas, los cinco casos), mismo patrón que `clinical_history_diagnosis_location_paired`. Más un `CHECK` que valida el **par** `type`↔`subtype`: un subtipo válido bajo otro tipo debe rechazarse (design.md D2/D6)
- [x] 1.4b Migración Flyway `dental_chart_finding_tooth` (`finding_id`, `position`, `tooth`) para los hallazgos de **rango**, con `UNIQUE (finding_id, position)` · **RLS propia** — la de la tabla padre no la protege, es el mismo hallazgo que costó la auditoría de `add-clinical-history` (design.md D2)
- [x] 1.5 Enums de dominio: los **38 tipos** de `ADR-0017` D7 con sus atributos **solo en código** (no en columnas — son derivables y se desincronizarían): `colorBehavior`, `scope` (cinco), `subtypes`, `requiresGrade`, `requiresSpecification`; y el subtipo con su **`sigla` oficial**. Más estado y tipo de odontograma, forma de localización, arco, superficie dental y estado de conservación (design.md D6)
- [x] 1.5b **Test que fija las 38 siglas** una por una contra `ADR-0017` D7. Son dato normativo que se imprime exacto: un typo no lo atrapa ningún test de comportamiento, y un rename del enum las arrastraría
- [x] 1.6 Extender `FdiTooth` con las **superficies aplicables** por pieza (anterior vs. posterior: incisal vs. oclusal) — extender lo construido, no crear un catálogo nuevo (design.md D7)
- [x] 1.7 Entidades JPA `DentalChartBlock` + `DentalChartFinding` con `@TenantId`, `@EntityListeners(AuditingEntityListener.class)` + `@CreatedBy`/`@LastModifiedBy` (exigido por el candado ArchUnit `business_entities_with_tenant_id_must_declare_authorship`), y `Location` como `@Embeddable` con discriminador
- [x] 1.8 Test de aislamiento por clínica análogo a `ClinicalHistoryRlsIT` (SQL crudo, bypass de JPA, como rol de aplicación no-superusuario) para **ambas** tablas — la RLS no se hereda por FK (spec: *Aislamiento por clínica*)

## 2. Servicio de dominio: creación y hallazgos (backend)

- [x] 2.1 Crear el odontograma inicial en estado abierto; rechazar si ya existe; unicidad por índice único parcial (no solo chequeo previo), tolerante a creación concurrente (spec: *Odontograma inicial único por paciente y clínica*)
- [x] 2.2 `@ValidFindingLocation` — constraint Bean Validation a **nivel de objeto** sobre el DTO del hallazgo, mismo patrón que `@ValidDiagnosisLocation`: coherencia alcance↔forma de localización (cinco), FDI real, superficie aplicable a la pieza, **rango con más de una pieza**, grado exigido/prohibido según el tipo, **subtipo exigido/prohibido y perteneciente al tipo**, y **estado de conservación exigido solo en los `SEGUN_ESTADO`**. Reporta sobre el campo correcto vía `addPropertyNode`, no una excepción genérica (design.md D9)
- [x] 2.3 Normalizar el orden canónico en la localización entre piezas (`tooth_a < tooth_b`) al construir el value object, para que `(11,21)` y `(21,11)` sean la misma localización (design.md D2, spec: *El mismo espacio entre piezas no se duplica por el orden*)
- [x] 2.4 Agregar / corregir / eliminar hallazgos **solo mientras el bloque esté abierto**; rechazar hallazgo de superficie sobre pieza marcada como ausente; rechazar duplicado exacto de tipo+localización
- [x] 2.5 Registrar **especificaciones y observaciones** como campos distintos: 5.14 detalla lo que sí está en el catálogo y no cabe dibujado; 5.15 es la vía para lo que la nomenclatura no contempla. Uno no cubre al otro (spec: *Especificaciones y observaciones del odontograma*, `ADR-0017` D6)
- [x] 2.6 Aviso **no bloqueante** de incoherencia dentición↔edad: registra el hallazgo y devuelve la advertencia (INV-15 — una pieza temporal retenida en un adulto es rara pero real; bloquearla impediría registrar un caso legítimo)
- [x] 2.7 Tests de servicio, uno por Scenario: crear, segundo rechazado, **creación concurrente con hilos reales** contra PostgreSQL (patrón de `ClinicalHistoryServiceIT`), hallazgo por superficie / pieza / entre piezas / **rango** / **arco**, pieza inexistente, superficie no aplicable, localización incoherente con el alcance, **rango de una sola pieza rechazado**, pieza ausente con superficie, duplicado por orden invertido, corrección con el odontograma abierto, grado exigido/prohibido, **subtipo exigido/prohibido y ajeno al tipo**, **restauración en buen vs. mal estado (mismo tipo, comportamiento distinto)**, estado exigido/prohibido según el comportamiento, **sellante y caries coexistiendo en la misma pieza**, **giroversión que no cambia por una caries vecina**, **implante que coexiste con la ausencia**, aviso por edad

## 3. Sellado coordinado con la admisión (backend) — **toca una capacidad construida**

- [ ] 3.1 Operación de sellado del odontograma en `dental-chart`: valida **especificaciones Y observaciones** registradas (por separado) **y las entradas que exigen tipos concretos** (movilidad ⇒ clasificación · corona ⇒ color del metal · fluorosis ⇒ clasificación, `ADR-0017` D6), sella (`sealed_at`, `status=SEALED`), transición **unidireccional**; permite sellar sin hallazgos (una boca sana es un registro válido) (spec: *Odontograma sellado e inmutable*)
- [ ] 3.2 Rechazar **en el servidor** toda modificación de un odontograma sellado, aunque la petición llegue directa a la API — con `errorType` dedicado, mismo patrón que `HistoriaClinicaSealedException` (spec: *Rechazar la modificación de un odontograma sellado aun por API directa*)
- [ ] 3.3 **Caso de uso coordinador** `CloseAdmissionService` en `service/admission/`, `@Transactional`, que llama a `ClinicalHistoryService.complete(...)` y a la operación de 3.1. Ninguno de los dos módulos se conoce entre sí; el coordinador depende de ambos (design.md D4)
- [ ] 3.4 Agregar el odontograma al checklist de sellado de `clinical-history` y hacer que el endpoint existente `POST /patients/{id}/clinical-history/complete` delegue en el coordinador — **conserva la ruta**, cambia la semántica (design.md D4/D5)
- [ ] 3.5 **Actualizar los tests existentes de `clinical-history` que sellan sin odontograma**: van a fallar por diseño. Que fallen es la señal de que el checklist se aplica; el riesgo sería no verlos fallar
- [ ] 3.6 Tests del sellado coordinado: sella ambos en una operación · rechaza sin odontograma indicando qué falta · **un fallo al sellar el odontograma deja la Historia en `DRAFT`** (rollback verificado, no asumido) · Historias selladas antes de la regla no se ven afectadas · transición inválida rechazada

## 4. Contrato y API (backend)

- [ ] 4.1 DTOs de request/response (crear odontograma, agregar/eliminar hallazgo, **especificaciones y observaciones por separado**, consultar) + controller de la capacidad. El catálogo viaja con **tipo, subtipo, sigla, alcance y comportamiento de color** para que el frontend no los reinvente
- [ ] 4.2 El aviso de dentición↔edad viaja en la respuesta como dato, no como error (es no bloqueante) — decidir su forma en el contrato, no inventarla en el cliente
- [ ] 4.3 Regenerar `contracts/openapi.json` + cliente orval (**commit aparte**); `ContractDriftIT` en verde

## 5. Frontend

- [ ] 5.1 Hooks React Query sobre el cliente generado (crear, consultar, agregar/eliminar hallazgo, especificaciones) (`docs/frontend.md`)
- [ ] 5.2 Schemas Zod con **paridad** a Bean Validation: alcance↔localización (cinco), FDI real, superficie aplicable, rango con más de una pieza, grado condicional, subtipo condicional y perteneciente al tipo (`docs/coding-style.md §7`). Espejar el catálogo desde el contrato, igual que `VALID_FDI_TEETH` espeja `FdiTooth`
- [ ] 5.3 Odontograma **SVG inline** en `modules/dental-chart/`: cada pieza y superficie **enfocable y accionable por teclado**, con nombre accesible — no canvas ni imagen (design.md D8)
- [ ] 5.4 ⚠️ Colores **normativos, NO del tema**: rojo `#FF0000` y azul `#0000FF` exactos, sobre **fondo blanco siempre**, aunque la app esté en modo oscuro — es un documento legal y lo mostrado debe ser idéntico a lo auditado (`ADR-0013` D5). **Única excepción a la regla de `frontend-guard`**: dejarlo comentado en el código, porque a simple vista parece la violación que esa regla persigue. El color deriva **solo** del `colorBehavior` del tipo y —en los `SEGUN_ESTADO`— del estado del propio hallazgo: **no hay regla de contagio** (`ADR-0017` D2). El color **nunca** es el único portador de significado: la sigla oficial y la forma también lo comunican
- [ ] 5.5 Dentición según la edad del paciente (temporal / mixta / permanente), derivada, con las piezas de ambas denticiones coexistiendo cuando corresponde
- [ ] 5.5b Marcadores que **cruzan varias piezas**: prótesis parcial y ortodoncia fija abarcan un rango; edéntulo total y prótesis completa, un arco entero. No son un marcador por pieza — es la parte del SVG que el modelo anterior no contemplaba (`ADR-0017` D4)
- [ ] 5.6 Odontograma sellado en **modo lectura**, con especificaciones y observaciones visibles por separado; el aviso de dentición↔edad se muestra sin bloquear
- [ ] 5.6b La pantalla de **Historia Clínica** refleja el requisito nuevo: muestra que falta el odontograma y enlaza a él. La ruta de `complete` no cambia, pero el flujo sí — sin esto el odontólogo choca con un rechazo sin entender por qué (design.md D4)
- [ ] 5.7 Tests Vitest/MSW: registrar hallazgo por superficie · rechazar superficie no aplicable · hallazgo entre piezas · **hallazgo de rango dibujado como un marcador, no como N** · odontograma sellado en solo lectura · aviso no bloqueante por edad · navegación por teclado sobre las piezas · **restauración en mal estado se pinta en rojo y en buen estado en azul** · **la giroversión sigue azul junto a una caries en la misma pieza** · el odontograma conserva fondo blanco con la app en modo oscuro

### 2.8 Tests que la revisión externa dejó al descubierto

- [ ] 2.8a Test de **concurrencia con hilos reales** del sellado coordinado, verificando que **no hay deadlock**: el orden de bloqueo es paciente → historia → odontograma en **toda** ruta (design.md D4). Un test secuencial no lo detecta
- [x] 2.8b Test de **duplicado concurrente de hallazgos** — no solo de creación de bloque, que es lo único que había (design.md D3)
- [x] 2.8c Test de **RLS de la tabla hija por separado**: son **tres** tablas de negocio nuevas, no dos. La RLS no se hereda por FK
- [ ] 2.8d Test de que un hallazgo **no puede apuntar a un bloque de otra organización**, aun declarando su propio `organization_id` (design.md D1)
- [ ] 2.8e Test de que **`sealInitial()` no es invocable fuera del coordinador** — si lo fuera, caería la invariante de atomicidad de `ADR-0012` D5 (design.md D4)
- [ ] 2.8f Test de que **agregar un hallazgo mientras otra transacción sella** no deja el odontograma sellado con un hallazgo posterior a su fecha de sellado (design.md D9)

## 6. Gobernanza y cierre

- [ ] 6.1 Al archivar: reemplazar el stub `Tooth` + `ToothCondition` de `docs/domain.md` por el modelo real (odontograma, hallazgo, localización de cinco formas, catálogo tipo+subtipo) y actualizar el ER
- [ ] 6.2 Al archivar: completar en `docs/compliance/regulatory-matrix.md` las filas del odontograma — de `pendiente` al requirement/scenario concreto, y su estado a ✅. El § ya cita el texto oficial; **no rebajarlo a un documento derivado** (`docs/adr/README.md`, dimensión 1)
- [ ] 6.3 Al archivar: marcar `dental-chart` como construida y enlazar su `spec.md` en `docs/vision.md`; anotar que la fase 2 (evolución) va junto con `update-clinical-history`
- [ ] 6.4 `mvn verify` (back) y `pnpm validate` (front) en verde; `openspec validate add-dental-chart --strict`
