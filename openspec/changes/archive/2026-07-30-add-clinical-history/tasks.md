## 1. Catálogo CIE10 (base para el diagnóstico)

> Numeración de migración: la última en `dev` es `V12__user_login_lockout.sql`. Este change usa `V13__cie10_catalog.sql` (1.1), `V14__historia_clinica.sql` (2.1), `V15__clinical_history_diagnosis.sql` (2.2) — verificar el número real contra `dev` al momento de escribirlas, por si otro change se adelantó.

- [x] 1.1 Migración Flyway `cie10_code` (`code` PK, `descripcion`, `nivel` CHECK `CAPITULO|GRUPO|CATEGORIA|SUBCATEGORIA`, `parent_code` FK autorreferenciada nullable) con índice en `parent_code` y en `descripcion` (búsqueda). **Sin** `organization_id`/RLS: catálogo global (D6)
- [x] 1.2 Loader idempotente que puebla `cie10_code` desde `resources/data/cie10-minsa.json` al arranque solo si la tabla está vacía; genera códigos **sintéticos deterministas** para `chapter`/`group` (`CAP01`, `GRP0101`...) ya que el JSON no los trae; inserción por **lote JDBC**, **transaccional**, valida `count` final esperado para detectar carga incompleta (D6)
- [x] 1.3 Repositorio + servicio de búsqueda CIE10 que devuelve resultados con su ruta jerárquica (capítulo→grupo→categoría→subcategoría) navegando `parent_code`, no plana (spec: búsqueda jerárquica)
- [x] 1.4 Endpoint de búsqueda CIE10 (autenticado) + slice test que verifica jerarquía y validación de existencia de código
- [x] 1.5 Tests del loader: arranque con tabla vacía puebla completo; segundo arranque no duplica; carga parcial simulada se completa o se rechaza explícitamente (D6)

## 2. Modelo y persistencia de la Historia Clínica (backend)

- [x] 2.1 Migración Flyway `historia_clinica` (`organization_id` NOT NULL + índice, `patient_id` UNIQUE, `status` con CHECK DRAFT/COMPLETED, campos narrativos, `signos_vitales` JSONB, flags `_revisado`+`_detalle` de D3, `created_by`/`updated_by`, timestamps) + `ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `organization_isolation` (mismo patrón que `V9__patients.sql`) (D1, D2, D2b, D3)
- [x] 2.2 Migración Flyway `clinical_history_diagnosis` (`organization_id` NOT NULL + índice, `historia_id` FK, `cie10_code`, `ubicacion` CHECK GENERAL/PIEZA, `fdi_number` nullable, `tipo_diagnostico` CHECK P/D/R, `created_at`/`created_by` inmutables, `updated_by`) + RLS igual que 2.1 (D2, D2b, D5, D11)
- [x] 2.3 Entidades JPA `HistoriaClinica` + `ClinicalHistoryDiagnosis`: `@TenantId`, `@EntityListeners(AuditingEntityListener.class)` + `@CreatedBy`/`@LastModifiedBy` (exigido por el candado ArchUnit `business_entities_with_tenant_id_must_declare_authorship`), enums de estado y ubicación (D1, D2b, D5)
- [x] 2.4 Test de aislamiento por clínica análogo a `PatientRlsIT` (SQL crudo, bypass de JPA) para `historia_clinica` y `clinical_history_diagnosis` (D2b, spec: aislamiento por clínica)

## 3. Servicio de dominio: creación y guardado en DRAFT (backend)

- [x] 3.1 Crear Historia Clínica en `DRAFT` para un paciente sin historia; rechazar si ya existe una; unicidad garantizada por `UNIQUE(patient_id)` (no solo chequeo previo), tolerante a creación concurrente (D9/spec: única por paciente/clínica, concurrencia)
- [x] 3.2 Guardado incremental en `DRAFT` (secciones incompletas permitidas) y edición de un `DRAFT` existente; **sin leer/copiar/modificar** la anamnesis de `patients` (D9, spec: la Historia no altera datos clínicos del paciente)
- [x] 3.3 Rechazar edición directa de información inicial cuando `status = COMPLETED`, con `errorType` dedicado, **server-side** aunque la petición llegue directa a la API (D1, spec: sellada tras COMPLETED)
- [x] 3.4 Tests de servicio para 3.1–3.3 (uno por Scenario: crear, segunda rechazada, creación concurrente, no toca patients, guardar incompleto, retomar, rechazar edición sellada por API directa)

## 4. Diagnósticos CIE10 en la Historia (backend)

- [x] 4.1 Agregar diagnóstico con código válido + ubicación (`GENERAL`/`PIEZA`) + **tipo P/D/R obligatorio**; validar existencia del código contra `cie10_code`; capturar `created_at`/`created_by` por auditoría; permitir agregar/modificar/eliminar **solo en `DRAFT`** (D5, D11, spec: diagnóstico CIE10)
- [x] 4.2 `@ValidDiagnosisLocation` — constraint Bean Validation a **nivel de objeto** sobre el DTO de diagnóstico, mismo patrón que `GuardianRequiredForMinor`/`EmergencyContactRequiredWithoutPhone` (`@Constraint` + `ConstraintValidator` + `addPropertyNode`): `PIEZA ⇒ fdi_number` obligatorio y dentro del **conjunto válido** de piezas (no rango laxo); `GENERAL ⇒` rechaza `fdi_number`. Reporta el error sobre `fdiNumber` o `ubicacion` según el caso, no una excepción genérica (D5, spec: rechazar PIEZA sin FDI / FDI fuera de conjunto / FDI en general)
- [x] 4.3 Tests de servicio: general OK (con tipo + auditoría capturada), pieza+FDI OK, código inexistente rechazado, PIEZA sin FDI rechazado, FDI fuera del conjunto (`19`/`20`/`99`) rechazado, GENERAL con FDI rechazado, **diagnóstico sin tipo P/D/R rechazado**, cambio de diagnóstico en `COMPLETED` rechazado

## 5. Sellado a COMPLETED y estado de habilitación (backend)

- [x] 5.1 `ChecklistIncompleteException` (extiende `BusinessException`, lleva `List<String> missingFields`) + `@ExceptionHandler` **dedicado** en `GlobalExceptionHandler` que agrega `problem.setProperty("missingFields", ...)` — mismo patrón que `existingPatientId` en `update-patients` D4, no pasa por `handleBusiness` (D4)
- [x] 5.2 Validación del checklist mínimo de D4 en la transición `DRAFT→COMPLETED`, con los flags afirmativos uniformes (alergias/medicación/antecedentes familiares); lanza `ChecklistIncompleteException` con los campos faltantes si no se cumple (spec: sellado con checklist)
- [x] 5.3 Transición **unidireccional**: sella la historia (`completed_at`, `status=COMPLETED`) cuando el checklist se cumple; rechaza `COMPLETED→DRAFT` y el re-sellado (D1, spec: completar OK / transición inválida)
- [x] 5.4 Endpoint de estado de habilitación por paciente: `{ status, enabledForTreatment }` (`true` solo si COMPLETED) (D8, spec: estado consultable)
- [x] 5.5 Contrato: `clinical-history-incomplete` documenta la extensión `missingFields` en `ApiProblem` (opcional, solo en este `errorType`), mismo patrón que `existingPatientId`. Regenerar `contracts/openapi.json` + cliente
- [x] 5.6 Tests de servicio/IT: checklist satisfecho sella; ítem crítico faltante rechaza con `missingFields` correcto; alergias vacías no cuentan como "sin hallazgos"; medicación vacía no cuenta como "no toma medicación"; transición inválida rechazada; habilitación COMPLETED vs DRAFT vs sin historia

## 6. Filiación y contrato (backend)

- [x] 6.1 Mapper que compone la filiación leyéndola de `Patient` (no columnas propias); reflejada aun en `COMPLETED` (D7, spec: filiación derivada)
- [x] 6.2 DTOs de request/response (crear/editar DRAFT, agregar diagnóstico, completar, estado) + controller de la capacidad
- [x] 6.3 Actualizar `contracts/openapi.json` y regenerar cliente orval (commit aparte); `ContractDriftIT` en verde

## 7. Frontend

- [x] 7.1 Hooks React Query (crear/editar DRAFT, agregar diagnóstico, completar, consultar estado) sobre el cliente generado (`docs/frontend.md`)
- [x] 7.2 Schemas Zod con paridad a Bean Validation: flags afirmativos de D3, regla condicional `PIEZA ⇒ FDI` (y `GENERAL` sin FDI), **tipo P/D/R obligatorio** por diagnóstico (`docs/coding-style.md §7`)
- [x] 7.3 Pantalla/feature de edición de la Historia en `DRAFT` (guardado incremental, secciones), en `modules/clinical-history/`
- [x] 7.4 Buscador de diagnóstico CIE10 que navega la jerarquía (no lista plana); al agregar, capturar ubicación (`GENERAL`/`PIEZA`+FDI) y tipo P/D/R; muestra la descripción oficial del código (solo lectura)
- [x] 7.5 Diálogo de confirmación del sello (`DRAFT→COMPLETED`) que avisa que la información queda fija; indicador visible de estado `DRAFT`/`COMPLETED`
- [x] 7.6 Tests Vitest/MSW: guardado incremental, checklist bloquea el sello, historia sellada en modo lectura, estado de habilitación

## 8. Gobernanza y cierre

- [ ] 8.1 Actualizar `docs/domain.md`: entidad `HistoriaClinica` + `ClinicalHistoryDiagnosis`, su ER y la relación con `Patient`/CIE10
- [ ] 8.2 Al archivar: marcar `clinical-history` como construida y enlazar su `spec.md` en `docs/vision.md`
- [x] 8.3 `mvn verify` (back) y `pnpm validate` (front) en verde; `openspec validate add-clinical-history --strict`
