> Solo backend. Sin cambios de API, contrato ni frontend. Depende de `add-dental-chart` (PR #68): trae `HistoriaClinicaRepository.lockByPatientId`.

## 1. Tests primero (deben fallar contra el código actual)

- [ ] 1.1 `ClinicalHistoryConcurrencyIT`: guardar la Historia mientras otra transacción la sella → rechazado (`HistoriaClinicaSealedException`) y la Historia queda `COMPLETED` (scenario *Guardar la Historia mientras otra operación la sella no la reabre*). Determinista: señal por `pg_locks`, como `CloseAdmissionServiceIT` 2.8f
- [ ] 1.2 `ClinicalHistoryConcurrencyIT`: agregar un diagnóstico mientras otra transacción sella → rechazado y la Historia sellada no tiene diagnósticos posteriores (scenario *Un diagnóstico no entra en una Historia que se está sellando*)
- [ ] 1.3 Correr 1.1 y 1.2 contra el código actual y registrar que fallan (prueba de que detectan la carrera)

## 2. Fix (design D1)

- [ ] 2.1 `ClinicalHistoryService.lockDraft(...)`: `lockByPatientId` + `requireDraft`, en ese orden
- [ ] 2.2 `ClinicalHistoryService.update` usa `lockDraft`
- [ ] 2.3 `ClinicalHistoryDiagnosisService.add/update/remove` usan `lockDraft`
- [ ] 2.4 1.1 y 1.2 pasan; los IT existentes de `clinical-history` siguen verdes

## 3. Cierre

- [ ] 3.1 `./mvnw -B verify` verde con `Skipped: 0`
- [ ] 3.2 `docs/backend.md`: regla "bloquear antes de comprobar el estado" para toda escritura de un documento sellable (Historia, odontograma)
- [ ] 3.3 `openspec validate update-clinical-history --strict`
