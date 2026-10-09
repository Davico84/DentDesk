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

**Primera carga dentro de la transacción:** `lockByPatientId` tiene que ser la primera lectura de la Historia en la transacción. Si ya estuviera en el contexto de persistencia, Hibernate devolvería la instancia vieja sin refrescarla (lección de `add-dental-chart`). Hoy ninguna de las cuatro rutas la lee antes, en ninguno de sus llamadores (verificado en revisión). Es una **regla de uso** que el compilador no impone: queda escrita en el javadoc de `lockDraft` y en `docs/backend.md`. Quien llame a estas operaciones desde una transacción más amplia no debe cargar antes la Historia.

**`create` queda fuera:** también escribe, pero crea una fila **nueva** en `DRAFT`. No puede reabrir una Historia sellada, y su unicidad concurrente ya la garantiza la base (`UNIQUE(patient_id)`).

**Resultado observable:** el esquema OpenAPI no cambia. Sí cambia el resultado de la carrera, que hoy es inválido: la escritura que hoy podía devolver `200` y reabrir la Historia pasa a recibir el mismo `409 /errors/clinical-history-sealed` que cualquier escritura sobre una Historia sellada.

### D2. Orden de bloqueo

Estas rutas solo bloquean la Historia, así que no pueden deadlockear con el cierre de admisión (paciente → historia → odontograma): toman la Historia como **primer y único** bloqueo de fila, y el cierre la toma segunda, después del paciente, sin que ninguna de estas rutas toque al paciente ni al odontograma.

El alta de un diagnóstico toma además un `KEY SHARE` implícito sobre la Historia, por la FK de `clinical_history_diagnosis`. Entre transacciones distintas, `KEY SHARE` **choca** con el `FOR UPDATE` del sellado: se **serializan**, pero no hay ciclo, porque cada transacción ya tiene la Historia (`FOR UPDATE`) antes de insertar. Dentro de la misma transacción, su propio `KEY SHARE` no se bloquea con su propio `FOR UPDATE`.

### D3. Tests

`ClinicalHistoryConcurrencyIT` (hilos reales, PostgreSQL, rol no superusuario como el resto de los IT de la capacidad), con el patrón determinista de `CloseAdmissionServiceIT` 2.8f. El orden está **forzado**, no librado al azar:
1. El hilo A sella (`CloseAdmissionService.close`) dentro de una transacción que mantiene abierta. Cuando `close` devuelve, A **ya tiene** el `FOR UPDATE` de la Historia, y recién entonces libera al hilo B.
2. B intenta escribir.
3. A confirma cuando B queda **esperando** su bloqueo (`pg_locks`). Con el fix, B siempre espera y, al confirmar A, ve `COMPLETED` y se rechaza. La salida "B ya terminó" solo se da **sin** el fix (B no esperó nada) y es exactamente el caso que hace fallar el test.

Casos:
- **Sellado vs. escritura**, uno por ruta de escritura: guardar la Historia, agregar un diagnóstico, editar uno y eliminar uno. Se rechazan y la Historia queda `COMPLETED` con los diagnósticos tal como estaban al sellarse. Las cuatro rutas comparten `lockDraft`, pero cada una lleva su test, para que un llamador futuro que se salte el helper no pase inadvertido.
- **Dos guardados concurrentes en `DRAFT`:** ambos terminan sin error de bloqueo, se serializan y gana el último en confirmar. La Historia sigue en `DRAFT`, con los datos completos de uno de los dos, no una mezcla.
- **Sellado que falla:** A toma el bloqueo, B queda esperando y A hace rollback (checklist incompleto). B continúa sobre una Historia que sigue en `DRAFT` y su guardado se confirma.

Se comprueban primero contra el código actual. Sin el fix, los casos de sellado vs. escritura tienen que fallar.

## Risks / Trade-offs

- [Dos guardados concurrentes de la misma Historia en `DRAFT` se serializan] → sin impacto: es la misma fila y el último en confirmar gana, igual que hoy. Lo fija un test (D3).
- [Una ruta futura que cargue la Historia antes de llamar a estas operaciones en la misma transacción anularía el bloqueo] → regla escrita en `lockDraft` y `docs/backend.md`; los tests por ruta la detectan en las rutas actuales.
- [El bloqueo se mantiene durante la validación del CIE10 en el alta de diagnóstico] → es una consulta local rápida; aceptable.

## Migration Plan

Sin migraciones ni cambios de contrato. Rollback = revertir el PR.

## Open Questions

Ninguna.
