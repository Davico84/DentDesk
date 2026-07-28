## Why

El modelo clínico no tiene dónde registrar el **diagnóstico** ni el **criterio clínico que autoriza a tratar**: `dental-chart`/`treatments`/`treatment-plans` (planeadas) asumen que ese juicio ya existe, pero ninguna capacidad lo produce. La **Historia Clínica** es el documento médico-legal que inicia la atención, contiene ese diagnóstico (CIE10) y es el **prerrequisito** para cualquier plan de tratamiento (`ADR-0011`). Sin ella, no hay forma de exigir "primero evalúa, después trata".

## What Changes

- **Nueva capacidad `clinical-history`** con `HistoriaClinica` como agregado: **una por paciente, de por vida, por clínica** (misma cardinalidad que `Patient`, `ADR-0004`). Depende **solo** de `patients`.
- **Ciclo de vida `DRAFT → COMPLETED`**: se crea en `DRAFT` (guardado incremental, editable, sin bloqueo); pasa a `COMPLETED` por acción manual del odontólogo, previa **validación de un checklist mínimo** y **diálogo de confirmación**. Una vez `COMPLETED`, la información inicial **queda sellada** (no se edita directamente).
- **Contenido del documento inicial**: filiación (leída de `Patient`, no duplicada), motivo de consulta, enfermedad actual, antecedentes (familiares/personales), alergias, medicación, signos vitales, examen clínico, **diagnóstico(s) CIE10** (con ubicación `PIEZA`/`REGIÓN`/`GENERAL`; la pieza por número FDI, sin acoplar al odontograma) y pronóstico.
- **Estado consultable para el gate**: la capacidad expone "¿este paciente tiene una Historia Clínica `COMPLETED` en esta clínica?" — la señal que `treatment-plans`/`treatments` usarán (cuando se construyan) para bloquear el inicio de tratamientos.
- **Checklist afirmativo**: un campo vacío **no** cuenta como revisado; "sin alergias conocidas" / "no toma medicación" son datos afirmativos distintos de "no llenado", y el sello los distingue.

## Capabilities

### New Capabilities
- `clinical-history`: creación y sellado del documento clínico inicial (Historia Clínica) por paciente/clínica, con ciclo `DRAFT→COMPLETED`, diagnóstico CIE10 ubicado, checklist de completitud y exposición del estado que habilita tratar.

### Modified Capabilities
<!-- ninguna en este change. La remoción de la anamnesis de `patients` es un update-patients FUTURO y separado (ver Non-goals), no parte de esta capacidad. -->

## Non-goals (fuera de alcance)

- **Evolución (controles) y Alta** — misma capacidad conceptual pero **fase posterior** (`update-clinical-history`): entidad `ClinicalEvolutionEntry` append-only. No entra aquí (`ADR-0011`).
- **Enmiendas auditables** (`ClinicalAmendment`) para corregir lo ya sellado — también fase 2.
- **Remover la anamnesis de `patients`** (`allergies`/`conditions`/`currentMedication`) — es un `update-patients` **futuro y separado**. Durante este change, la Historia Clínica captura su propia anamnesis; los campos de `patients` se retiran después, no ahora. Se acepta la duplicación temporal.
- **Enforcement del gate dentro de `treatments`/`treatment-plans`** — esas capacidades no existen todavía. Aquí solo se **expone** el estado `COMPLETED`; el bloqueo real lo aplicará la capacidad consumidora cuando se construya.
- **Odontograma** (`dental-chart`), **presupuesto/plan** (`treatment-plans`/`patient-billing`), **cierre automático de citas** (`appointments`) — pertenecen a otras capacidades del roadmap (`docs/vision.md §2`), no a esta.
- **Rol `DENTIST` real** — como `dental-chart`/`treatments`, se especifica sin esperar `staff`; hoy `ADMIN`/`USER` pueden lo clínico (`docs/domain.md`). El candado por rol llega con `staff`.

## Impact

- **Backend**: nueva entidad `HistoriaClinica` + tabla + migración Flyway; enum de estado (`DRAFT`/`COMPLETED`) y de ubicación de diagnóstico; carga/consulta del catálogo CIE10 (`modules/backend/src/main/resources/data/cie10-minsa.json`, jerárquico 4 niveles) con endpoint de búsqueda; validación del checklist en la transición a `COMPLETED`; endpoint de consulta del estado por paciente. Capas y estándares: `docs/backend.md`.
- **Frontend**: pantallas de creación/edición del documento en `DRAFT`, buscador CIE10 que respeta la jerarquía (no aplana), diálogo de confirmación del sello, indicador de estado (`DRAFT`/`COMPLETED`). Paridad de validación Zod↔Bean Validation (`docs/coding-style.md §7`). Estándares: `docs/frontend.md`.
- **Contrato**: nuevos tipos en `contracts/openapi.json` + cliente orval regenerado (`ContractDriftIT`).
- **Docs al archivar**: actualizar `docs/domain.md` (nueva entidad y su ER) y marcar la capacidad como construida en `docs/vision.md`.
- **Dependencias**: `patients` (construida). Habilita `dental-chart`/`treatments`/`treatment-plans` (planeadas).
