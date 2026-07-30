## 0. Antes de escribir la migración (bloquea al grupo 1)

- [ ] 0.1 **Revisar el catálogo de tipos de condición con el odontólogo** y fijarlo: naturaleza (requiere intervención / sin patología), alcance (superficie / pieza / entre piezas) y cuáles exigen grado. Contrastar contra **NTS 150 y sus anexos gráficos**, el Colegio Odontológico y la práctica clínica peruana. La lista de `ADR-0012` es **tentativa**; agregar un tipo después de la migración cuesta migración de datos (design.md, Open Question 1)
- [ ] 0.2 **Decidir si el Índice de Higiene Oral (IHO) es obligatorio** en el examen inicial. Si lo es, **no va como tipo de condición**: es un índice numérico calculado, con su propio método de medición — sería un **requirement adicional** de esta capacidad y hay que agregarlo al spec antes de implementar (design.md, Open Question 2)
- [ ] 0.3 **Decidir dónde vive el registro periodontal** (sondaje, furca): capacidad propia, `update-dental-chart`, o texto en especificaciones. No entra en este change, pero la decisión se toma ahora para que después no se meta a presión en el modelo equivocado (design.md, Open Question 3)
- [ ] 0.4 Completar la columna **§ (artículo/numeral)** de `docs/compliance/regulatory-matrix.md` con los textos de las normas a la vista — hoy está vacía a propósito, porque inventar números en un documento de cumplimiento induciría a error a un auditor
- [ ] 0.5 Verificar el número real de la última migración en `dev` (hoy `V15__clinical_history_diagnosis.sql`) por si otro change se adelantó

## 1. Modelo y persistencia (backend)

- [ ] 1.1 Migración Flyway `dental_chart_block`: `organization_id` NOT NULL + índice, `patient_id`, `type` CHECK `INITIAL`, `status` CHECK `OPEN|SEALED`, `examined_on`, `sealed_at`, `specifications`, autoría + timestamps · `ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `organization_isolation` (patrón de `V14__historia_clinica.sql`) (design.md D1)
- [ ] 1.2 Índices **únicos parciales** en la misma migración: uno por `patient_id WHERE status='OPEN'`, otro por `patient_id WHERE type='INITIAL'` — las invariantes de unicidad las garantiza la base, no un chequeo previo (design.md D3)
- [ ] 1.3 Migración Flyway `dental_chart_finding`: `organization_id` NOT NULL + índice, FK al bloque, `location_kind` CHECK, `tooth_a`, `tooth_b`, `surface`, `type` CHECK contra el catálogo de 0.1, `grade` nullable, autoría · RLS propia igual que 1.1 (design.md D1/D2)
- [ ] 1.4 `CHECK` de coherencia de localización en `dental_chart_finding` (discriminador ↔ columnas pobladas), mismo patrón que `clinical_history_diagnosis_location_paired` (design.md D2)
- [ ] 1.5 Enums de dominio: tipo de condición con sus atributos `nature`/`scope`/`requiresGrade` **solo en código** (no en columnas — son derivables y se desincronizarían), estado y tipo de bloque, forma de localización, superficie dental (design.md D6)
- [ ] 1.6 Extender `FdiTooth` con las **superficies aplicables** por pieza (anterior vs. posterior: incisal vs. oclusal) — extender lo construido, no crear un catálogo nuevo (design.md D7)
- [ ] 1.7 Entidades JPA `DentalChartBlock` + `DentalChartFinding` con `@TenantId`, `@EntityListeners(AuditingEntityListener.class)` + `@CreatedBy`/`@LastModifiedBy` (exigido por el candado ArchUnit `business_entities_with_tenant_id_must_declare_authorship`), y `Location` como `@Embeddable` con discriminador
- [ ] 1.8 Test de aislamiento por clínica análogo a `ClinicalHistoryRlsIT` (SQL crudo, bypass de JPA, como rol de aplicación no-superusuario) para **ambas** tablas — la RLS no se hereda por FK (spec: *Aislamiento por clínica*)

## 2. Servicio de dominio: creación y hallazgos (backend)

- [ ] 2.1 Crear el odontograma inicial en estado abierto; rechazar si ya existe; unicidad por índice único parcial (no solo chequeo previo), tolerante a creación concurrente (spec: *Odontograma inicial único por paciente y clínica*)
- [ ] 2.2 `@ValidFindingLocation` — constraint Bean Validation a **nivel de objeto** sobre el DTO del hallazgo, mismo patrón que `@ValidDiagnosisLocation`: coherencia alcance↔forma de localización, FDI real, superficie aplicable a la pieza, grado exigido/prohibido según el tipo. Reporta sobre el campo correcto vía `addPropertyNode`, no una excepción genérica (design.md D7)
- [ ] 2.3 Normalizar el orden canónico en la localización entre piezas (`tooth_a < tooth_b`) al construir el value object, para que `(11,21)` y `(21,11)` sean la misma localización (design.md D2, spec: *El mismo espacio entre piezas no se duplica por el orden*)
- [ ] 2.4 Agregar / corregir / eliminar hallazgos **solo mientras el bloque esté abierto**; rechazar hallazgo de superficie sobre pieza marcada como ausente; rechazar duplicado exacto de tipo+localización
- [ ] 2.5 Registrar las especificaciones del odontograma (spec: *Especificaciones del odontograma*)
- [ ] 2.6 Aviso **no bloqueante** de incoherencia dentición↔edad: registra el hallazgo y devuelve la advertencia (INV-15 — una pieza temporal retenida en un adulto es rara pero real; bloquearla impediría registrar un caso legítimo)
- [ ] 2.7 Tests de servicio, uno por Scenario: crear, segundo rechazado, **creación concurrente con hilos reales** contra PostgreSQL (patrón de `ClinicalHistoryServiceIT`), hallazgo por superficie / pieza / entre piezas, pieza inexistente, superficie no aplicable, localización incoherente con el alcance, pieza ausente con superficie, duplicado por orden invertido, corrección con el bloque abierto, grado exigido/prohibido, aviso por edad

## 3. Sellado coordinado con la admisión (backend) — **toca una capacidad construida**

- [ ] 3.1 Operación de sellado del odontograma en `dental-chart`: valida especificaciones registradas, sella (`sealed_at`, `status=SEALED`), transición **unidireccional**; permite sellar sin hallazgos (una boca sana es un registro válido) (spec: *Odontograma sellado e inmutable*)
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
- [ ] 5.4 Color derivado de la naturaleza del tipo **vía tokens semánticos del tema**, nunca hexadecimales (el tema se deriva de la marca de cada clínica — `frontend-guard`). Implementar la **regla de contagio**: una pieza con algún hallazgo que requiere intervención pinta todos sus marcadores en rojo (`ADR-0012` D2, se calcula al renderizar, no se almacena)
- [ ] 5.5 Dentición según la edad del paciente (temporal / mixta / permanente), derivada, con las piezas de ambas denticiones coexistiendo cuando corresponde
- [ ] 5.6 Odontograma sellado en **modo lectura**; el aviso de dentición↔edad se muestra sin bloquear
- [ ] 5.7 Tests Vitest/MSW: registrar hallazgo por superficie · rechazar superficie no aplicable · hallazgo entre piezas · odontograma sellado en solo lectura · aviso no bloqueante por edad · navegación por teclado sobre las piezas

## 6. Gobernanza y cierre

- [ ] 6.1 Al archivar: reemplazar el stub `Tooth` + `ToothCondition` de `docs/domain.md` por el modelo real (bloque, hallazgo, localización, catálogo) y actualizar el ER
- [ ] 6.2 Al archivar: completar en `docs/compliance/regulatory-matrix.md` las filas del odontograma — de `pendiente` al requirement/scenario concreto, y su estado a ✅
- [ ] 6.3 Al archivar: marcar `dental-chart` como construida y enlazar su `spec.md` en `docs/vision.md`; anotar que la fase 2 (evolución) va junto con `update-clinical-history`
- [ ] 6.4 `mvn verify` (back) y `pnpm validate` (front) en verde; `openspec validate add-dental-chart --strict`
