# Design

## Context

Ver `proposal.md` (Why). Estado observado:

- **Nota de consulta** (`V21`, `V22`, `ConsultationNoteService`): `ABIERTA → SELLADA`; campos de texto libre (motivo,
  examen, tratamiento…), diagnósticos (`consultation_note_diagnosis`: CIE10 + `GENERAL`/`PIEZA` con **un** FDI + P/D/R)
  y antecedentes. Los hijos de la nota tienen guarda de sellado en la base (`consultation_note_child_seal_guard`,
  `BEFORE INSERT OR UPDATE OR DELETE`), RLS forzada por clínica y FK compuestas `(organization_id, …)`.
- **Odontograma** (`V17`–`V20`): un bloque `INITIAL` por paciente, sellado **atómicamente con la Historia Clínica**;
  sus hallazgos (`dental_chart_finding`, `ux_dental_chart_finding_org_id`) son inmutables una vez sellado. Una nota solo
  se abre con la Historia `COMPLETED` (spec `clinical-history`), así que **todo hallazgo que una nota puede citar hoy
  está sellado**.
- **Color**: lo calcula el frontend (`findingColor`) desde el catálogo: `SIEMPRE_ROJO`, `SIEMPRE_AZUL` o `SEGUN_ESTADO`
  (`condition` `GOOD`/`POOR`). "Se ve rojo" = `SIEMPRE_ROJO` o `SEGUN_ESTADO` con `POOR`.
- **CIE10**: catálogo MINSA en `cie10_code` (códigos sin punto: `K021`); `GET /cie10?query=` busca en todo el catálogo y
  `Cie10SearchField` lo usa en `DiagnosisForm`.
- Las etiquetas legibles de los hallazgos (`findingTypeLabel`, `subtypeOptionLabel`) viven en el frontend; el catálogo
  del backend tiene tipo, subtipo, sigla, alcance y color, no el nombre en castellano.

## Goals / Non-Goals

**Goals:**
- Que elegir un hallazgo reemplace tipearlo: queda registrado en la nota, aparece en el examen y propone el diagnóstico.
- Que la nota sellada se lea sola y que el vínculo sirva al odontograma de evolución (corte 2).

**Non-Goals:**
- Cambiar el odontograma, su color o su sellado; el hallazgo atendido sigue rojo hasta el corte 2.
- Ubicaciones de diagnóstico nuevas (cara, rango, entre piezas).

## Decisions

### D1 — Tabla `consultation_note_finding`, hija de la nota

```
consultation_note_finding(id, organization_id, note_id, finding_id,
                          description VARCHAR(300) NOT NULL CHECK (btrim(description) <> ''),
                          created_at, updated_at, created_by, updated_by)
  FK (organization_id, note_id)    → consultation_note
  FK (organization_id, finding_id) → dental_chart_finding
  UNIQUE (note_id, finding_id)
```

Migración `V29`: RLS forzada con `organization_isolation` como el resto de la nota, y el **mismo trigger**
`consultation_note_child_seal_guard` en `BEFORE INSERT OR UPDATE OR DELETE`, como diagnósticos y antecedentes (`V22`):
con la nota `SELLADA` la base rechaza cualquier escritura aun por API directa o SQL. La API no expone `UPDATE` (un
vínculo se quita y se vuelve a marcar), pero la guarda cubre también esa vía. `updated_*` existen por la regla de
autoría completa (`docs/backend.md`) y valen lo mismo que `created_*`. Las invariantes de la descripción viven en la
base (`VARCHAR(300)` + `CHECK` no vacía), no solo en Java. `GRANT` defensivo a `odontorisas_app` si el rol existe, como
`V26` (los `DEFAULT PRIVILEGES` de `V6` ya lo cubren; el bloque es idempotente y protege ante una base rehecha).

**Errores de `POST …/findings`** (RFC 9457):

| Caso | Respuesta |
|---|---|
| Nota o hallazgo inexistente, de otra clínica (RLS) o de **otro paciente** | 404 (anti-enumeración, como diagnósticos y antecedentes) |
| Nota `SELLADA` | 409 (`ConsultationNoteSealedException`, ya existe) |
| Hallazgo de un bloque no sellado | 409 propio |
| Hallazgo ya marcado en la nota | 409 propio |
| Descripción vacía o > 300 | 400 de validación |

- **Lectura sin N+1**: `ConsultationNoteService.list` ya carga diagnósticos, antecedentes y correcciones de todas las
  notas en bloque (`findByNoteIdInOrderByIdAsc` + `byNote`); los hallazgos atendidos se cargan igual, en la misma
  pasada.

- **Mismo paciente**: el servicio comprueba que el hallazgo pertenezca al bloque de odontograma del paciente de la nota
  (`finding → block.patient_id = note.patient_id`): el hallazgo no tiene `patient_id` propio, así que la base solo
  garantiza la clínica (FK compuesta) y el paciente lo garantiza el servicio. Otro paciente u otra clínica → 404.
- **Concurrencia con el sellado**: marcar y desmarcar toman el mismo bloqueo de la nota abierta que el resto de las
  escrituras (`SELECT … FOR UPDATE`); una escritura que espera detrás del sellado recibe 409 al despertar, y la guarda de
  la base cubre cualquier camino que no pase por el servicio.
- **Solo hallazgos de un bloque sellado**: la descripción guardada tiene que seguir siendo cierta. Hoy siempre se cumple
  (ver Context); se valida igual para que el corte 2, con bloques de evolución abiertos, no rompa el supuesto en
  silencio.
- **Duplicado** → `UNIQUE (note_id, finding_id)` + chequeo en el servicio con un error RFC 9457 propio (409).
- *Alternativa descartada:* guardar solo la referencia. La nota sellada dependería de leer el odontograma para decir qué
  se atendió; la norma pide que el registro clínico se lea por sí mismo.

### D2 — La descripción la compone el cliente y el servidor la guarda

El texto ("Pieza 16, cara oclusal: Lesión de caries — Mancha Blanca") lo arma el frontend con las etiquetas que ya
tiene y lo envía al marcar; el servidor exige texto no vacío (≤ 300 caracteres) y lo guarda. Es contenido clínico de la
nota escrito desde la UI, igual que el examen. *Alternativa descartada:* llevar las etiquetas del catálogo al backend
para componerlo allá: duplica `findingTypeLabels` en dos lenguajes por un texto que el usuario igual ve antes de
marcar.

### D3 — Marcar agrega una línea al examen, editable

Al marcar un hallazgo, el frontend agrega su descripción como línea nueva al campo **Examen** del formulario de la nota
(sin guardarlo solo: el usuario guarda como hoy). Así el examen, que es obligatorio para sellar, no se tipea dos veces,
y el odontólogo puede completar lo que vio. La línea entra con `setValue(…, { shouldDirty: true })`: el formulario
queda con cambios sin guardar, "Sellar nota" se deshabilita y aparece "Tienes cambios sin guardar" (comportamiento que
ya tiene `OpenNoteEditor`). Así nadie cree que marcar el hallazgo ya guardó el texto del examen. No hay doble fuente de
verdad: el Examen es la narrativa clínica; el vínculo es el dato estructurado para el sistema y para el corte 2. Desmarcar **no** borra la línea del examen: puede haber sido editada; el
usuario la quita si quiere.

### D4 — Sugerencias CIE10 en el backend, verificadas contra el catálogo

`GET /cie10/suggestions?findingType=&subtype=` devuelve `[{ code, descripcion }]` desde un mapa fijo validado por el
odontólogo (explore 2026-10-16):

| Tipo:subtipo | Códigos |
|---|---|
| `LESION_CARIES:MB`, `LESION_CARIES:CE` | `K020` |
| `LESION_CARIES:CD` | `K021` |
| `LESION_CARIES:CDP` | `K025`, `K040` |
| `FRACTURA_DENTAL` | `S025` |
| `DEFECTOS_DESARROLLO_ESMALTE:O` | `K003`, `K004` |
| `DEFECTOS_DESARROLLO_ESMALTE:PE` | `K004` |
| `MOVILIDAD_PATOLOGICA` | `K053`, `K088` |
| `REMANENTE_RADICULAR` | `K083` |
| `SUPERFICIE_DESGASTADA` | `K030`, `K031`, `K032` |
| `RESTAURACION_TEMPORAL`, `CORONA_TEMPORAL`, todo lo demás | ninguno |

En el backend porque el catálogo CIE10 está ahí: un test de integración recorre el mapa y exige que **cada código exista
en `cie10_code`** (Scenario "Las sugerencias existen en el catálogo"); la descripción sale del catálogo, no del mapa.
*Alternativa descartada:* constante en el frontend: no puede verificar los códigos contra el catálogo.

### D5 — Propuesta de diagnóstico sobre el `DiagnosisForm` existente

`DiagnosisForm` acepta valores iniciales: ubicación (`PIEZA` + FDI si el hallazgo es `SURFACE` o `TOOTH`; `GENERAL` si
es `BETWEEN_TEETH`, `TOOTH_RANGE` o `ARCH`) y una lista de sugerencias que se muestran como opciones rápidas **encima**
de `Cie10SearchField`, que sigue buscando en todo el catálogo. Elegir una sugerencia llena el código igual que elegirlo
del buscador; el tipo P/D/R lo elige el usuario y la regla del `R` no cambia. Nada se registra hasta "Agregar
diagnóstico". Sin sugerencias, el formulario es el de hoy con la ubicación precargada.

*Ajuste del apply:* "Proponer diagnóstico" **precarga el formulario de diagnóstico de la nota** (con un aviso
"Propuesto desde: …" y "Descartar propuesta") en vez de abrir un diálogo con un segundo `DiagnosisForm`. Dos
formularios a la vez repetían los `id` de sus campos (`ubicacion`, `fdiNumber`, `cie10-search`) y los `<label>` del
diálogo apuntaban a los campos de la nota. Uno solo en la página evita el problema; descartar la propuesta es el
"cerrar sin confirmar" del Scenario.

### D6 — Panel de hallazgos en la nota abierta

`OpenNoteEditor` suma una sección **"Hallazgos del odontograma"** antes de "Diagnóstico":
- **Sin hallazgos** en el odontograma: el panel lo dice ("Sin hallazgos en el odontograma") y la nota carga igual.
- **Pendientes**: los que se ven rojos (`findingColor === RED`), con pieza/cara/hallazgo y, si se atendieron en una nota
  sellada anterior, "Atendido el 12/10/2026" (sale de las notas del paciente que ya trae la pantalla). Botón "Atender".
- **Atendidos en esta consulta**: cada uno con "Proponer diagnóstico" y "Quitar".
- **"Ver los demás hallazgos"**, plegado, con el mismo botón "Atender".
La nota sellada (`SealedNoteCard`) muestra "Hallazgos atendidos" con las descripciones guardadas. Los datos del
odontograma salen de `useDentalChart` y `useDentalFindingCatalog` (ya existen).

### D7 — El descarte cuenta los hallazgos atendidos

`ConsultationNoteService.discard` suma "sin hallazgos atendidos" a la condición de nota vacía, y el frontend lo refleja
en `empty` de `OpenNoteEditor`.

## Risks / Trade-offs

- **[El hallazgo atendido sigue rojo]** → aceptado hasta el corte 2; el panel dice cuándo se atendió para que no parezca
  ignorado.
- **[Descripción escrita por el cliente]** → es texto clínico de la nota como el examen; el servidor valida que el
  hallazgo sea del paciente y que el texto no esté vacío. No se usa para decisiones del sistema.
- **[Sugerencia clínica equivocada]** → el mapa lo validó el odontólogo; el código final siempre lo elige el usuario y
  puede buscar cualquier otro.
- **[Concurrencia con el sellado]** → la guarda de la base (D1) rechaza un vínculo que llegue durante o después del
  sellado, igual que diagnósticos y antecedentes (spec: "Una escritura concurrente no altera la nota que se sella").
- **[`clinical-history` sigue creciendo]** → 19 requirements + 2; si cuesta leerla, partir `consultation-notes` es un
  change aparte (ya anotado en `docs/vision.md`).

## Migration Plan

`V29` crea la tabla, su RLS y su trigger; sin datos que migrar. Despliegue normal (backend antes que frontend). Rollback:
revertir el PR; la tabla queda vacía y sin uso.

## Open Questions

Ninguna bloqueante. Si la regla de sugerencias crece (más tipos o reglas por estado), pasar el mapa a una tabla de base
editable es un change aparte.
