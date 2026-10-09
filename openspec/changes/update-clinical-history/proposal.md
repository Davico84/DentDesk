## Why

La Historia Clínica de Dentdesk hoy es solo el **documento inicial**: se sella en la admisión y después no hay dónde registrar la consulta siguiente. Un paciente que vuelve a los tres meses —o a los dos años— no tiene nota de atención, ni diagnóstico nuevo, ni forma de dejar constancia de una alergia que antes no tenía. La norma exige que toda atención quede registrada y que la historia sea un **libro de actas**: lo escrito no se modifica, lo nuevo se agrega como **registro nuevo** (NTS 139 §5.3.3 i; DS 009-2017-SA arts. 46 y 47 c).

Es el **corte A** de la fase 2 (orden acordado: A nota de consulta → B odontograma de evolución → C odontograma de inicio/final de plan, con `treatment-plans`). Va primero porque B y C se apoyan en él: el odontograma de evolución se cierra **junto con** la nota del día (`ADR-0012` D5).

## What Changes

- **Nota de consulta** (nueva entidad de `clinical-history`): una por atención posterior a la admisión, solo con la Historia `COMPLETED`. Contenido mínimo según la unión de NTS 139 §5.2.1 (1) 1.1 d y 1.2 (ver design D3). Ciclo `ABIERTA → SELLADA`: abierta es un borrador editable, descartable solo si está vacía; sellada es inmutable. Una nota abierta por paciente a la vez, y las notas se sellan en orden cronológico.
- **Fecha y hora de atención** las ingresa el odontólogo (no puede ser futura ni anterior a la última nota sellada). Además queda registrado cuándo se escribió.
- **Diagnósticos de la nota**: CIE10 + tipo P/D/R (NTS 139 §4.2.9). El tipo **R (repetitivo) se rechaza si ese código no se registró antes** al paciente (en la Historia o en una nota sellada).
- **Corrección de un error material** (DS 009 art. 47 c): solo sobre una nota sellada y solo por quien la escribió; es un **registro nuevo** que indica el campo, el texto corregido y el motivo, sin tocar el original. No aplica a diagnósticos ni a omisiones (campo que estaba vacío).
- **Antecedentes nuevos estructurados**: dentro de una nota se registran asientos de **alta** o **baja** en tres categorías —alergias y reacciones adversas (NTS 139 §4.2.10), antecedentes patológicos personales y medicación actual—. Append-only: dar de baja no borra el asiento original.
- **Vista "antecedentes vigentes"**: lo registrado en la Historia (fechado), los asientos vigentes de las notas, y la anamnesis de la ficha del paciente marcada como *registrada al dar de alta, sin autoría clínica*.
- **BREAKING (`patients`)**: la anamnesis de la ficha (`allergies`, `conditions`, `currentMedication`) queda **congelada** (requirement nuevo en `patients`) desde que el paciente tiene Historia Clínica. Editarla después se rechaza; los demás datos de la ficha siguen editables.
- **ADR-0020** (nuevo): la nota de consulta según el texto oficial. Corrige `ADR-0017` D3 (el "cierre diario" es de hospitalización, no de la nota) y `ADR-0018` D2/D3 ("SOAP" no está en la norma; §4.2.9 es sobre diagnósticos; "odontograma inicial y final" es solo de la ficha del niño, §5.2.2 (12.1)).
- Matriz normativa: filas nuevas para la nota y para la corrección; se reescriben las filas "Evolución estructurada en SOAP" y "Enmienda como asiento nuevo vinculado".

## Capabilities

### New Capabilities

_(ninguna)_

### Modified Capabilities

- `clinical-history`: agrega la nota de consulta, sus diagnósticos, la corrección de error material y los antecedentes nuevos con su vista de vigentes. El documento inicial y su sellado no cambian.
- `patients`: la anamnesis de la ficha se congela al existir la Historia Clínica.

> ⚠️ **Granularidad.** `clinical-history` pasa de 9 a 19 requirements, por encima de la guía de ~7–8 (`docs/vision.md` §5). Se mantiene en la misma capacidad porque el roadmap ya lo decidió (fila 4, `ADR-0011`: la evolución pertenece a la historia clínica): mismo actor, mismo documento legal, mismo libro de actas. Si al archivar el spec resulta difícil de leer, partir `consultation-notes` sería un `update-*` aparte, no parte de este change.

## Non-goals

- **Odontograma de evolución y de inicio/final de plan** (cortes B y C, `update-dental-chart`). Esta nota se sella sola; B la amplía para sellar también el odontograma del evento.
- **Índices de la Ficha Odonto-Estomatológica** (IHO-S, placa blanda y calcificada, índice de caries, alta básica odontológica; NTS 139 §5.2.2 (12.2)): change propio.
- **Corrección pedida por el paciente** (DS 009 art. 47 b) y **auditoría de accesos** (art. 32): necesitan `staff` (el profesional que acepta, quién lee).
- **Autoría con colegiatura y restricción del acto clínico al profesional habilitado** (`ADR-0016` D1/D7): bloqueado por `staff`. Hasta entonces cualquier usuario de la clínica escribe notas; la autoría es el usuario (riesgo explícito, design D9).
- **Firma digital** (Ley 27269; punto de extensión `ADR-0014` D2).
- **Corrección de la Historia inicial sellada**: esta fase corrige notas; la Historia sigue cubierta solo por su sellado.
- **Borrar las columnas de anamnesis de `patients`**: un `update-patients` futuro, una vez migrada la lectura.
- **"Próxima cita" como cita agendada**: es solo una fecha; `appointments` decidirá si la consume.
- Copia de la historia al paciente en 5 días (NTS 139 §5.3.3 r, `ADR-0014` D3).

## Impact

- **Backend**: entidades y tablas nuevas `consultation_note`, `consultation_note_diagnosis`, `consultation_note_correction`, `clinical_background_entry` (RLS propia, FK compuestas por tenant, triggers de sello como `V20`). Servicios en `service/clinicalhistory/`. Endpoints nuevos bajo `/patients/{patientId}/consultation-notes` y `/patients/{patientId}/clinical-background`. `PatientService` rechaza cambios de anamnesis si existe Historia.
- **Frontend**: pantalla de notas de consulta del paciente (lista cronológica, editor de la nota abierta, lectura de la sellada, corrección), panel de antecedentes vigentes, `PatientForm` con la anamnesis en solo lectura cuando hay Historia. Reutiliza `Cie10SearchField`.
- **Contrato OpenAPI** y cliente orval regenerados.
- **Docs**: `docs/adr/0020-*.md` (nuevo), notas de corrección en `ADR-0017` y `ADR-0018`, `docs/compliance/regulatory-matrix.md`, `docs/domain.md` (al archivar), `docs/vision.md`.
- Las normas que se subieron a `docs/compliance/normas/` (NTS 139, RM 265-2018, RM 576-2011, DS 009-2017-SA, Ley 27269, Código del COP) se registran en su README; si se versionan los PDF de NTS y COP se decide en el apply (la política vigente dice que no, design D12).
