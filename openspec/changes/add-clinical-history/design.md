## Context

`patients` está construida (`ADR-0004`: el paciente pertenece a una clínica; aislamiento por RLS, `multi-tenancy`). `ADR-0011` decide introducir `clinical-history` como documento inicial obligatorio antes de tratar, con ciclo `DRAFT→COMPLETED`, diagnóstico CIE10 ubicado y un estado consultable que sirve de gate para las capacidades de tratamiento (aún no construidas). El catálogo CIE10-MINSA ya vive en `modules/backend/src/main/resources/data/cie10-minsa.json` (jerárquico, 4 niveles, ~70k códigos, 2.2MB). Stack y capas: `docs/architecture.md`, `docs/backend.md`, `docs/frontend.md`.

## Goals / Non-Goals

**Goals:**
- Modelar `HistoriaClinica` (1 por paciente/clínica) con ciclo `DRAFT→COMPLETED` y sellado server-side de la información inicial.
- Diagnósticos CIE10 con ubicación `GENERAL`/`REGION`/`PIEZA` (FDI), validando contra el catálogo.
- Búsqueda de CIE10 que respeta la jerarquía de 4 niveles.
- Exponer el estado de habilitación (`COMPLETED`) por paciente.

**Non-Goals:**
- Evolución/Alta (`ClinicalEvolutionEntry`) y enmiendas (`ClinicalAmendment`) — fase 2 (`update-clinical-history`).
- Remover la anamnesis de `patients` — `update-patients` futuro; aquí se acepta duplicación temporal.
- Enforcement del gate dentro de `treatments`/`treatment-plans` — esas capacidades lo consumirán cuando existan.
- Rol `DENTIST` real — llega con `staff`.

## Decisions

### D1 — Estado como enum con transición unidireccional en esta fase
`HistoriaClinica.status: DRAFT | COMPLETED`, columna `status` + `CHECK`. Transición **solo** `DRAFT→COMPLETED`, manual. No hay reapertura ni `AMENDED` en esta capacidad (llegan en fase 2). El sellado se **valida en el servicio**, no solo en la UI: cualquier escritura sobre información inicial de una historia `COMPLETED` se rechaza con un `errorType` dedicado (patrón `BusinessException`, `docs/backend.md`).
- *Alternativa descartada:* máquina de estados con librería — sobredimensionado para dos estados.

### D2 — Modelo de datos relacional, no JSONB
- `historia_clinica`: `id`, `patient_id` (**UNIQUE** — una por paciente; el paciente ya implica la clínica por `ADR-0004`), `status`, campos narrativos (`motivo_consulta`, `enfermedad_actual`, `antecedentes_personales`, `antecedentes_familiares`, `examen_clinico`, `pronostico`), `signos_vitales` (JSONB — estructura flexible, sin queries por dentro), y los flags afirmativos de D3. Auditoría: `created_at`/`updated_at`/`completed_at`.
- `clinical_history_diagnosis`: `id`, `historia_id` (FK), `cie10_code` (FK lógica al catálogo), `ubicacion` enum, `fdi_number` (nullable). Tabla aparte porque son múltiples y estructurados (consultables).
- *Alternativa descartada:* anamnesis en JSONB como hoy en `patients` — justo el patrón del que `ADR-0011` se aleja; los diagnósticos necesitan ser relacionales para validar e indexar.

### D3 — "Campo vacío ≠ revisado" vía flag afirmativo por sección sensible
Para alergias, medicación y antecedentes familiares (secciones donde "nada" es un dato clínico, no un olvido): par **`<seccion>_revisado` (boolean) + `<seccion>_detalle` (text nullable)**. `revisado=true, detalle=null` ⇒ "sin alergias conocidas"; `revisado=false` ⇒ no revisado. El checklist de D4 exige `revisado=true`. Así el modelo distingue explícitamente "afirmó que no hay" de "no llenó".
- *Alternativa descartada:* un solo campo texto con convención (`""` vs `"ninguna"`) — frágil, no verificable.

### D4 — Checklist mínimo validado en el servidor en la transición a COMPLETED
La transición valida (400 con detalle de faltantes si no se cumple): `motivo_consulta` no vacío; `antecedentes_personales` presente; `alergias_revisado`, `medicacion_revisado`, `antecedentes_familiares_revisado` = true; `examen_clinico` presente; **≥ 1 diagnóstico**; `pronostico` presente. La lista exacta vive aquí (design), no en el spec, para poder ajustarla sin tocar requirements. Paridad Zod↔Bean Validation en el front (`docs/coding-style.md §7`).

### D5 — Ubicación del diagnóstico y número FDI
`ubicacion: GENERAL | REGION | PIEZA`. `PIEZA` ⇒ `fdi_number` obligatorio (validación condicional, como la del responsable de menores en `patients`). FDI se guarda como entero de 2 dígitos (11–48 permanentes, 51–85 temporales) validado por rango. **No** hay FK a `dental-chart`/`ToothCondition`: el FDI es identidad intrínseca del diente, no una fila del odontograma — evita el acoplamiento (`ADR-0011`).

### D6 — Catálogo CIE10 en tabla, cargado idempotentemente desde el recurso
Se crea la tabla `cie10_code` (`code` PK, `descripcion`, `nivel`, `parent_code` nullable) y un **loader idempotente** que, al arrancar, la puebla desde `cie10-minsa.json` **solo si está vacía**. Así hay búsqueda/validación por query (indexable, paginable, jerárquica por `parent_code`) sin una migración Flyway de 70k INSERTs. Validar "el código existe" = lookup en tabla.
- *Alternativas descartadas:* (a) árbol en memoria con Caffeine — evita la tabla pero mete la lógica de búsqueda en app y recarga en cada instancia; (b) servir el JSON al frontend — 2.2MB al bundle y mueve la autoridad de validación fuera del backend (`docs/frontend.md` prohíbe hardcodear datos así).

### D7 — Filiación derivada, no almacenada
`historia_clinica` **no** guarda columnas de filiación. La respuesta de la API compone nombre/sexo/fecha-nacimiento/dirección/teléfono leyendo de `Patient` en el mapper. Por eso la filiación refleja siempre el dato vigente aun en historias `COMPLETED` (no forma parte del sello).

### D8 — Estado de habilitación como endpoint liviano
`GET` del estado por paciente devuelve `{ status: DRAFT|COMPLETED|NONE, enabledForTreatment: boolean }` (`true` solo si `COMPLETED`). Es la señal que `treatment-plans` consumirá; se expone ya para no rehacer el contrato después.

## Risks / Trade-offs

- **Duplicación temporal de anamnesis** (`patients` JSONB + `historia_clinica`) hasta el `update-patients` futuro → sin sincronización entre ambas; la Historia Clínica es la fuente clínica desde ya, `patients` queda como dato muerto a remover. Trackeado en `docs/vision.md §3`.
- **Loader CIE10 en el arranque** (70k filas) → idempotente (carga solo si vacía) e indexado; el costo es un arranque más lento la primera vez. Mitigación: índice en `parent_code` y en búsqueda por descripción.
- **Bypass del sellado** → se valida en el servicio (no solo UI), con test por cada Scenario de "rechazar edición de COMPLETED".
- **FDI mal validado** (ceros, rangos) → entero con rango explícito + regla condicional `PIEZA ⇒ fdi`.

## Migration Plan

1. Flyway: `historia_clinica`, `clinical_history_diagnosis`, `cie10_code` (+ enums vía `CHECK`).
2. Loader idempotente de CIE10 al arranque (carga si `cie10_code` vacía).
3. Contrato: nuevos DTOs en `contracts/openapi.json` + cliente orval regenerado (`ContractDriftIT`).
4. Rollback: las tablas son nuevas y aisladas; revertir = drop de las 3 tablas. No toca datos de `patients`.

## Open Questions

- **Estructura de `signos_vitales`**: se modela como JSONB libre en esta fase (PA, FC, temperatura, etc. sin queries por dentro). Si más adelante se necesita filtrar/graficar por signo, se normaliza — decisión diferible, no bloquea.
