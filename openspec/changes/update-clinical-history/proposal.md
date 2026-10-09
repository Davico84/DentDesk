## Why

Al cerrar `add-dental-chart` (2026-10-08) los tests de concurrencia con hilos reales encontraron que una escritura que lee un documento **sin bloquearlo** se cuela durante su sellado. En el odontograma se corrigió. La **Historia Clínica tiene la misma carrera**, y es una capacidad construida y en uso:

- `ClinicalHistoryService.update(...)` lee la Historia, comprueba `DRAFT` y guarda. Si un sellado (`complete`) confirma entre la lectura y el guardado, **guardar la reabre**: Hibernate escribe todas las columnas, y el `status = DRAFT` leído antes pisa el `COMPLETED`. No hay `@Version`.
- Agregar, editar o eliminar un diagnóstico hace la misma comprobación sin bloqueo: un diagnóstico puede **entrar en una Historia ya sellada**.

Las dos rompen el requirement *Información inicial sellada tras COMPLETED*: un documento médico-legal sellado deja de serlo. Es poco probable (dos operaciones casi simultáneas sobre el mismo paciente), pero el sistema no debe permitirlo.

## What Changes

- Toda escritura de la información inicial (guardado incremental de la Historia y alta, edición y baja de diagnósticos) **bloquea la Historia** (`SELECT ... FOR UPDATE`) **antes** de comprobar que siga en `DRAFT`. Es el mismo patrón que `DentalChartService.lockOpenBlock` y reutiliza `HistoriaClinicaRepository.lockByPatientId`, que ya existe (lo agregó `add-dental-chart` para el sellado).
- El requirement *Información inicial sellada tras COMPLETED* suma dos scenarios: una escritura concurrente con el sellado no reabre la Historia ni deja un diagnóstico en una Historia sellada.
- Tests con hilos reales contra PostgreSQL, deterministas: el sellado mantiene su transacción abierta mientras la otra escritura lo intenta.

No cambia el contrato HTTP ni el frontend: la escritura que pierde la carrera recibe el mismo rechazo que hoy recibe una escritura sobre una Historia sellada.

## Non-goals

- **Evolución post-sello** (nota SOAP, diagnóstico tipo `R`, enmiendas auditables): es la fase 2 de `update-clinical-history`, otro change.
- **Triggers en la base** para la Historia sellada, como hizo `V20` con el odontograma: se valora aparte. `ADR-0014` D4 (revocar `UPDATE`/`DELETE` al rol de aplicación) ya cubre ese terreno a futuro.
- La Ficha Odonto-Estomatológica de NTS 139 (`ADR-0018` D2) y el diagnóstico sin CIE10 para casos estéticos: pendientes aparte en `docs/vision.md`.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `clinical-history`: MODIFIED *Información inicial sellada tras COMPLETED* (+2 scenarios de concurrencia con el sellado).

## Impact

- **Backend**: `ClinicalHistoryService.update` y `ClinicalHistoryDiagnosisService.add/update/remove` cargan la Historia con `lockByPatientId`. Sin migraciones.
- **Tests**: IT de concurrencia (patrón de `CloseAdmissionServiceIT` 2.8f, con señal por `pg_locks`). Los IT existentes de `clinical-history` siguen verdes.
- **Docs**: `docs/vision.md` (estado del change); `docs/backend.md` si conviene dejar la regla "bloquear antes de comprobar el estado" como estándar.
- **Depende de** `add-dental-chart` (PR #68), que trae `lockByPatientId` y el patrón de bloqueo.
