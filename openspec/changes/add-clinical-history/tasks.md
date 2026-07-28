## 1. Catálogo CIE10 (base para el diagnóstico)

- [ ] 1.1 Migración Flyway `cie10_code` (`code` PK, `descripcion`, `nivel`, `parent_code` nullable) con índice en `parent_code` y en `descripcion` (búsqueda) (D6)
- [ ] 1.2 Loader idempotente que puebla `cie10_code` desde `resources/data/cie10-minsa.json` al arranque solo si la tabla está vacía (D6)
- [ ] 1.3 Repositorio + servicio de búsqueda CIE10 que devuelve resultados con su ruta jerárquica (capítulo→grupo→categoría→subcategoría), no plana (spec: búsqueda jerárquica)
- [ ] 1.4 Endpoint de búsqueda CIE10 (autenticado) + slice test que verifica jerarquía y validación de existencia de código

## 2. Modelo y persistencia de la Historia Clínica (backend)

- [ ] 2.1 Migración Flyway `historia_clinica` (`patient_id` UNIQUE, `status` con CHECK DRAFT/COMPLETED, campos narrativos, `signos_vitales` JSONB, flags `_revisado`+`_detalle` de D3, timestamps) (D1, D2, D3)
- [ ] 2.2 Migración Flyway `clinical_history_diagnosis` (`historia_id` FK, `cie10_code`, `ubicacion` CHECK GENERAL/REGION/PIEZA, `fdi_number` nullable) (D2, D5)
- [ ] 2.3 Entidades JPA `HistoriaClinica` + `ClinicalHistoryDiagnosis` + enums de estado y ubicación (D1, D5)
- [ ] 2.4 Confirmar RLS/aislamiento por tenant sobre las tablas nuevas (patrón `multi-tenancy`); slice test de aislamiento por clínica (spec: aislamiento por clínica)

## 3. Servicio de dominio: creación y guardado en DRAFT (backend)

- [ ] 3.1 Crear Historia Clínica en `DRAFT` para un paciente sin historia; rechazar si ya existe una (spec: única por paciente/clínica)
- [ ] 3.2 Guardado incremental en `DRAFT` (secciones incompletas permitidas) y edición de un `DRAFT` existente (spec: guardado incremental)
- [ ] 3.3 Rechazar edición directa de información inicial cuando `status = COMPLETED`, con `errorType` dedicado (D1, spec: sellada tras COMPLETED)
- [ ] 3.4 Tests de servicio para 3.1–3.3 (uno por Scenario: crear, segunda rechazada, guardar incompleto, retomar, rechazar edición sellada)

## 4. Diagnósticos CIE10 en la Historia (backend)

- [ ] 4.1 Agregar diagnóstico con código válido + ubicación; validar existencia del código contra `cie10_code` (spec: diagnóstico CIE10)
- [ ] 4.2 Validación condicional `PIEZA ⇒ fdi_number` obligatorio y rango FDI válido (D5, spec: rechazar PIEZA sin FDI)
- [ ] 4.3 Tests de servicio: general OK, pieza+FDI OK, código inexistente rechazado, PIEZA sin FDI rechazado

## 5. Sellado a COMPLETED y estado de habilitación (backend)

- [ ] 5.1 Validación del checklist mínimo de D4 en la transición `DRAFT→COMPLETED`; 400 con detalle de ítems faltantes (spec: sellado con checklist)
- [ ] 5.2 Transición sella la historia (`completed_at`, `status=COMPLETED`) cuando el checklist se cumple (spec: completar OK)
- [ ] 5.3 Endpoint de estado de habilitación por paciente: `{ status, enabledForTreatment }` (`true` solo si COMPLETED) (D8, spec: estado consultable)
- [ ] 5.4 Tests de servicio/IT: checklist satisfecho sella; ítem crítico faltante rechaza; campo vacío no cuenta como "sin hallazgos"; habilitación COMPLETED vs DRAFT vs sin historia

## 6. Filiación y contrato (backend)

- [ ] 6.1 Mapper que compone la filiación leyéndola de `Patient` (no columnas propias); reflejada aun en `COMPLETED` (D7, spec: filiación derivada)
- [ ] 6.2 DTOs de request/response (crear/editar DRAFT, agregar diagnóstico, completar, estado) + controller de la capacidad
- [ ] 6.3 Actualizar `contracts/openapi.json` y regenerar cliente orval (commit aparte); `ContractDriftIT` en verde

## 7. Frontend

- [ ] 7.1 Hooks React Query (crear/editar DRAFT, agregar diagnóstico, completar, consultar estado) sobre el cliente generado (`docs/frontend.md`)
- [ ] 7.2 Schemas Zod con paridad a Bean Validation, incluidos los flags afirmativos de D3 y la regla condicional `PIEZA ⇒ FDI` (`docs/coding-style.md §7`)
- [ ] 7.3 Pantalla/feature de edición de la Historia en `DRAFT` (guardado incremental, secciones), en `modules/clinical-history/`
- [ ] 7.4 Buscador de diagnóstico CIE10 que navega la jerarquía (no lista plana)
- [ ] 7.5 Diálogo de confirmación del sello (`DRAFT→COMPLETED`) que avisa que la información queda fija; indicador visible de estado `DRAFT`/`COMPLETED`
- [ ] 7.6 Tests Vitest/MSW: guardado incremental, checklist bloquea el sello, historia sellada en modo lectura, estado de habilitación

## 8. Gobernanza y cierre

- [ ] 8.1 Actualizar `docs/domain.md`: entidad `HistoriaClinica` + `ClinicalHistoryDiagnosis`, su ER y la relación con `Patient`/CIE10
- [ ] 8.2 Al archivar: marcar `clinical-history` como construida y enlazar su `spec.md` en `docs/vision.md`
- [ ] 8.3 `mvn verify` (back) y `pnpm validate` (front) en verde; `openspec validate add-clinical-history --strict`
