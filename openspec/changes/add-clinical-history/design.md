## Context

`patients` está construida (`ADR-0004`: el paciente pertenece a una clínica; aislamiento por RLS, `multi-tenancy`). `ADR-0011` decide introducir `clinical-history` como documento inicial obligatorio antes de tratar, con ciclo `DRAFT→COMPLETED`, diagnóstico CIE10 ubicado y un estado consultable que sirve de gate para las capacidades de tratamiento (aún no construidas). El catálogo CIE10-MINSA ya vive en `modules/backend/src/main/resources/data/cie10-minsa.json` (jerárquico, 4 niveles, ~70k códigos, 2.2MB). Stack y capas: `docs/architecture.md`, `docs/backend.md`, `docs/frontend.md`.

## Goals / Non-Goals

**Goals:**
- Modelar `HistoriaClinica` (1 por paciente/clínica) con ciclo `DRAFT→COMPLETED` y sellado server-side de la información inicial.
- Diagnósticos CIE10 con ubicación `GENERAL`/`PIEZA` (FDI del conjunto válido), validando contra el catálogo. `REGION` se difiere hasta tener su clasificación.
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
- `historia_clinica`: `id`, `organization_id` (**obligatorio** — ver D2b), `patient_id` (**UNIQUE** — una por paciente; el paciente ya implica la clínica por `ADR-0004`), `status`, campos narrativos (`motivo_consulta`, `enfermedad_actual`, `antecedentes_personales`, `antecedentes_familiares`, `examen_clinico`, `pronostico`), `signos_vitales` (JSONB — estructura flexible, sin queries por dentro), y los flags afirmativos de D3. Auditoría: `created_at`/`updated_at`/`completed_at` + autoría (ver D2b).
- `clinical_history_diagnosis`: `id`, `organization_id`, `historia_id` (FK), `cie10_code` (FK lógica al catálogo), `ubicacion` enum, `fdi_number` (nullable). Tabla aparte porque son múltiples y estructurados (consultables).
- *Alternativa descartada:* anamnesis en JSONB como hoy en `patients` — justo el patrón del que `ADR-0011` se aleja; los diagnósticos necesitan ser relacionales para validar e indexar.

### D2b — Aislamiento por clínica y autoría, igual que toda entidad de negocio (no negociable)
`HistoriaClinica` y `ClinicalHistoryDiagnosis` son entidades de negocio bajo RLS — **exactamente el mismo patrón que `Patient`** (`V9__patients.sql`, `docs/backend.md §8.1`/`§8.6`), sin excepción. El hecho de colgar de `patient_id` **no** las exime: RLS es por tabla, no se hereda por FK. Cada una lleva:
- Columna `organization_id BIGINT NOT NULL REFERENCES organizations(id)` + índice.
- `@TenantId` en la entidad JPA — el contexto lo fija `TenantAwareTransactionManager`, el código de servicio **no** toca `TenantSession` (ArchUnit lo impide, `business_code_must_not_bind_the_tenant_context_by_hand`).
- Migración con `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`, `... FORCE ROW LEVEL SECURITY` y policy `organization_isolation` (copiar el patrón exacto de `V9__patients.sql:60-77`), más el `GRANT` al rol de aplicación.
- Autoría completa: `@EntityListeners(AuditingEntityListener.class)` + `@CreatedBy`/`@LastModifiedBy` (columnas `created_by`/`updated_by`) — **obligatorio** en cuanto la entidad declara `@TenantId`: lo exige el candado ArchUnit `business_entities_with_tenant_id_must_declare_authorship` (`MultiTenancyArchitectureTest`), que rompe la build si falta.
- Test de aislamiento análogo a `PatientRlsIT` (consulta cruzada entre clínicas con SQL crudo, bypass de JPA) para cada tabla nueva.

`cie10_code` es la **única** excepción: catálogo de referencia global, sin `organization_id` ni RLS — no es dato de una clínica.
- *Hallazgo de review:* el `design.md` original omitía esto por completo en D2 — hueco real detectado antes de implementar, no una alternativa descartada.

### D3 — "Campo vacío ≠ revisado" vía flag afirmativo por sección sensible
El patrón aplica **uniformemente** a las tres secciones donde "nada" es un dato clínico, no un olvido: **alergias, medicación y antecedentes familiares**. Cada una: par **`<seccion>_revisado` (boolean) + `<seccion>_detalle` (text nullable)**. `revisado=true, detalle=null` ⇒ "sin alergias conocidas" / "no toma medicación" / "sin antecedentes familiares"; `revisado=false` ⇒ no revisado. El checklist de D4 exige `revisado=true` en las tres. Así el modelo distingue explícitamente "afirmó que no hay" de "no llenó". (`antecedentes_personales` y `examen_clinico` son texto presente/ausente, no llevan flag afirmativo — su ausencia no es un dato clínico como sí lo es "sin alergias".)
- *Alternativa descartada:* un solo campo texto con convención (`""` vs `"ninguna"`) — frágil, no verificable.

### D4 — Checklist mínimo validado en el servidor en la transición a COMPLETED
La transición valida (400 con detalle de faltantes si no se cumple): `motivo_consulta` no vacío; `antecedentes_personales` presente; `alergias_revisado`, `medicacion_revisado`, `antecedentes_familiares_revisado` = true; `examen_clinico` presente; **≥ 1 diagnóstico**; `pronostico` presente. La lista exacta vive aquí (design), no en el spec, para poder ajustarla sin tocar requirements. Paridad Zod↔Bean Validation en el front (`docs/coding-style.md §7`).

**Transporte del error (verificado contra el código):** esto es una regla de negocio sobre datos ya persistidos, no un `@Valid` de request — `MethodArgumentNotValidException` (que hoy es el único camino que produce `errors[]`) no aplica aquí. Mismo patrón que `existingPatientId` en `update-patients` D4: `ChecklistIncompleteException` (extiende `BusinessException`) lleva `List<String> missingFields`; `@ExceptionHandler(ChecklistIncompleteException.class)` **dedicado** en `GlobalExceptionHandler` (no pasa por `handleBusiness`) construye el `ProblemDetail` vía `ProblemDetails.of(...)` y le agrega `problem.setProperty("missingFields", ex.getMissingFields())`. Contrato: `clinical-history-incomplete` documenta la extensión `missingFields` (opcional, solo en ese `errorType`), mismo patrón que `existingPatientId` en `ApiProblem`.
- *Alternativa descartada:* generalizar `BusinessException` con un mapa de extensiones genérico — mismo motivo que en `update-patients` D4: mayor radio de impacto sobre la base que usan otras excepciones sin necesitarlo; un handler dedicado por caso concreto es el patrón que el proyecto ya usa dos veces (`errors[]` de `@Valid`, `existingPatientId`).

### D5 — Ubicación del diagnóstico y número FDI
`ubicacion: GENERAL | PIEZA`. **`REGION` se difiere**: no hay todavía una clasificación clínica aprobada (cuadrante / maxilar / sector), y dejar una ubicación aceptada por el contrato sin reglas de validación es un hueco. Se agrega en una versión posterior cuando se defina su catálogo (nota: el "Sector" que menciona la estructura MINSA HIS corresponde a esta `REGION` diferida). `PIEZA` ⇒ `fdi_number` obligatorio (validación condicional, como la del responsable de menores en `patients`); `GENERAL` ⇒ **rechaza** un `fdi_number`. El FDI se valida contra el **conjunto real de piezas** (permanentes 11–18/21–28/31–38/41–48; temporales 51–55/61–65/71–75/81–85), **no** por un rango numérico laxo (un rango 11–48 aceptaría `19`/`20`/`29`, que no existen). Los diagnósticos solo se agregan/modifican/eliminan mientras la Historia esté en `DRAFT` (coherente con el sello de D1). **No** hay FK a `dental-chart`/`ToothCondition`: el FDI es identidad intrínseca del diente, no una fila del odontograma — evita el acoplamiento (`ADR-0011`).

### D11 — Registro de diagnóstico conforme a la norma MINSA HIS
La norma MINSA HIS exige que un diagnóstico no sea una cadena suelta. Cada fila de `clinical_history_diagnosis` lleva, además del código y la ubicación: **`tipo_diagnostico`** obligatorio — enum `PRESUNTIVO`(P) / `DEFINITIVO`(D) / `REPETIDO`(R), con `CHECK`; **`created_at`** (fecha/hora del servidor, inmutable) y **`created_by`** (usuario que registra) vía el mecanismo de auditoría ya existente (`AuditorAware` de `multi-tenancy`, `docs/backend.md §8.6`) — no se capturan a mano. La **descripción oficial** del código no se copia en la fila: se lee del catálogo `cie10_code` por el `code` (fuente única). El vínculo formal a la **colegiatura COP** del profesional se **difiere a `staff`**: hoy no existe el dato de colegiatura ni el rol `DENTIST`, así que `created_by` guarda el usuario autenticado del tenant; cuando `staff` modele la credencial profesional, ese `created_by` se resuelve a la COP. Es un gap conocido y explícito, coherente con D10.
- *Alternativa descartada:* copiar la descripción CIE10 en cada fila de diagnóstico — dos fuentes de verdad para el mismo texto; se lee del catálogo.

### D6 — Catálogo CIE10 en tabla, cargado idempotentemente desde el recurso
Se crea la tabla `cie10_code` (`code` PK, `descripcion`, `nivel` con `CHECK`, `parent_code` FK autorreferenciada nullable, índice en `parent_code` y en `descripcion`) y un **loader idempotente** que, al arrancar, la puebla desde `cie10-minsa.json` **solo si está vacía**. Así hay búsqueda/validación por query (indexable, paginable, jerárquica por `parent_code`) sin una migración Flyway de 70k INSERTs. Validar "el código existe" = lookup en tabla.

**Códigos sintéticos para capítulo/grupo (verificado contra el JSON real):** `cie10-minsa.json` no tiene la forma asumida originalmente. Cada entrada es `{ chapter: "CAPITULO I: ...", group: "(A00 - A09) ...", category: { code, name, subcategories: [...] } }` — **`chapter` y `group` son texto libre, sin código propio**; solo `category`/`subcategories` traen un `code` CIE10 real. Para tener los 4 niveles en una sola tabla navegable por `parent_code`, el loader genera códigos **sintéticos deterministas**: `CAP01`, `CAP02`... para capítulos (orden de aparición en el JSON) y `GRP0101`, `GRP0102`... para grupos (prefijados por su capítulo). Determinista = la misma entrada de texto produce siempre el mismo código sintético entre recargas (clave: el texto exacto de `chapter`/`group`, no su posición, para tolerar reordenamientos del JSON fuente). `nivel: CAPITULO|GRUPO|CATEGORIA|SUBCATEGORIA` (`CHECK`). `category`/`subcategories` usan su `code` CIE10 real sin modificar.
- *Alternativa descartada:* tabla separada para capítulos/grupos (`cie10_chapter`/`cie10_group` con `id` técnico) — evita inventar códigos CIE10-like, pero multiplica tablas y la búsqueda jerárquica (1.3) tendría que hacer *join* entre 3 tablas en vez de una consulta recursiva simple sobre `parent_code`. Con códigos sintéticos, una sola tabla resuelve los 4 niveles.
- *Alternativas descartadas (ya evaluadas):* (a) árbol en memoria con Caffeine — evita la tabla pero mete la lógica de búsqueda en app y recarga en cada instancia; (b) servir el JSON al frontend — 2.2MB al bundle y mueve la autoridad de validación fuera del backend (`docs/frontend.md` prohíbe hardcodear datos así).

### D7 — Filiación derivada, no almacenada
`historia_clinica` **no** guarda columnas de filiación. La respuesta de la API compone nombre/sexo/fecha-nacimiento/dirección/teléfono leyendo de `Patient` en el mapper. Por eso la filiación refleja siempre el dato vigente aun en historias `COMPLETED` (no forma parte del sello).

### D8 — Estado de habilitación como endpoint liviano
`GET` del estado por paciente devuelve `{ status: DRAFT|COMPLETED|NONE, enabledForTreatment: boolean }` (`true` solo si `COMPLETED`). Es la señal que `treatment-plans` consumirá; se expone ya para no rehacer el contrato después.

### D9 — `clinical-history` no toca `patients` (aislamiento durante la duplicación temporal)
Este change **no lee, no copia y no modifica** los campos clínicos de `patients` (`allergies`/`conditions`/`currentMedication`). La Historia Clínica captura su anamnesis **de cero**; `patients` queda **intacto y funcional** exactamente como hoy. Toda la reconciliación —copiar valores previos, congelar la edición en `patients`, remover los campos— es responsabilidad **exclusiva** del `update-patients` **futuro** (`ADR-0011`), no de este change. Regla clara mientras coexisten: la Historia Clínica es la única fuente clínica activa; los campos de `patients` no se consultan para decisiones clínicas y se retiran después. Esto evita cualquier lógica de sincronización o resolución de divergencias aquí.
- *Alternativa descartada:* copiar la anamnesis de `patients` al crear la Historia y bloquear su edición en `patients` ya en este change — mete cambios a `patients` (contradice "patients intacto, remoción diferida") y obliga a resolver divergencias entre ambas fuentes ahora, justo lo que se difiere.

### D10 — Acceso en esta fase: miembro autenticado del tenant
Todas las operaciones clínicas (crear, editar `DRAFT`, agregar diagnósticos, sellar, consultar) requieren un **usuario autenticado perteneciente a la clínica** (tenant activo). **No** hay acceso anónimo ni cross-tenant (garantizado por RLS de `multi-tenancy`). La restricción al rol `DENTIST` y la distinción fina consultar/editar/sellar por rol se **difieren a `staff`** — hoy no existe el modelo de roles clínicos, así que cualquier miembro del tenant puede operar (mismo criterio ya aceptado para `dental-chart`/`treatments`, `docs/domain.md`). Es una decisión **explícita y temporal**, no una omisión.

## Risks / Trade-offs

- **Duplicación temporal de anamnesis** (`patients` JSONB + `historia_clinica`) hasta el `update-patients` futuro → mitigada por **D9**: este change no toca `patients`, así que **no hay sincronización ni divergencia que resolver aquí**. La Historia Clínica es la fuente clínica activa; `patients` queda como dato muerto a remover por el `update-patients` futuro. Trackeado en `docs/vision.md §3`.
- **Loader CIE10 en el arranque** (70k filas) → idempotente (carga solo si vacía) e indexado; el costo es un arranque más lento la primera vez. Mitigaciones concretas (hallazgo de review): (a) inserción en **lote** (JDBC batch, no `repository.save()` fila por fila); (b) **transaccional** — si falla a mitad, no deja el catálogo a medias; (c) el chequeo "vacía → cargar" no basta si el arranque se interrumpe a mitad: se valida con un `count` final esperado (no solo `> 0`) para detectar carga incompleta; (d) sin lock explícito entre instancias — aceptable porque el arranque de una nueva versión no corre con réplicas concurrentes escribiendo la misma tabla en este proyecto (single-region, sin blue/green todavía); si eso cambia, revisar. Tests: arranque con tabla vacía puebla completo; segundo arranque no duplica; arranque con carga parcial (simulada) la completa o la rechaza explícitamente, no la deja a medias en silencio.
- **Bypass del sellado** → se valida en el servicio (no solo UI), con test por cada Scenario de "rechazar edición de COMPLETED".
- **FDI mal validado** (ceros, rangos) → entero con rango explícito + regla condicional `PIEZA ⇒ fdi`.

## Migration Plan

1. Flyway: `historia_clinica`, `clinical_history_diagnosis`, `cie10_code` (+ enums vía `CHECK`).
2. Loader idempotente de CIE10 al arranque (carga si `cie10_code` vacía).
3. Contrato: nuevos DTOs en `contracts/openapi.json` + cliente orval regenerado (`ContractDriftIT`).
4. Rollback: las tablas son nuevas y aisladas; revertir = drop de las 3 tablas. No toca datos de `patients`.

## Open Questions

- **Estructura de `signos_vitales`**: se modela como JSONB libre en esta fase (PA, FC, temperatura, etc. sin queries por dentro). Si más adelante se necesita filtrar/graficar por signo, se normaliza — decisión diferible, no bloquea.
