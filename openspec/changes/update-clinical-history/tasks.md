> Solo backend. Sin cambios de esquema OpenAPI ni frontend. Depende de `add-dental-chart` (PR #68): trae `HistoriaClinicaRepository.lockByPatientId`.

## 1. Tests primero (los de sellado vs. escritura deben fallar contra el código actual)

`ClinicalHistoryConcurrencyIT`, orden forzado (design D3): A sella y ya tiene el bloqueo antes de liberar a B; A confirma cuando B queda esperando (`pg_locks`).

- [ ] 1.1 Guardar la Historia mientras otra transacción la sella → `HistoriaClinicaSealedException` y la Historia queda `COMPLETED` (scenario *Guardar la Historia mientras otra operación la sella no la reabre*)
- [ ] 1.2 Agregar un diagnóstico durante el sellado → rechazado y la Historia sellada conserva sus diagnósticos (scenario *Los diagnósticos de una Historia que se está sellando no cambian*)
- [ ] 1.3 Editar un diagnóstico durante el sellado → rechazado, el diagnóstico queda como estaba (mismo scenario)
- [ ] 1.4 Eliminar un diagnóstico durante el sellado → rechazado, el diagnóstico sigue ahí (mismo scenario)
- [ ] 1.5 Dos guardados concurrentes en `DRAFT` → ambos terminan sin error, la Historia sigue en `DRAFT` con los datos completos de uno de los dos (design D3, riesgo "se serializan")
- [ ] 1.6 Sellado que falla: A toma el bloqueo, B espera, A hace rollback → B se confirma sobre una Historia que sigue en `DRAFT`
- [ ] 1.7 Correr 1.1–1.4 contra el código actual y registrar que fallan (prueba de que detectan la carrera)

## 2. Fix (design D1)

- [ ] 2.1 `ClinicalHistoryService.lockDraft(...)`: `lockByPatientId` + `requireDraft`, en ese orden; javadoc con la regla de "primera carga" (D1)
- [ ] 2.2 `ClinicalHistoryService.update` usa `lockDraft`
- [ ] 2.3 `ClinicalHistoryDiagnosisService.add/update/remove` usan `lockDraft`
- [ ] 2.4 1.1–1.6 pasan; los IT existentes de `clinical-history` siguen verdes

## 3. Cierre

- [ ] 3.1 `./mvnw -B verify` verde con `Skipped: 0`
- [ ] 3.2 `docs/backend.md`: regla "bloquear antes de comprobar el estado" para toda escritura de un documento sellable (Historia, odontograma), incluida la condición de primera carga
- [ ] 3.3 `openspec validate update-clinical-history --strict`, y medir a mano el requirement MODIFIED (≤ 500 caracteres): el validador no mide los MODIFIED
