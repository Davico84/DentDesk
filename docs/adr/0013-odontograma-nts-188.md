# ADR-0013: NTS 188 deroga a NTS 150 — el color del odontograma deja de ser fijo por tipo

- **Estado:** Aceptado (decisión); **pendiente de implementar** — corrige `ADR-0012` antes de que `add-dental-chart` se implemente
- **Fecha:** 2026-08-03
- **Decisores:** David Velarde Alvarado
- **Supersede:** `ADR-0012` **D2** (parcialmente) y su catálogo de `FindingType`. El resto de `ADR-0012` sigue vigente.

## Contexto

`ADR-0012` modeló el odontograma apoyándose en la **NTS N°150-MINSA/2019/DGIESP**. Al preparar la validación normativa que ese mismo ADR dejaba pendiente, se descubrió que esa norma **está derogada**:

> La **NTS N°188-MINSA/DGIESP-2022** reemplaza y deroga por completo a la NTS N°150-MINSA/2019/DGIESP, mediante **Resolución Ministerial N°559-2022/MINSA**, publicada en el Diario Oficial El Peruano el **29 de julio de 2022**.

Construir sobre la norma derogada habría generado un **compliance gap crítico**: el odontograma resultante podría no ser válido ante una auditoría de SUSALUD, y el catálogo —que se fija en una migración— habría quedado mal desde el primer día.

El hallazgo llegó **antes de escribir una sola línea de código**, gracias a que `add-dental-chart` puso la validación del catálogo como tarea **bloqueante** previa a la migración (`tasks 0.1`) y a que `ADR-0012` marcó explícitamente sus afirmaciones normativas como *"pendientes de validación documental"*. La disciplina de no afirmar cumplimiento sin evidencia es lo que evitó el daño.

> ⚠️ **Alcance de la evidencia.** Los detalles de NTS 188 que se recogen abajo provienen del **análisis del decisor**, con la cita normativa precisa (RM 559-2022, El Peruano 29/07/2022) — no de la transcripción del texto completo ni de sus anexos gráficos. Sigue pendiente adjuntar el articulado y completar la columna § de `docs/compliance/regulatory-matrix.md`.

## Decisión

### D1 — NTS 188 es la norma vigente del odontograma; NTS 150 queda derogada

Toda referencia normativa al odontograma en el proyecto (ADR, matriz de cumplimiento, artefactos de `add-dental-chart`) apunta a **NTS 188**. Las referencias a NTS 150 se corrigen, y su derogación queda registrada — no se borran en silencio: un auditor debe poder ver que el sistema conoció el cambio y se adaptó.

### D2 — El color deja de ser una propiedad fija del tipo (supersede `ADR-0012` D2)

`ADR-0012` afirmaba que *"la naturaleza de cada tipo queda **fija, sin excepciones**"*, y explicaba los casos de color variable (supernumerario, giroversión) como **contagio** desde otro hallazgo de la misma pieza. **NTS 188 lo contradice**: hay tipos cuyo color depende del **estado del propio hallazgo**, no de otro.

```
Sellante   →  S azul  si está en buen estado  ·  S rojo  si está en mal estado
Prótesis   →  azul    si está en buen estado  ·  rojo    si está en mal estado
```

El principio semántico de `ADR-0012` **sobrevive** —rojo = requiere intervención, azul = no la requiere—; lo que cae es que el tipo lo determine siempre por sí solo. Un sellante en mal estado requiere intervención; el mismo sellante en buen estado, no.

**Los tipos pasan a declarar uno de tres comportamientos:**

| Comportamiento | Color | Ejemplos |
|---|---|---|
| `PATOLOGIA` | siempre **rojo** | caries, superficie desgastada, indicación de exodoncia |
| `DESCRIPTIVO` | siempre **azul** | macrodoncia, microdoncia, implante, ectópica, erupción, diastema, supernumerario |
| `RESTAURACION` | **según su estado** | sellante, prótesis fija, prótesis removible, y toda restauración |

Solo los `RESTAURACION` llevan el campo de **estado** (`BUENO` / `MALO`), y el sistema SHALL exigirlo en esos y rechazarlo en los demás — mismo criterio que ya se aplica al grado de movilidad.

La regla de **contagio** de `ADR-0012` (una pieza con patología pinta sus marcadores en rojo) **sigue vigente**: no se contradice con esto, resuelve un caso distinto.

### D3 — El catálogo se alinea a las siglas oficiales, que son dato normativo del tipo

NTS 188 fija **siglas obligatorias** en el recuadro de cada pieza. Esas siglas son parte del documento legal: deben aparecer **exactamente**, en mayúscula.

Se modelan como un **atributo del tipo**, no como su nombre interno: el enum conserva un identificador descriptivo y **lleva su sigla oficial** como dato. Así el nombre en código puede ser legible y la representación sigue siendo exacta, sin que un renombrado interno altere el documento.

**Cambios respecto del catálogo tentativo de `ADR-0012`:**

| Tipo | Sigla | Comportamiento | Nota |
|---|---|---|---|
| Macrodoncia | `MAC` | descriptivo (azul) | **nuevo** — no estaba |
| Microdoncia | `MIC` | descriptivo (azul) | **nuevo** |
| Implante | `IMP` | descriptivo (azul) | sigla obligatoria, en la pieza reemplazada |
| Superficie desgastada | `DES` | patología (rojo) | **nuevo** — además se dibuja la zona afectada |
| Pieza ectópica | `E` | descriptivo (azul) | **nuevo** |
| Movilidad | `M1`/`M2`/`M3` | descriptivo (**azul**) | ⚠️ `ADR-0012` la tenía en **rojo** — ver Open Questions |
| Sellante | `S` | **restauración** | color según estado; admite caries concurrente en la misma pieza |
| Prótesis fija / removible | — | **restauración** | color según estado |
| Pieza en erupción | — | descriptivo (azul) | representación cambia a **flecha en zigzag** |

El catálogo **completo** se fija recién al contrastar el articulado y los anexos gráficos de NTS 188 (`add-dental-chart` tarea 0.1, sigue bloqueante).

### D4 — Algunos tipos exigen llenar las especificaciones

NTS 188 condiciona el campo de especificaciones al tipo registrado:

- **Movilidad** ⇒ anotar la **clasificación** utilizada.
- **Prótesis** ⇒ anotar el **material**.

Se modela como un atributo más del tipo (`requiereEspecificacion`), y el sistema SHALL rechazar el sellado si un hallazgo que lo exige dejó las especificaciones sin la entrada correspondiente. Es el mismo patrón de validación condicional que ya usan `GuardianRequiredForMinor` y `@ValidDiagnosisLocation`.

### D5 — Los colores del odontograma son normativos, no de marca

NTS 188 fija los valores exactos: **rojo `#FF0000`** y **azul `#0000FF`**.

Esto **supersede la decisión D10 del `design.md` de `add-dental-chart`**, que mandaba derivar todo color de los tokens del tema. El odontograma es la excepción: **no participa del sistema de tematización por clínica ni del modo oscuro**.

Se renderiza **siempre sobre fondo blanco, con los hex exactos**, aunque el resto de la aplicación esté en modo oscuro — se comporta como un documento embebido, no como una superficie de UI. Razón: **lo que el odontólogo ve en pantalla debe ser idénticamente lo que se audita**. Cualquier adaptación por tema o contraste introduce una diferencia entre el documento mostrado y el documento legal.

*Alternativa descartada:* adaptar los colores en pantalla para modo oscuro y usar los exactos solo al exportar. Mejor experiencia diaria, pero exige mantener dos implementaciones sincronizadas y rompe la equivalencia entre lo visto y lo auditado — en un documento médico-legal, esa equivalencia vale más que la comodidad visual.

**Consecuencia de accesibilidad, asumida explícitamente**: al fijar fondo blanco y hex normativos, el contraste del odontograma no se adapta al tema. El resto de la accesibilidad **no se relaja**: cada pieza y superficie sigue siendo enfocable y accionable por teclado, con nombre accesible — el color **nunca** es el único portador de significado (la sigla y la forma también lo comunican, que es justamente lo que la norma exige).

## Consecuencias

- ✅ **El compliance gap se cierra antes de la migración.** El catálogo se fija una sola vez, contra la norma vigente. Descubrirlo después habría costado migración de datos y reportes epidemiológicos inválidos.
- ✅ **`ADR-0012` sobrevive casi entero**: el corte estado/acción, el libro de asientos, el bloque diario como aggregate, el coordinador transaccional, la cronología por expediente y el corte v1/fase 2 no se tocan. Solo cae la parte apoyada en el detalle de la norma derogada.
- ✅ **Valida la disciplina de no afirmar cumplimiento sin evidencia.** `ADR-0012` marcó sus afirmaciones normativas como pendientes de validar y `add-dental-chart` puso el catálogo como tarea bloqueante: por eso el error se encontró en un documento y no en una migración aplicada.
- ⚠️ **El modelo gana dos atributos y un campo**: el comportamiento del tipo (patología / descriptivo / restauración), el `requiereEspecificacion`, y el **estado** (`BUENO`/`MALO`) en los hallazgos de restauración. Todo antes de escribir la migración, así que no hay coste de cambio.
- ⚠️ **El odontograma queda fuera del sistema de tematización.** Es la primera superficie del producto que no respeta la marca de la clínica ni el modo oscuro. Es deliberado y hay que documentarlo donde alguien pueda "corregirlo" por error.
- ⚠️ **13 referencias a NTS 150** repartidas en `ADR-0012`, la matriz de cumplimiento y los artefactos de `add-dental-chart` deben corregirse.

## Alternativas descartadas

- **Editar `ADR-0012` en lugar de escribir uno nuevo** — descartado. `docs/adr/README.md` admite corregir un ADR por error de detalle, pero acá el cambio es material (cae una decisión completa y cambia el modelo). Para una auditoría, ver **cuándo se detectó la derogación y cómo se corrigió** vale más que un documento que aparenta haber estado siempre bien.
- **Esperar al texto completo de NTS 188 antes de decidir nada** — descartado. La derogación está citada con resolución y fecha; que la norma vigente es otra ya es un hecho accionable. Los detalles del catálogo siguen bloqueados por la tarea 0.1, que es donde corresponde.
- **Adaptar los colores normativos al tema y al modo oscuro** — ver D5.
- **Usar las siglas oficiales como nombre del enum** (`MAC`, `IMP`, `DES`) — descartado: acopla el identificador interno a la nomenclatura de una norma que ya demostró que cambia. La sigla es un **atributo** del tipo; si una norma futura la modifica, cambia el dato y no el código que la referencia.

## Open Questions

1. ✅ **La movilidad en azul — CONFIRMADA** (2026-08-03). El análisis normativo de `docs/compliance/nts-188-odontogram-update.md` lo sustenta en el **Anexo N°01**: la escala pasa de romanos rojos (`I`/`II`/`III`, NTS 150) a arábigos azules (`M1`/`M2`/`M3`). Se acata: la movilidad es **descriptiva**, no patológica, a efectos de color. La API debe además **rechazar** la sintaxis romana.

2. ⚠️ **Alcance dual de `DES` (superficie desgastada) — sin resolver.** NTS 188 pide la sigla `DES` en el **recuadro de la pieza** *y* pintar la **cara afectada** en el gráfico. El modelo tiene `scope` de **valor único** (superficie / pieza / entre piezas), así que un tipo que es las dos cosas no encaja. Opciones: (a) que `scope` admita un conjunto, (b) que `DES` se registre como hallazgo de superficie y la sigla del recuadro se **derive** al renderizar. **(b) parece preferible** —no toca el modelo y mantiene la precisión cara por cara que exige la auditoría—, pero hay que confirmarlo contra el Anexo N°01. Resolver en `add-dental-chart` tarea 0.1, antes de la migración.

3. ⚠️ **`IMP` implica pieza ausente — sin formalizar.** NTS 188 vincula el implante a la **pieza reemplazada** e inhabilita sus caras. `ADR-0012` tiene la invariante *"pieza ausente no admite hallazgos de superficie"* (INV-09), pero no dice que `IMP` la dispare. Formalizarlo en el catálogo (tarea 0.1).

4. **El catálogo completo** sigue pendiente del **Anexo N°01** de NTS 188 (`add-dental-chart` tarea 0.1, bloqueante). Los documentos de `docs/compliance/` traducen la norma pero no la reemplazan.
3. **Datos históricos**: no aplica hoy (no hay odontogramas registrados), pero si alguna vez se importan de un sistema previo, el mapeo a las siglas de NTS 188 debe ser explícito y auditable.
5. ✅ **NTS 139 verificada** (2026-08-03) — sigue vigente (RM N°214-2018/MINSA). Pero al leerla aparecieron **seis gaps sobre capacidades ya construidas** (firma digital, filiación completa con Ubigeo, cifrado en reposo, secreto profesional, autoría COP, log de accesos). Están en `docs/compliance/regulatory-matrix.md` §Huecos abiertos y **exceden el alcance de este ADR**: cada uno necesita su propia decisión.

6. ⚠️ **Datos históricos con movilidad en romanos.** No aplica hoy (no hay odontogramas registrados), pero si alguna vez se importan de un sistema previo, el mapeo `I→M1`, `II→M2`, `III→M3` debe ser explícito y auditable.
