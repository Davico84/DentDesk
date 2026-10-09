## Context

- `ClinicalHistoryService.complete` ya bloquea la Historia (`HistoriaClinicaRepository.lockByPatientId`, `SELECT ... FOR UPDATE`) desde `add-dental-chart`. El resto de las escrituras no lo hacen:
  - `ClinicalHistoryService.update` → `findByPatientId` (sin bloqueo) → `requireDraft` → `save`.
  - `ClinicalHistoryDiagnosisService.add/update/remove` → `findByPatientId` (sin bloqueo) → `requireDraft` → escribe el diagnóstico.
- `HistoriaClinica` no tiene `@Version` y Hibernate actualiza **todas** las columnas de la entidad, incluido `status`.
- Precedente directo: `add-dental-chart` design D9 y su test 2.8f (`CloseAdmissionServiceIT`), que demostró la carrera en el odontograma y la cerró con `DentalChartService.lockOpenBlock`.

## Goals / Non-Goals

**Goals:** ninguna escritura de la información inicial puede reabrir una Historia sellada ni escribir en ella; probado con hilos reales contra PostgreSQL, de forma determinista.

**Non-Goals:** ver `proposal.md`.

## Decisions

### D1. Bloquear antes de comprobar `DRAFT`, en toda escritura

Un helper `ClinicalHistoryService.lockDraft(repository, patientId)` (estático, mismo estilo que `lockOpenBlock`) que hace `lockByPatientId` + `requireDraft`, usado por `update` y por las tres operaciones de diagnóstico. El orden importa: primero el bloqueo y después la comprobación, para que el estado leído sea el ya confirmado por cualquier sellado concurrente.

- **Sellado primero:** la escritura espera al bloqueo, ve `COMPLETED` y se rechaza con `HistoriaClinicaSealedException`.
- **Escritura primero:** el sellado espera, y al validar el checklist ve la escritura ya confirmada.

**Por qué no `@Version` (bloqueo optimista):** también detectaría la carrera, pero solo en `update`, porque los diagnósticos son otra tabla y su alta no toca la fila de la Historia. Además cambiaría el contrato (409 por conflicto de versión) y obligaría al cliente a reenviar la versión. El bloqueo pesimista cubre las cuatro rutas con una sola regla y sin tocar la API.

**Primera carga dentro de la transacción:** `lockByPatientId` tiene que ser la primera lectura de la Historia en la transacción. Si ya estuviera en el contexto de persistencia, Hibernate devolvería la instancia vieja sin refrescarla (lección de `add-dental-chart`). Hoy ninguna de las cuatro rutas la lee antes.

### D2. Orden de bloqueo

Estas rutas solo bloquean la Historia, así que no pueden deadlockear con el cierre de admisión (paciente → historia → odontograma): todas toman la Historia como único bloqueo, o el segundo después del paciente. Un alta de diagnóstico toma además un `KEY SHARE` implícito sobre la Historia por la FK, compatible con el `FOR UPDATE` en el orden descrito.

### D3. Tests

`ClinicalHistoryConcurrencyIT` (hilos reales, PostgreSQL, rol no superusuario como el resto de los IT de la capacidad), con el patrón determinista de `CloseAdmissionServiceIT` 2.8f:
- el hilo A sella dentro de una transacción que mantiene abierta;
- el hilo B intenta guardar la Historia (o agregar un diagnóstico);
- A confirma cuando B queda **esperando** el bloqueo (`pg_locks`) o cuando B ya terminó.

Sin el fix, B escribe y el test falla: la Historia vuelve a `DRAFT`, o queda un diagnóstico en una Historia sellada. Se comprueba primero contra el código actual para ver el fallo.

## Risks / Trade-offs

- [Dos guardados concurrentes de la misma Historia en `DRAFT` se serializan] → sin impacto: es la misma fila y el último en confirmar gana, igual que hoy.
- [El bloqueo se mantiene durante la validación del CIE10 en el alta de diagnóstico] → es una consulta local rápida; aceptable.

## Migration Plan

Sin migraciones ni cambios de contrato. Rollback = revertir el PR.

## Open Questions

Ninguna.
