# ADR-0011: Historia Clínica como documento inicial obligatorio antes de tratar

- **Estado:** Aceptado (decisión); **pendiente de implementar** — capacidad nueva, aún no propuesta
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

Hasta ahora el modelo clínico planeado era `Paciente → Odontograma (dental-chart) → Tratamientos (treatments)`. Falta un documento que el odontólogo llena **una sola vez**, al inicio, antes de cualquier tratamiento: motivo de consulta, antecedentes, examen clínico, diagnóstico general (CIE10) y pronóstico. Sin él, `dental-chart`/`treatments` no tienen de dónde colgar el diagnóstico ni el criterio clínico que autoriza a tratar.

## Decisión

> **Alcance de este documento.** Esto es un ADR: registra la **decisión y su porqué**. Las reglas SHALL detalladas (requirements + scenarios) y el flujo de implementación **no** viven aquí — se generan como el change OpenSpec **`add-clinical-history`** (`proposal.md` / `design.md` / `specs/clinical-history/spec.md` / `tasks.md`) y se validan con `openspec validate --strict`. Este ADR es la fuente del *por qué*; el change es la del *qué construir*.

Se introduce la capacidad **`clinical-history`** (Historia Clínica), con **`HistoriaClinica`** como Aggregate Root: **una por paciente, de por vida, por clínica** (misma cardinalidad que `Patient` ya tiene por `ADR-0004` — no rompe el aislamiento existente).

**Depende únicamente de `patients`** — no de `appointments`. Para agendar una cita a un paciente alcanza con tenerlo registrado; la Historia Clínica se completa **después**, con la información del paciente ya disponible, y **antes** de cualquier tratamiento (no antes de la cita). `clinical-history` y `appointments` son ramas **paralelas** que comparten `patients` como único prerequisito — no hay dependencia entre ellas, y no hace falta construir `appointments` primero.

Se llena **una sola vez** en el sentido de que no se reabre después de sellada — pero no tiene que completarse en un solo acto: hay secciones (antecedentes extensos, examen clínico completo) que toman tiempo y el odontólogo puede necesitar completarlas en una sesión posterior. Para eso, `HistoriaClinica` tiene un **estado explícito**: `DRAFT` → `COMPLETED`.

- **`DRAFT`**: se crea al primer guardado, con lo que haya. Editable libremente, sin diálogo de confirmación ni bloqueo — el odontólogo entra y sale cuantas veces necesite hasta tener todo.
- **`COMPLETED`**: transición **manual**, la decide el odontólogo cuando considera que ya está todo. La transición **valida un checklist mínimo** (motivo de consulta, antecedentes, alergias, medicación, examen clínico, diagnóstico, pronóstico) antes de permitir el sello. Regla clave: **un campo vacío no cuenta como revisado** — "sin alergias conocidas" / "no toma medicación" es un dato **afirmativo**, distinto de "no llenado", y el sistema SHALL distinguir esos casos (no aceptar un vacío como si fuera revisado). Al confirmar dispara el **diálogo explícito**: esa información inicial **queda sellada** — no se edita directamente después.

Guardar **no** es completar: guardar conserva el avance en `DRAFT`; completar confirma que la información inicial ya es suficiente para tratar. La corrección de un error posterior **no** se hace editando en silencio, sino mediante **enmienda auditable** (conserva el valor original, exige motivo, registra quién y cuándo) — mecanismo **diferido a `update-clinical-history`** (fase 2), no entra en esta primera capacidad.

Una `HistoriaClinica` en `DRAFT` **no** satisface el gate de tratamientos (ver abajo) — solo `COMPLETED` lo hace.

- **Filiación** — no duplica: lee de `Patient` (nombre, sexo, fecha de nacimiento, dirección, teléfono).
- **Motivo de consulta, enfermedad actual, antecedentes** (familiares y personales) — **reemplaza** la anamnesis actual de `patients` (`allergies`/`conditions`/`currentMedication`, JSONB en la fila del paciente): esos datos dejan de capturarse ahí y pasan a vivir únicamente en Historia Clínica.
- **Signos vitales, examen clínico.**
- **Diagnóstico(s)** — código **CIE10**, catálogo oficial MINSA (ver `modules/backend/src/main/resources/data/cie10-minsa.json`). El catálogo tiene **jerarquía de 4 niveles** (capítulo → grupo → categoría → subcategorías) que el buscador de diagnóstico SHALL respetar — no aplanarlo a una lista plana de 70 mil códigos. Cada diagnóstico lleva una **ubicación**: `PIEZA` | `REGIÓN` | `GENERAL`. Cuando es `PIEZA`, referencia el diente por su **número FDI** (notación universal, identidad intrínseca del diente) — **no** por una fila de `ToothCondition` del odontograma. Esto da precisión clínica **sin** crear una dependencia de build con `dental-chart`: la historia no lee ni escribe el odontograma, solo anota el número de pieza.
- **Pronóstico.**
- **Relación con Plan(es) de Tratamiento** — la Historia **no** cuelga de un plan ni guarda un vínculo único mutable al "plan actual". Un paciente tiene **varios** `TreatmentPlan` a lo largo de su vida; es cada plan el que **referencia** a la `ClinicalHistory` y valida que esté `COMPLETED`, **no al revés**. La dirección de la dependencia va del plan hacia la historia.

**Evolución** (controles cronológicos de seguimiento) y **Alta** (cierre del proceso) **forman parte de la misma capacidad** `clinical-history` — no se separan en una capacidad aparte — pero llegan en una **fase posterior** (`update-clinical-history`), no en el documento inicial. Cada control es una entidad propia, **`ClinicalEvolutionEntry`**, **append-only**: se agrega al final del historial, nunca edita ni reemplaza los anteriores. Lleva `appointmentId`/`treatmentId` **opcionales** — un control puede colgar de un tratamiento, de una cita, o ser seguimiento clínico suelto. **No** se modela como un `Treatment`: un tratamiento es una **acción** clínica (realizada o planificada); un control es la **observación** del estado del paciente tras esa acción. Son conceptos distintos y merecen entidades distintas.

Esto también cubre el caso del paciente que **regresa tras años** con nuevas alergias o antecedentes: no se enmienda el pasado (que era cierto cuando se registró), se **agrega** un registro cronológico nuevo. Corregir un dato mal escrito es otra cosa — eso sí es enmienda (fase 2), no un control.

**Gate obligatorio:** ningún tratamiento (`treatments`/`treatment-plans`) puede iniciarse sin una Historia Clínica en estado **`COMPLETED`** para ese paciente en esa clínica. Una `DRAFT` no alcanza, sin importar cuánto tenga llenado.

## Consecuencias

- ✅ El diagnóstico (CIE10) y el criterio clínico que autoriza a tratar quedan modelados — hueco real que no existía en ningún lado.
- ✅ **Llenado incremental sin comprometer el sello final**: el estado `DRAFT` cubre el caso real de una historia extensa que no se llena de una sentada, sin necesidad de relajar el bloqueo de `COMPLETED` ni el gate de tratamientos. El costo es modelar un estado más (`DRAFT`/`COMPLETED`) y su transición manual — bajo, ya hay precedente de estados similares en el dominio (`Treatment.status`, `Appointment.status`).
- ✅ **Checklist en el sello, no en el guardado**: `DRAFT` acepta secciones vacías; `COMPLETED` valida el mínimo clínico y obliga a distinguir "no llenado" de "sin hallazgos" — evita historias selladas con vacíos que aparentan revisión.
- ✅ **La dependencia va del plan a la historia**: `TreatmentPlan → ClinicalHistory` (valida `COMPLETED`), nunca la historia colgando de un plan. Soporta múltiples planes por paciente sin un vínculo mutable frágil. Corrige la primera redacción de este ADR (que ponía un "vínculo al plan" dentro de la historia).
- ✅ **Evolución/Alta como entidad propia `ClinicalEvolutionEntry` append-only**, no como `Treatment`: separa acción de observación. Es trabajo de la **fase 2** (`update-clinical-history`) — no infla la primera capacidad. La enmienda auditable (`ClinicalAmendment`) va también a fase 2: la primera capacidad queda lean (documento inicial + `DRAFT`/`COMPLETED` + checklist + CIE10).
- ✅ Reordena el roadmap (`docs/vision.md §2`, ya actualizado): nueva fila 4, justo después de `patients`. `appointments` (fila 5) no gana dependencia — es paralela. `dental-chart`/`treatments`/`treatment-plans` (filas 6-8) sí pasan a depender también de `clinical-history`, no solo de `patients`.
- ⚠️ **Modifica un requirement ya construido y testeado de `patients`**: la anamnesis (`allergies`/`conditions`/`currentMedication`) deja de capturarse ahí. Requiere un `update-patients` **futuro y separado** que remueva o redefina ese requirement — es trabajo pendiente que hay que trackear, no algo que resolver ahora.
- ✅ **Verificado, no hay conflicto real con la rama en curso**: `docs/refine-update-patients` (teléfono compartido, contacto de emergencia, documento duplicado) **no menciona ni depende de** `allergies`/`conditions`/`currentMedication` (confirmado con grep sobre sus 4 artefactos). Ambos cambios tocan `patients` en aspectos distintos y pueden implementarse/mergearse en **cualquier orden**. La afirmación original de que "chocaban" era una sobreestimación — se corrigió con una nota en `update-patients/proposal.md`.
- ⚠️ **Sequencing:** como ninguna de `dental-chart`/`treatments`/`treatment-plans` está construida todavía, es el momento correcto para insertar `clinical-history` en el roadmap — evita el patrón de "bloqueo de secuencia" que ya se vio con `multi-tenancy`.

## Alternativas descartadas

- **Historia Clínica como capacidad separada de Evolución/Alta** (`clinical-history` + `clinical-evolution` aparte) — se consideró (mismo argumento que separó `dental-chart` de `treatments`: otro momento, otro actor-en-el-tiempo), pero se descarta: el usuario decidió mantenerlos en la misma capacidad, con Evolución/Alta como una fase posterior (`update-clinical-history`) en vez de una capacidad nueva.
- **Modelar Control/Evolución como un `Treatment`** (reusar el modelo de tratamientos, menos entidades) — se consideró y de hecho fue la primera decisión, pero se **revierte**: mezcla acción clínica con observación en un mismo tipo. Un control es la observación del estado tras una acción, no la acción — merece su propia entidad `ClinicalEvolutionEntry`.
- **Diagnóstico ligado a `ToothCondition` del odontograma** — descartado: acoplaría `clinical-history` con `dental-chart` (dependencia de build que hoy no existe). La ubicación por pieza se logra con el **número FDI** del diente, no con una fila del odontograma — misma precisión, sin el acoplamiento. (La versión inicial de este ADR descartaba *toda* ubicación por pieza; se revisó: la ubicación `PIEZA`/`REGIÓN`/`GENERAL` sí se admite, solo que referenciando el FDI.)
- **Mantener la anamnesis en `patients` además de en `clinical-history`** (duplicada) — descartado: generaría dos fuentes de verdad para el mismo dato clínico, exactamente lo que el modelo de aislamiento y las reglas de "una sola fuente" del proyecto buscan evitar.
- **Campos obligatorios/opcionales fijos en vez de estado `DRAFT`/`COMPLETED`** — se consideró no introducir un estado nuevo y en su lugar definir de una vez qué campos son obligatorios para guardar (motivo, antecedentes) dejando el resto opcional/nulo hasta completarse. Se descarta: el gate de tratamientos necesita una señal binaria clara ("¿esta historia ya está lista para tratar?"), no una lista de campos opcionales que puede crecer con el tiempo y quedaría implícita en el formulario en vez de explícita en el modelo.
