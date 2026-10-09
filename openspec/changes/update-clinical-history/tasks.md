> Fase 2, corte A: nota de consulta. Backend y frontend en commits separados por scope. Requiere Docker (Testcontainers) para que los IT corran con `Skipped: 0`.

## 1. Documentación normativa (antes del código)

- [x] 1.1 `docs/adr/0020-nota-de-consulta.md`: D1–D7 del design con su fundamento en el texto oficial (NTS 139 §4.2.9, §4.2.10, §5.2.1 (1) 1.1 d y 1.2, §5.2.1 (3), §5.2.2 (12), §5.3.3 j; DS 009-2017-SA arts. 46 y 47; RM 265-2018), las cinco dimensiones de `docs/adr/README.md` y las alternativas descartadas (SOAP, cierre diario, tabla única de diagnósticos)
- [x] 1.2 Normas subidas (design D12): renombrar y registrar en `docs/compliance/normas/README.md` DS 009-2017-SA, RM 576-2011 (solo la resolución, sin la Directiva 183), Ley 27269 y RM 265-2018; **preguntar al usuario** si se versionan NTS 139 y Código del COP; eliminar la copia duplicada de la RM 265 en `docs/compliance/`
- [x] 1.3 Línea de corrección en la cabecera de `ADR-0017` (D3) y `ADR-0018` (D2, D3) apuntando a `ADR-0020` (design D11)
- [x] 1.4 `docs/compliance/regulatory-matrix.md`: reescribir las filas "Evolución estructurada en SOAP" y "Enmienda como asiento nuevo vinculado"; filas nuevas para contenido de la nota, diagnóstico R, corrección art. 47 c, antecedentes nuevos (§4.2.10, art. 46); corregir la fila "Odontograma inicial y final" (solo 12.1) y "Cierre transaccional… del día"; agregar DS 009-2017-SA y RM 265-2018 a "Normas de referencia"

## 2. Backend — esquema

- [x] 2.1 `V21__consultation_note.sql`: `consultation_note`, `consultation_note_diagnosis`, `consultation_note_correction`, `clinical_background_entry` con `organization_id`, RLS forzada, policy `organization_isolation`, FK compuestas por tenant, CHECK de coherencia sello (`status` ↔ `sealed_at`/`sealed_by`), CHECK ubicación/FDI emparejados, CHECK alta/baja (`description` y `closes_entry_id`), índices únicos parciales (una `ABIERTA` por paciente; una baja por alta)
- [x] 2.2 `V22__consultation_note_seal_guard.sql`: triggers SQLSTATE `55000` — nota `SELLADA` no se modifica ni se borra; diagnósticos y antecedentes de nota sellada no se insertan, modifican ni borran; `consultation_note_correction` sin `UPDATE`/`DELETE` nunca
- [x] 2.3 Entidades, enums (`ConsultationNoteStatus`, `NoteTextField`, `BackgroundCategory`, `BackgroundEntryKind`) y repositorios, incluido `lockById` (`FOR UPDATE`)
- [x] 2.4 `ConsultationNoteSealGuardIT`: cada trigger rechaza la escritura directa por SQL sobre una nota sellada; la corrección rechaza `UPDATE`/`DELETE`
- [x] 2.5 Agregar las tablas nuevas a los tests de aislamiento por tenant existentes (RLS) y a la regla ArchUnit de entidades con `@TenantId`

## 3. Backend — nota de consulta

- [x] 3.1 `ConsultationNoteService.open/get/list/update/discard/seal` con `lockOpenNote` (bloquear y después comprobar `ABIERTA`, `docs/backend.md` §8.3.1); gate Historia `COMPLETED`; conflicto por índice parcial traducido a `409`
- [x] 3.2 Validación de `attended_at` (≤ ahora, ≥ última sellada) al abrir, editar y sellar; próxima cita posterior a la fecha de atención
- [x] 3.3 Checklist de sellado (motivo, examen, tratamiento, ≥ 1 diagnóstico) que devuelve los faltantes
- [x] 3.4 Rechazar abrir una nota si el paciente está dado de baja (scenario *Rechazar la nota de un paciente dado de baja*)
- [x] 3.5 `ConsultationNoteServiceIT` — scenarios de *Nota de consulta posterior a la admisión*, *Contenido mínimo*, *Fecha y hora de la atención*, *Nota abierta editable y descartable solo vacía*, *Sellado irreversible de la nota* (uno por WHEN/THEN)
- [x] 3.6 `ConsultationNoteConcurrencyIT` — apertura concurrente (una sola nota) y escritura durante el sellado (rechazada, contenido intacto), con orden forzado por `pg_locks` como `ClinicalHistoryConcurrencyIT`

## 4. Backend — diagnósticos, corrección y antecedentes

- [x] 4.1 `ConsultationNoteDiagnosisService.add/update/remove` reutilizando `DiagnosisRequest`/`ValidDiagnosisLocation`/catálogo CIE10, con la regla R (design D5)
- [x] 4.2 `ConsultationNoteCorrectionService.add`: solo nota sellada, solo `sealed_by`, campo no vacío en el original, motivo obligatorio
- [x] 4.3 `ClinicalBackgroundService`: alta/baja dentro de nota abierta (baja solo de alta vigente del mismo paciente y categoría) y vista de vigentes con los tres orígenes (design D8)
- [x] 4.4 ITs por scenario: *Diagnósticos de la nota de consulta*, *Diagnóstico repetitivo exige un registro previo*, *Corrección de un error material como registro nuevo*, *Antecedentes nuevos como asientos de alta y baja*, *Antecedentes vigentes del paciente*

## 5. Backend — API y `patients`

- [x] 5.1 `ConsultationNoteController` bajo `/patients/{patientId}/consultation-notes` (CRUD de nota, `/{noteId}/seal`, `/{noteId}/diagnoses`, `/{noteId}/background-entries`, `/{noteId}/corrections`) y `GET /patients/{patientId}/clinical-background`; DTOs con Bean Validation; errores RFC 9457 con `detail` en español
- [x] 5.2 `ConsultationNoteControllerIT`: status reales, `404` para otra clínica, `409` en nota sellada aun por API directa
- [x] 5.3 `PatientService.update`: rechazar cambios de anamnesis si existe Historia (design D10); IT por cada scenario de *La anamnesis de la ficha se congela al existir la Historia Clínica*; `PatientResponse` expone si la anamnesis está congelada
- [x] 5.4 Regenerar el contrato OpenAPI y verificar `ContractDriftIT`
- [x] 5.5 `./mvnw -B verify` verde con `Skipped: 0`

## 6. Frontend

- [x] 6.1 `pnpm` codegen orval; esquemas Zod de nota, diagnóstico de nota, corrección y asiento con paridad Bean Validation (`docs/coding-style.md` §7), incluida la regla de próxima cita
- [x] 6.2 Hooks React Query de notas, diagnósticos, correcciones, antecedentes y vigentes (invalidaciones al sellar)
- [x] 6.3 Pantalla de notas de consulta del paciente: lista cronológica, nota abierta arriba con acciones sellar/descartar, editor por campos con checklist de faltantes, lectura de la sellada con sus correcciones bajo el campo corregido; ruta junto a la de la Historia
- [x] 6.4 Diálogo de corrección (campo, texto, motivo), visible solo para quien selló y solo sobre campos no vacíos
- [x] 6.5 Panel de antecedentes: altas/bajas dentro de la nota abierta y vista de vigentes con origen y fecha (`FICHA` con su etiqueta)
- [x] 6.6 `PatientForm`/`PatientDetail`: anamnesis en solo lectura con aviso cuando está congelada
- [x] 6.7 Tests Vitest/MSW por pantalla y componente (abrir, guardar, sellar con faltantes, descartar vacía, corrección, vigentes, anamnesis congelada)
- [x] 6.8 `pnpm validate` verde

## 7. Cierre

- [x] 7.1 `openspec validate update-clinical-history --strict`
- [ ] 7.2 Revisión externa (Codex) antes del PR
- [ ] 7.3 Al archivar: Purpose de `clinical-history` con el texto de design (Open Questions), `docs/domain.md` (nota de consulta, asiento de antecedente, corrección; glosario y ER), `docs/vision.md` marcada como construida, matriz con los tests reales
