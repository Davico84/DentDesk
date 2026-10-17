# Tasks

> Back y front por separado (commits por scope). Antes de tocar el frontend, skill `frontend-guard`. Pantalla
> autenticada: sistema de diseño existente (`docs/frontend.md` §4). Tests derivados de los Scenario del delta.
> Tests de concurrencia con pool propio (nunca `supplyAsync` sin executor).

## 1. Backend — hallazgos atendidos en la nota

- [x] 1.1 Migración `V29`: `consultation_note_finding` (design D1) con FK compuestas a `consultation_note` y `dental_chart_finding`, `UNIQUE (note_id, finding_id)`, `description VARCHAR(300) NOT NULL CHECK (btrim(description) <> '')`, autoría completa, RLS forzada `organization_isolation`, trigger `consultation_note_child_seal_guard` en `BEFORE INSERT OR UPDATE OR DELETE` y `GRANT` defensivo a `odontorisas_app` como `V26`
- [x] 1.2 Entidad + repositorio (con `findByNoteIdInOrderByIdAsc`); servicio para marcar y desmarcar con el bloqueo de la nota abierta como el resto de las escrituras, y la matriz de errores de design D1: 404 nota/hallazgo inexistente, de otra clínica u otro paciente; 409 nota sellada, bloque no sellado o duplicado; 400 descripción inválida
- [x] 1.3 Endpoints `POST /patients/{patientId}/consultation-notes/{noteId}/findings` y `DELETE …/findings/{id}`; `ConsultationNoteResponse` incluye `attendedFindings` (`id`, `findingId`, `description`, `createdAt`, `createdBy`), cargados en bloque en `list` junto con diagnósticos y antecedentes (sin N+1)
- [x] 1.4 `discard` cuenta los hallazgos atendidos como contenido (design D7)
- [x] 1.5 `GET /cie10/suggestions?findingType=&subtype=` con el mapa de design D4; la descripción sale de `cie10_code`
- [x] 1.6 Tests (IT con Postgres real) de cada Scenario: marcar, desmarcar, duplicado, otro paciente, otra clínica (404), nota sellada por API directa (también desde la base: el trigger), marcar concurrente con el sellado, descartar con hallazgos, la nota sellada devuelve las descripciones; sugerencias: caries en dentina → `K021`, tipo sin sugerencia → lista vacía, **cada código del mapa existe en `cie10_code`**
- [x] 1.7 `ContractDriftIT`/OpenAPI al día; `mvn verify` verde

## 2. Frontend — panel de hallazgos y diagnóstico propuesto

- [ ] 2.1 Regenerar el cliente (orval) y hooks de React Query: marcar, desmarcar, sugerencias (con invalidación de la nota)
- [ ] 2.2 Panel "Hallazgos del odontograma" en `OpenNoteEditor` (design D6), con estado vacío ("Sin hallazgos en el odontograma") sin romper la nota: pendientes (`findingColor === RED`) con "Atendido el …" si una nota sellada anterior los marcó, atendidos en esta consulta con "Proponer diagnóstico" y "Quitar", y "Ver los demás hallazgos" plegado
- [ ] 2.3 Descripción del hallazgo (pieza, cara, hallazgo, subtipo, estado) con las etiquetas existentes; al marcar, se envía y se agrega como línea al **Examen** del formulario con `shouldDirty` (sellar queda bloqueado hasta guardar) (design D2, D3)
- [ ] 2.4 `DiagnosisForm` con valores iniciales (ubicación `PIEZA`+FDI o `GENERAL` según la localización) y sugerencias como opciones rápidas encima de `Cie10SearchField`, que sigue buscando en todo el catálogo (design D5)
- [ ] 2.5 `SealedNoteCard` muestra "Hallazgos atendidos" con las descripciones guardadas; `empty` de la nota cuenta los hallazgos atendidos (design D7)
- [ ] 2.6 Tests (Vitest/MSW) de cada Scenario: pendientes primero y demás plegados, "Atendido el …", marcar agrega la línea al examen, quitar, propuesta con la pieza y la sugerencia `K021`, hallazgo entre piezas → general, sin sugerencia → buscador, elegir otro código del buscador, cerrar sin confirmar no registra, nota sellada con descripciones, nota con hallazgos no ofrece descartar
- [ ] 2.7 e2e: el panel a 375 y 1280 px sin desplazamiento lateral

## 3. Cierre

- [ ] 3.1 `pnpm validate` (con `--maxWorkers=8` en local) y `mvn verify` verdes; captura para el usuario antes de commitear; prueba manual en local
- [ ] 3.2 `openspec validate update-consultation-note-findings --strict` y medir a mano los requirements (incluido el MODIFIED) ≤ 500 caracteres
- [ ] 3.3 Al archivar: `docs/domain.md` (la nota referencia hallazgos del odontograma) y `docs/vision.md` (marcar construido)
