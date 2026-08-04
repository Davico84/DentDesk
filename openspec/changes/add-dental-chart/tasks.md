## 0. Antes de escribir la migración (bloquea al grupo 1)

- [x] 0.1 ~~Revisar el catálogo de tipos de condición y fijarlo~~ — **hecho** (2026-08-03) contra el **texto oficial de NTS 188**, no un análisis derivado: [`ADR-0017`](../../../docs/adr/0017-catalogo-odontograma-nts-188.md) cierra los **38 hallazgos** (6.1.1–6.1.38) con sigla, subtipo, alcance y color. Resuelve los dos hallazgos abiertos: **(a)** `DES` se registra como hallazgo **de superficie** y la sigla del recuadro se **deriva** al renderizar (D7); **(b)** `IMP` **no** implica pieza ausente — **coexiste** con ella (un implante sin corona está en una pieza ausente, uno con corona no), así que la invariante propuesta habría rechazado registros válidos (D7)
- [x] 0.2 ~~Decidir si el IHO es obligatorio~~ — **hecho**: **no está en NTS 188**, no es requisito del odontograma. Fuera de alcance (`ADR-0017` **D8**)
- [x] 0.3 ~~Decidir dónde vive el registro periodontal~~ — **hecho**: **no está en NTS 188**. Lo periodontal que la norma sí exige (movilidad 6.1.19, recesión 6.1.31, furca) ya entra como hallazgo del catálogo; el **periodontograma** completo es documento aparte, fuera de alcance (`ADR-0017` **D8**)
- [x] 0.4 ~~Completar la columna **§** de `docs/compliance/regulatory-matrix.md`~~ — **hecho** (2026-08-03) con los numerales de NTS 139 (5.4.1 f/i, 6.1.1 a, 6.1.4, 6.6.1), NTS 188 (6.1, 6.2, Anexo N°01), COP y las leyes 29733 / 27269
- [ ] 0.5 Verificar el número real de la última migración en `dev` (hoy `V15__clinical_history_diagnosis.sql`) por si otro change se adelantó

### 0.6 Realinear los artefactos con `ADR-0017` (bloquea al grupo 1)

Proposal, design, spec y tasks se escribieron **antes** de tener el texto oficial. Implementar sobre ellos construiría el modelo equivocado.

- [ ] 0.6.1 `design.md` **D6** — el catálogo pasa a `type` + `subtipo` con la **sigla oficial** como atributo normativo, y el color deja de ser un booleano patológico: son **tres comportamientos** (`SIEMPRE_AZUL`, `SIEMPRE_ROJO`, `SEGUN_ESTADO`). "Temporal" **no** es un eje aparte: colapsa en `SIEMPRE_ROJO` (`ADR-0017` D1/D5)
- [ ] 0.6.2 `design.md` **D2** — `Location` gana `EnRango(piezas[])` y `EnArco(maxilar)`: son **cinco** formas, no tres. Ajustar el discriminador, el `CHECK` y las columnas (`ADR-0017` D4)
- [ ] 0.6.3 `design.md` **D10** — corregir los colores del render: movilidad, extruida e intruida estaban mal asignadas (`ADR-0017` D7)
- [ ] 0.6.4 `spec.md` — el requirement *Registro de hallazgos con localización precisa* dice **"una de tres formas"**: pasa a cinco, con sus scenarios de rango y arco. El requirement de catálogo pierde la clasificación *patológico / descriptivo / restauración* y adopta los tres comportamientos de color
- [ ] 0.6.5 `spec.md` — **Especificaciones (5.14)** y **Observaciones (5.15)** son **dos campos distintos**, no uno: 5.14 es lo que no cabe en el recuadro, 5.15 es lo que la nomenclatura no contempla. Hoy el spec funde ambos (`ADR-0017` D6)
- [ ] 0.6.6 `spec.md` + `proposal.md` — quitar el modelo de **bloque diario**: NTS 188 (5.10/5.11) genera odontograma **por evento** (inicio y fin del plan, hallazgo nuevo, reingreso, pedido judicial). El agregado sobrevive; cambia el disparador (`ADR-0017` D3, supersede `ADR-0012` D4)
- [ ] 0.6.7 `spec.md` — eliminar el scenario que apoya la **regla de contagio**: no existe en la norma; cada marcador se pinta por su propio estado (`ADR-0017` D2, supersede `ADR-0012` D2)
- [ ] 0.6.8 `openspec validate add-dental-chart --strict` en verde tras los cambios

## 1. Modelo y persistencia (backend)

> ⚠️ Los grupos 1–8 se escribieron **antes** de `ADR-0017` y todavía citan el modelo anterior (tres formas de localización, catálogo sin siglas, un solo campo de especificaciones). **Releerlos después de 0.6**, que es lo que los corrige.

- [ ] 1.1 Migración Flyway `dental_chart_block`: `organization_id` NOT NULL + índice, `patient_id`, `type` CHECK `INITIAL`, `status` CHECK `OPEN|SEALED`, `examined_on`, `sealed_at`, `specifications`, autoría + timestamps · `ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `organization_isolation` (patrón de `V14__historia_clinica.sql`) (design.md D1)
- [ ] 1.2 Índices **únicos parciales** en la misma migración: uno por `patient_id WHERE status='OPEN'`, otro por `patient_id WHERE type='INITIAL'` — las invariantes de unicidad las garantiza la base, no un chequeo previo (design.md D3)
- [ ] 1.3 Migración Flyway `dental_chart_finding`: `organization_id` NOT NULL + índice, FK al bloque, `location_kind` CHECK, `tooth_a`, `tooth_b`, `surface`, `type` CHECK contra el catálogo de 0.1, `grade` nullable, **`condition` nullable** (buen/mal estado — solo para tipos de restauración, `ADR-0013` D2), autoría · RLS propia igual que 1.1 (design.md D1/D2)
- [ ] 1.4 `CHECK` de coherencia de localización en `dental_chart_finding` (discriminador ↔ columnas pobladas), mismo patrón que `clinical_history_diagnosis_location_paired` (design.md D2)
- [ ] 1.5 Enums de dominio: tipo de condición con sus atributos **solo en código** (no en columnas — son derivables y se desincronizarían): `behavior` (patológico / descriptivo / restauración), `scope`, **`sigla` oficial de NTS 188**, `requiresGrade`, `requiresSpecification`. Más estado y tipo de bloque, forma de localización, superficie dental, y el estado de conservación (`ADR-0013` D2/D3, design.md D6)
- [ ] 1.6 Extender `FdiTooth` con las **superficies aplicables** por pieza (anterior vs. posterior: incisal vs. oclusal) — extender lo construido, no crear un catálogo nuevo (design.md D7)
- [ ] 1.7 Entidades JPA `DentalChartBlock` + `DentalChartFinding` con `@TenantId`, `@EntityListeners(AuditingEntityListener.class)` + `@CreatedBy`/`@LastModifiedBy` (exigido por el candado ArchUnit `business_entities_with_tenant_id_must_declare_authorship`), y `Location` como `@Embeddable` con discriminador
- [ ] 1.8 Test de aislamiento por clínica análogo a `ClinicalHistoryRlsIT` (SQL crudo, bypass de JPA, como rol de aplicación no-superusuario) para **ambas** tablas — la RLS no se hereda por FK (spec: *Aislamiento por clínica*)

## 2. Servicio de dominio: creación y hallazgos (backend)

- [ ] 2.1 Crear el odontograma inicial en estado abierto; rechazar si ya existe; unicidad por índice único parcial (no solo chequeo previo), tolerante a creación concurrente (spec: *Odontograma inicial único por paciente y clínica*)
- [ ] 2.2 `@ValidFindingLocation` — constraint Bean Validation a **nivel de objeto** sobre el DTO del hallazgo, mismo patrón que `@ValidDiagnosisLocation`: coherencia alcance↔forma de localización, FDI real, superficie aplicable a la pieza, grado exigido/prohibido según el tipo, y **estado de conservación exigido en los tipos de restauración y rechazado en los demás** (`ADR-0013` D2). Reporta sobre el campo correcto vía `addPropertyNode`, no una excepción genérica (design.md D7)
- [ ] 2.3 Normalizar el orden canónico en la localización entre piezas (`tooth_a < tooth_b`) al construir el value object, para que `(11,21)` y `(21,11)` sean la misma localización (design.md D2, spec: *El mismo espacio entre piezas no se duplica por el orden*)
- [ ] 2.4 Agregar / corregir / eliminar hallazgos **solo mientras el bloque esté abierto**; rechazar hallazgo de superficie sobre pieza marcada como ausente; rechazar duplicado exacto de tipo+localización
- [ ] 2.5 Registrar las especificaciones del odontograma (spec: *Especificaciones del odontograma*)
- [ ] 2.6 Aviso **no bloqueante** de incoherencia dentición↔edad: registra el hallazgo y devuelve la advertencia (INV-15 — una pieza temporal retenida en un adulto es rara pero real; bloquearla impediría registrar un caso legítimo)
- [ ] 2.7 Tests de servicio, uno por Scenario: crear, segundo rechazado, **creación concurrente con hilos reales** contra PostgreSQL (patrón de `ClinicalHistoryServiceIT`), hallazgo por superficie / pieza / entre piezas, pieza inexistente, superficie no aplicable, localización incoherente con el alcance, pieza ausente con superficie, duplicado por orden invertido, corrección con el bloque abierto, grado exigido/prohibido, **restauración en buen vs. mal estado (mismo tipo, comportamiento distinto)**, estado exigido/prohibido según el tipo, **sellante y caries coexistiendo en la misma pieza**, aviso por edad

## 3. Sellado coordinado con la admisión (backend) — **toca una capacidad construida**

- [ ] 3.1 Operación de sellado del odontograma en `dental-chart`: valida especificaciones registradas **y las entradas que exigen tipos concretos** (movilidad ⇒ clasificación · prótesis ⇒ material, `ADR-0013` D4), sella (`sealed_at`, `status=SEALED`), transición **unidireccional**; permite sellar sin hallazgos (una boca sana es un registro válido) (spec: *Odontograma sellado e inmutable*)
- [ ] 3.2 Rechazar **en el servidor** toda modificación de un odontograma sellado, aunque la petición llegue directa a la API — con `errorType` dedicado, mismo patrón que `HistoriaClinicaSealedException` (spec: *Rechazar la modificación de un odontograma sellado aun por API directa*)
- [ ] 3.3 **Caso de uso coordinador** `CloseAdmissionService` en `service/admission/`, `@Transactional`, que llama a `ClinicalHistoryService.complete(...)` y a la operación de 3.1. Ninguno de los dos módulos se conoce entre sí; el coordinador depende de ambos (design.md D4)
- [ ] 3.4 Agregar el odontograma al checklist de sellado de `clinical-history` y hacer que el endpoint existente `POST /patients/{id}/clinical-history/complete` delegue en el coordinador — **conserva la ruta**, cambia la semántica (design.md D4/D5)
- [ ] 3.5 **Actualizar los tests existentes de `clinical-history` que sellan sin odontograma**: van a fallar por diseño. Que fallen es la señal de que el checklist se aplica; el riesgo sería no verlos fallar
- [ ] 3.6 Tests del sellado coordinado: sella ambos en una operación · rechaza sin odontograma indicando qué falta · **un fallo al sellar el odontograma deja la Historia en `DRAFT`** (rollback verificado, no asumido) · Historias selladas antes de la regla no se ven afectadas · transición inválida rechazada

## 4. Contrato y API (backend)

- [ ] 4.1 DTOs de request/response (crear odontograma, agregar/eliminar hallazgo, especificaciones, consultar) + controller de la capacidad
- [ ] 4.2 El aviso de dentición↔edad viaja en la respuesta como dato, no como error (es no bloqueante) — decidir su forma en el contrato, no inventarla en el cliente
- [ ] 4.3 Regenerar `contracts/openapi.json` + cliente orval (**commit aparte**); `ContractDriftIT` en verde

## 5. Frontend

- [ ] 5.1 Hooks React Query sobre el cliente generado (crear, consultar, agregar/eliminar hallazgo, especificaciones) (`docs/frontend.md`)
- [ ] 5.2 Schemas Zod con **paridad** a Bean Validation: alcance↔localización, FDI real, superficie aplicable, grado condicional (`docs/coding-style.md §7`). Espejar el catálogo de tipos desde el contrato, igual que `VALID_FDI_TEETH` espeja `FdiTooth`
- [ ] 5.3 Odontograma **SVG inline** en `modules/dental-chart/`: cada pieza y superficie **enfocable y accionable por teclado**, con nombre accesible — no canvas ni imagen (design.md D8)
- [ ] 5.4 ⚠️ Colores **normativos, NO del tema**: rojo `#FF0000` y azul `#0000FF` exactos, sobre **fondo blanco siempre**, aunque la app esté en modo oscuro — es un documento legal y lo mostrado debe ser idéntico a lo auditado (`ADR-0013` D5). **Única excepción a la regla de `frontend-guard`**: dejarlo comentado en el código, porque a simple vista parece la violación que esa regla persigue. El color deriva del `behavior` del tipo y —en restauraciones— de su estado; la **regla de contagio** se calcula al renderizar. El color **nunca** es el único portador de significado: la sigla oficial y la forma también lo comunican
- [ ] 5.5 Dentición según la edad del paciente (temporal / mixta / permanente), derivada, con las piezas de ambas denticiones coexistiendo cuando corresponde
- [ ] 5.6 Odontograma sellado en **modo lectura**; el aviso de dentición↔edad se muestra sin bloquear
- [ ] 5.6b La pantalla de **Historia Clínica** refleja el requisito nuevo: muestra que falta el odontograma y enlaza a él. La ruta de `complete` no cambia, pero el flujo sí — sin esto el odontólogo choca con un rechazo sin entender por qué (design.md D4)
- [ ] 5.7 Tests Vitest/MSW: registrar hallazgo por superficie · rechazar superficie no aplicable · hallazgo entre piezas · odontograma sellado en solo lectura · aviso no bloqueante por edad · navegación por teclado sobre las piezas · **restauración en mal estado se pinta en rojo y en buen estado en azul** · el odontograma conserva fondo blanco con la app en modo oscuro

## 6. Gobernanza y cierre

- [ ] 6.1 Al archivar: reemplazar el stub `Tooth` + `ToothCondition` de `docs/domain.md` por el modelo real (bloque, hallazgo, localización, catálogo) y actualizar el ER
- [ ] 6.2 Al archivar: completar en `docs/compliance/regulatory-matrix.md` las filas del odontograma — de `pendiente` al requirement/scenario concreto, y su estado a ✅
- [ ] 6.3 Al archivar: marcar `dental-chart` como construida y enlazar su `spec.md` en `docs/vision.md`; anotar que la fase 2 (evolución) va junto con `update-clinical-history`
- [ ] 6.4 `mvn verify` (back) y `pnpm validate` (front) en verde; `openspec validate add-dental-chart --strict`
