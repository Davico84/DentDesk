# ADR-0011: Historia Clínica como documento inicial obligatorio antes de tratar

- **Estado:** Aceptado (decisión); **pendiente de implementar** — capacidad nueva, aún no propuesta
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

Hasta ahora el modelo clínico planeado era `Paciente → Odontograma (dental-chart) → Tratamientos (treatments)`. Falta un documento que el odontólogo llena **una sola vez**, al inicio, antes de cualquier tratamiento: motivo de consulta, antecedentes, examen clínico, diagnóstico general (CIE10) y pronóstico. Sin él, `dental-chart`/`treatments` no tienen de dónde colgar el diagnóstico ni el criterio clínico que autoriza a tratar.

## Decisión

Se introduce la capacidad **`clinical-history`** (Historia Clínica), con **`HistoriaClinica`** como Aggregate Root: **una por paciente, de por vida, por clínica** (misma cardinalidad que `Patient` ya tiene por `ADR-0004` — no rompe el aislamiento existente).

**Depende únicamente de `patients`** — no de `appointments`. Para agendar una cita a un paciente alcanza con tenerlo registrado; la Historia Clínica se completa **después**, con la información del paciente ya disponible, y **antes** de cualquier tratamiento (no antes de la cita). `clinical-history` y `appointments` son ramas **paralelas** que comparten `patients` como único prerequisito — no hay dependencia entre ellas, y no hace falta construir `appointments` primero.

Se llena **una sola vez**, al inicio del tratamiento del paciente. Al confirmarse, un **diálogo de confirmación explícito** dice al odontólogo que esa información (todo salvo el vínculo al plan de tratamiento) **queda fija**: no se corrige en el propio formulario después. Es la salvaguarda contra el error de tipeo antes de sellar un documento clínico — no hay mecanismo de enmienda/auditoría más allá de eso en este alcance.

- **Filiación** — no duplica: lee de `Patient` (nombre, sexo, fecha de nacimiento, dirección, teléfono).
- **Motivo de consulta, enfermedad actual, antecedentes** (familiares y personales) — **reemplaza** la anamnesis actual de `patients` (`allergies`/`conditions`/`currentMedication`, JSONB en la fila del paciente): esos datos dejan de capturarse ahí y pasan a vivir únicamente en Historia Clínica.
- **Signos vitales, examen clínico.**
- **Diagnóstico(s)** — código **CIE10**, catálogo oficial MINSA (ver `modules/backend/src/main/resources/data/cie10-minsa.json`). **General**, no ligado a pieza dental: no tiene relación con el odontograma ni con `ToothCondition`. El catálogo tiene **jerarquía de 4 niveles** (capítulo → grupo → categoría → subcategorías) que el buscador de diagnóstico SHALL respetar — no aplanarlo a una lista plana de 70 mil códigos.
- **Pronóstico.**
- **Vínculo a Plan de Tratamiento** — referencia/resumen; el contenido real vive en `treatment-plans`.

**Evolución** (controles cronológicos, uno por visita de seguimiento) y **Alta** (cierre del proceso) **sí forman parte de la misma capacidad** `clinical-history` — no se separan en una capacidad aparte — pero se agregan **como tratamientos**: dinámicamente, a lo largo del tiempo, con la misma naturaleza de "se va sumando" que ya tiene `Treatment` en `treatments`. No entran en el documento inicial de una sola vez; llegan después, sobre la misma Historia Clínica ya creada.

**Gate obligatorio:** ningún tratamiento (`treatments`/`treatment-plans`) puede iniciarse sin una Historia Clínica completa para ese paciente en esa clínica.

## Consecuencias

- ✅ El diagnóstico (CIE10) y el criterio clínico que autoriza a tratar quedan modelados — hueco real que no existía en ningún lado.
- ✅ Reordena el roadmap (`docs/vision.md §2`, ya actualizado): nueva fila 4, justo después de `patients`. `appointments` (fila 5) no gana dependencia — es paralela. `dental-chart`/`treatments`/`treatment-plans` (filas 6-8) sí pasan a depender también de `clinical-history`, no solo de `patients`.
- ⚠️ **Modifica un requirement ya construido y testeado de `patients`**: la anamnesis (`allergies`/`conditions`/`currentMedication`) deja de capturarse ahí. Requiere un `update-patients` **futuro y separado** que remueva o redefina ese requirement — es trabajo pendiente que hay que trackear, no algo que resolver ahora.
- ✅ **Verificado, no hay conflicto real con la rama en curso**: `docs/refine-update-patients` (teléfono compartido, contacto de emergencia, documento duplicado) **no menciona ni depende de** `allergies`/`conditions`/`currentMedication` (confirmado con grep sobre sus 4 artefactos). Ambos cambios tocan `patients` en aspectos distintos y pueden implementarse/mergearse en **cualquier orden**. La afirmación original de que "chocaban" era una sobreestimación — se corrigió con una nota en `update-patients/proposal.md`.
- ⚠️ **Sequencing:** como ninguna de `dental-chart`/`treatments`/`treatment-plans` está construida todavía, es el momento correcto para insertar `clinical-history` en el roadmap — evita el patrón de "bloqueo de secuencia" que ya se vio con `multi-tenancy`.

## Alternativas descartadas

- **Historia Clínica como capacidad separada de Evolución/Alta** (`clinical-history` + `clinical-evolution` aparte) — se consideró (mismo argumento que separó `dental-chart` de `treatments`: otro momento, otro actor-en-el-tiempo), pero se descarta: el usuario decidió mantenerlos en la misma capacidad, con Evolución/Alta como una fase posterior (`update-clinical-history`) en vez de una capacidad nueva.
- **Diagnóstico por pieza dental** (ligado a `ToothCondition`) — descartado explícitamente: el diagnóstico CIE10 es general, sin relación al odontograma.
- **Mantener la anamnesis en `patients` además de en `clinical-history`** (duplicada) — descartado: generaría dos fuentes de verdad para el mismo dato clínico, exactamente lo que el modelo de aislamiento y las reglas de "una sola fuente" del proyecto buscan evitar.
