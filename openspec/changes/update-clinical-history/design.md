# Design — update-clinical-history (fase 2, corte A: nota de consulta)

## Context

- `clinical-history` hoy es el **documento inicial**: `HistoriaClinica` (`DRAFT → COMPLETED`), sus diagnósticos (`clinical_history_diagnosis`, V15) y el sellado coordinado con el odontograma inicial (`CloseAdmissionService`). Después del sellado no existe ningún registro clínico posible.
- Patrones ya pagados que este change reutiliza:
  - **Bloquear y después comprobar el estado** en toda escritura (`ClinicalHistoryService.lockDraft`, `DentalChartService.lockOpenBlock`; `docs/backend.md` §8.3.1). Sin `@Version`, Hibernate escribe todas las columnas y un guardado con estado viejo reabre un documento sellado.
  - **Índice único parcial** para "uno abierto por paciente" (V17 `ux_dental_chart_block_open`): un `exists` previo pierde ante peticiones concurrentes.
  - **Triggers de sello** con SQLSTATE `55000` (V20): el sellado es un hecho de la base, no solo del servicio.
  - **RLS propia por tabla + FK compuesta por tenant** `(organization_id, id)` (V17–V19).
- Texto oficial leído el 2026-10-09: NTS 139 (con la RM 265-2018), DS 009-2017-SA. Los hallazgos que contradicen ADRs anteriores van a `ADR-0020` (ver D11).
- `patients` guarda la anamnesis como listas JSON (`allergies`, `conditions`, `currentMedication`); la Historia la captura de cero, sin leerla (scenario *La Historia no lee ni altera los datos clínicos del paciente*).

## Goals / Non-Goals

**Goals:**
- Registrar cada atención posterior a la admisión como nota inmutable una vez sellada, con el contenido mínimo de NTS 139.
- Corregir errores materiales sin tocar el original (DS 009 art. 47 c).
- Antecedentes nuevos estructurados, alertables, append-only, y una vista de los vigentes.
- Dejar el sellado de la nota como **punto único** al que el corte B agregue el odontograma de evolución.

**Non-Goals:** los del proposal. En particular, ni odontograma de evolución, ni índices de la ficha, ni autoría con colegiatura.

## Decisions

### D1. La nota es un agregado propio, colgado del paciente

Tabla `consultation_note` (paciente, clínica, estado, contenido, fechas, autoría). No es una fila hija de `historia_clinica`: tiene ciclo de vida propio y se crea muchas veces. La relación con la Historia es una **precondición** (debe estar `COMPLETED`), no una FK de composición.

- Gate: paciente **activo** (decisión del usuario 2026-10-09: no se abre una nota a un paciente dado de baja; primero se reactiva) y Historia `COMPLETED`. La Historia `COMPLETED` es irreversible, así que basta leerla al abrir la nota; no hace falta bloquearla.
- Un `ABIERTA` por paciente: índice único parcial `(patient_id) WHERE status = 'ABIERTA'`. La violación se traduce a conflicto (mismo manejo que la creación concurrente de la Historia).
- **Alternativa descartada — `ClinicalEvolutionEntry` SOAP** (lo que anotaba el roadmap): "SOAP" no está en la norma (D11). El nombre y los campos salen del texto oficial.

### D2. Ciclo `ABIERTA → SELLADA`, sin cierre diario

- Abierta = borrador: se edita libremente, varias sesiones.
- Descartar solo si está **vacía**: sin ningún campo de contenido, sin diagnósticos y sin antecedentes. Con contenido no se descarta: lo escrito en una atención es registro clínico aunque no se haya sellado. Una nota abierta olvidada bloquea abrir otra (cronología, `ADR-0012` D7) y eso es intencional: obliga a sellarla.
- **No hay cierre automático diario**: ese es de hospitalización (NTS 139 §5.2.1 (3), "una vez al día"), no de consulta externa. `ADR-0017` D3 lo afirmaba para la nota; se corrige (D11).
- Sellado: `status`, `sealed_at`, `sealed_by`. Unidireccional; segundo sellado rechazado.

### D3. Contenido mínimo: unión de NTS 139 §5.2.1 (1) 1.1 d y 1.2

La lista *"las siguientes consultas deben contener"* que se usó en el explore es de **1.2, hospitales e institutos especializados**. Un consultorio dental privado es **primer nivel (1.1)**, donde la norma usa formatos por etapa de vida y solo el del niño (1.1 d) trae lista para las consultas siguientes; adolescente, joven, adulto y adulto mayor solo fijan la primera atención. Decisión del usuario (2026-10-09): **tomar la unión** de ambas listas como mínimo defendible.

| Campo | Fuente | Al sellar |
|---|---|---|
| fecha y hora de la atención | 1.1 d · 1.2 | obligatorio (D4) |
| motivo de consulta | 1.1 d · 1.2 | **obligatorio** |
| tiempo de enfermedad | 1.1 d | opcional |
| síntomas y signos | 1.2 | opcional |
| tratamiento recibido, cumplimiento y resultado | 1.2 ("de ser el caso") | opcional |
| funciones biológicas | 1.1 d | opcional |
| examen (evaluación odontológica) | 1.1 d · 1.2 "examen físico"; §5.2.2 (12) "evaluación odontológica" | **obligatorio** |
| diagnósticos CIE10 | 1.1 d · 1.2 · §4.2.9 | **≥ 1** |
| tratamiento | 1.1 d · 1.2 | **obligatorio** (texto; "control sin tratamiento" es un valor válido) |
| exámenes auxiliares | 1.1 d | opcional |
| referencia | 1.1 d | opcional |
| fecha de próxima cita | 1.1 d · 1.2 | opcional, **solo fecha**, posterior a la atención |
| edad | 1.1 d | derivada de `Patient.birthDate` a la fecha de la atención, no se captura |
| firma, sello y colegiatura | 1.1 d · 1.2 | autoría por usuario (D9); colegiatura con `staff` |

Todos los textos son `TEXT` nulos. "Vacío" para descarte y omisión = nulo o solo espacios.

### D4. Dos tiempos: cuándo se atendió y cuándo se escribió

- `attended_at` (`TIMESTAMPTZ`): lo ingresa el usuario. Reglas: `≤ now()` (reloj del servidor) y `≥` el `attended_at` de la última nota **sellada** del paciente. Se valida al abrir, al editar y otra vez al sellar.
- `created_at` / `created_by` / `updated_*`: automáticos, como en el resto del dominio.
- **Por qué no usar `created_at` como fecha de atención**: el odontólogo escribe al final del día o al día siguiente. Una sola marca obliga a mentir en una de las dos preguntas. Las dos juntas además dejan auditar el retraso.

### D5. Diagnósticos de la nota: tabla propia, mismas reglas

`consultation_note_diagnosis` replica la forma de V15 (CIE10 con FK al catálogo, `ubicacion` + `fdi_number` emparejados por CHECK, `tipo_diagnostico`, auditoría inmutable). Reutiliza `DiagnosisRequest`, `ValidDiagnosisLocation` y `Cie10SearchService`.

- **Por qué no reutilizar `clinical_history_diagnosis` con una columna `note_id` nula**: mezclaría dos ciclos de vida (DRAFT de la Historia vs. ABIERTA de cada nota) en una tabla cuyas reglas de escritura dependen del padre. Dos tablas con la misma forma son más fáciles de proteger con triggers distintos.
- **Regla R** (`TipoDiagnostico.REPETIDO`): se acepta si el `cie10_code` existe en `clinical_history_diagnosis` de la Historia del paciente **o** en `consultation_note_diagnosis` de una nota **sellada** del mismo paciente. La misma nota abierta no cuenta: no es todavía un registro previo.

### D6. Corrección de error material: registro nuevo, inmutable desde el insert

`consultation_note_correction`: `note_id`, `field` (enum de los **campos de texto** de D3), `corrected_text`, `reason` (obligatorio), `created_at`, `created_by`.

- Solo sobre nota `SELLADA`; solo si `current_user == note.sealed_by` ("el profesional que atiende… en **su** registro", art. 47 c). Quien sella es quien firma.
- El campo destino no puede estar vacío en el original: corregir un vacío es una **omisión**, que el art. 47 c excluye. Los diagnósticos no están en el enum: no hay forma de expresar su corrección.
- Varias correcciones sobre el mismo campo se acumulan; la vista muestra el original y todas, en orden.
- Trigger: la tabla no admite `UPDATE` ni `DELETE`, nunca.
- Fuera: fechas (`attended_at`, próxima cita). Riesgo asumido, ver Risks.

### D7. Antecedentes: libro de asientos alta/baja

`clinical_background_entry`: `patient_id`, `note_id`, `category` (`ALERGIA_RAM` · `ANTECEDENTE_PATOLOGICO` · `MEDICACION`), `kind` (`ALTA` · `BAJA`), `description` (obligatoria en el alta), `closes_entry_id` (obligatorio en la baja, nulo en el alta), auditoría.

- Mismo patrón que el odontograma (`ADR-0012` D3): el hecho clínico es el asiento, el estado vigente se **calcula**.
- Una baja apunta a un **alta vigente**: de otra nota ya sellada, del mismo paciente y categoría, sin otra baja. Unicidad de la baja: índice único parcial sobre `closes_entry_id`.
- Se agregan y quitan solo con la nota `ABIERTA`; al sellar quedan protegidos por trigger (como hallazgos de V20).
- Categoría `ALERGIA_RAM` reúne alergias y **sospechas de reacción adversa** (NTS 139 §4.2.10): para alertar antes de prescribir, las dos son la misma señal. El reporte a farmacovigilancia que exige §4.2.10 queda fuera.
- **Lo que no se puede dar de baja**: el texto de la Historia (`alergias_detalle`, etc.) y la anamnesis congelada de la ficha. No son ítems, son texto o listas sin autoría. La vista los muestra con su origen y su fecha, para que se lean como historia y no como estado actual. Si el odontólogo sabe que ya no aplican, lo deja escrito en el texto de la nota. Riesgo en Risks.

### D8. Vista de antecedentes vigentes: calculada, tres orígenes

`GET /patients/{id}/clinical-background` devuelve, por categoría:

| Origen | Contenido | Fecha mostrada |
|---|---|---|
| `HISTORIA` | `alergias_detalle`, `medicacion_detalle`, `antecedentes_personales` (si la Historia está `COMPLETED`) | `completed_at` |
| `NOTA` | altas de notas **selladas** sin baja | `attended_at` de su nota |
| `FICHA` | `Patient.allergies` / `conditions` / `currentMedication` | ninguna; etiqueta *registrado al dar de alta, sin autoría clínica* |

Se calcula en cada lectura; no hay tabla de vigentes que pueda desincronizarse. El volumen por paciente es chico.

### D9. Autoría: usuario, hasta que exista `staff`

Cualquier usuario autenticado de la clínica abre, edita y sella notas. Autoría = `created_by` / `sealed_by` (`userId`). `ADR-0016` D1 (colegiatura fotografiada) y D7 (solo el habilitado ejerce) siguen **bloqueados por `staff`**. Mismo nivel que hoy tiene la Historia: el riesgo no es nuevo, pero crece con cada nota escrita. Cuando `staff` llegue, las notas existentes quedarán con autoría `userId` sin colegiatura; no se reescriben.

### D10. Congelamiento de la anamnesis en `patients`

`PatientService.update` compara las tres listas recibidas con las guardadas; si alguna difiere y existe `historia_clinica` para el paciente (en cualquier estado), rechaza toda la petición con un `409` RFC 9457 y no guarda nada. Enviar las listas sin cambios es válido: el formulario sigue mandando la ficha completa. El front muestra la anamnesis en solo lectura con un aviso que lleva a la consulta.

- **Por qué desde que existe la Historia y no desde que se sella**: desde que existe, la Historia captura su propia anamnesis. Dos lugares editables para el mismo dato crean dos verdades.
- **Por qué no borrar las columnas ahora**: la vista de vigentes las lee (origen `FICHA`), y borrarlas es destructivo. Va a un `update-patients` futuro.

### D11. ADR-0020 y correcciones a ADRs anteriores

`docs/adr/0020-nota-de-consulta.md` registra D1–D7 con su fundamento en el texto oficial, y corrige:

| ADR | Decía | Texto oficial |
|---|---|---|
| `ADR-0017` D3 (nota final) | "el cierre diario sí aplica a la nota de evolución" | "una vez al día" es §5.2.1 (3), **hospitalización**. Consulta externa no tiene cierre diario |
| `ADR-0018` D3 (fila SOAP) | evolución SOAP en §5.2.2 · §4.2.9 | "SOAP" no aparece. §4.2.9 es **diagnósticos** (CIE10 + P/D/R). El contenido de la nota sale de §5.2.1 (1) |
| `ADR-0018` D2 (fila 12.1) | "odontograma inicial y final" como exigencia general | es solo de **12.1, ficha del niño**; 12.2 (adulto) pide "odontograma" y "alta básica odontológica con fecha" |

Los ADR no se reescriben: se agrega una línea de corrección en la cabecera que apunta a `ADR-0020`, como se hizo con `ADR-0016` → `ADR-0018`.

### D12. Normas subidas: registrar en el README y decidir el versionado

`docs/compliance/normas/README.md` dice que las NTS y el Código del COP **no** se versionan (peso, licencia), aunque la NTS 188 (9 MB) ya está versionada. Los PDF nuevos sin commitear pesan: NTS 139 3,6 MB, Código COP 1 MB, DS 009 0,5 MB, RM 576 0,15 MB, Ley 27269 0,06 MB, RM 265 0,06 MB. Por la regla del README, DS 009, RM 576, Ley 27269 y RM 265 **sí** entran (normas chicas de publicación abierta), renombrados con número y año. Para NTS 139 y COP, el usuario decide en el apply (tarea 1.2). Hay además una **copia duplicada** de la RM 265 en `docs/compliance/` (fuera de `normas/`), que se elimina.

## Risks / Trade-offs

- **Cualquier usuario firma notas clínicas** (D9) → cada nota escrita antes de `staff` queda con autoría sin colegiatura. Mitigación: es explícito en el ADR y en la matriz; `staff` sigue siendo la siguiente prioridad de cumplimiento.
- **Fechas no corregibles** (D6) → un `attended_at` equivocado en una nota sellada queda así. Mitigación: se valida al sellar contra el reloj y la cronología; el front confirma la fecha antes de sellar.
- **Texto de la Historia y anamnesis de ficha no se pueden dar de baja** (D7) → la vista de vigentes puede mostrar una alergia que ya no aplica. Para alertar, mostrar de más es el error barato; ocultar de menos es el caro.
- **Una nota abierta olvidada bloquea al paciente** (D2) → la UI la muestra arriba con su fecha y acción de sellar o descartar.
- **Granularidad** (`clinical-history` 19 requirements) → ver proposal; partir sería un change aparte.
- **`patients` BREAKING** → un cliente que hoy edita la anamnesis después de crear la Historia recibe `409`. Solo existe el front propio, que se ajusta en el mismo change.

## Migration Plan

- Flyway `V21__consultation_note.sql` (nota + diagnósticos + correcciones + antecedentes, RLS, índices, FK compuestas) y `V22__consultation_note_seal_guard.sql` (triggers). Solo aditivas: no tocan datos existentes.
- Sin backfill: las Historias ya selladas no tienen notas, y la anamnesis existente de `patients` queda como origen `FICHA`.
- Rollback: las tablas son nuevas; revertir el código deja de escribirlas. El congelamiento es solo lógica de servicio.

## Open Questions

- **Purpose de `clinical-history`**: dice que "la evolución/alta y las enmiendas son fases posteriores". Texto propuesto para revisar ahora y aplicar al archivar: *"…Esta capacidad cubre el documento inicial y su sellado (`DRAFT→COMPLETED`), y las **notas de consulta** posteriores con sus diagnósticos, antecedentes nuevos y corrección de error material. El alta básica odontológica, los índices de la ficha y la corrección pedida por el paciente son fases posteriores."*
