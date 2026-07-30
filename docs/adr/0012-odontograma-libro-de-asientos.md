# ADR-0012: Odontograma como libro de asientos, partido en inicial y evolución

- **Estado:** Aceptado (decisión); **pendiente de implementar** — capacidad nueva, aún no propuesta
- **Fecha:** 2026-07-30
- **Decisores:** David Velarde Alvarado

## Contexto

`docs/domain.md` tenía un **stub** de `dental-chart` desde el inicio del proyecto: entidades `Tooth` (catálogo de piezas) + `ToothCondition` (estado por paciente/pieza/superficie), con `status: EXISTING | PLANNED | DONE`, una FK opcional `→ Treatment`, y una **pregunta abierta anotada**: el código de color acordado usa dos colores (rojo = por hacer, azul = realizado) pero el modelo tiene tres estados — faltaba decidir si `EXISTING` compartía color con `PLANNED`.

Al modelar el dominio en profundidad (sesión de exploración, 2026-07-30) contra la normativa peruana (**NTS N°139-MINSA** para la historia clínica y su odontograma anexo; **NTS N°150-MINSA/2019/DGIESP**) y contra lo ya construido (`clinical-history`, archivada el mismo día), aparecieron cuatro problemas que el stub no resolvía:

1. El `status` del stub **viola el corte estado/acción** que `docs/vision.md §2` ya había decidido para separar `dental-chart` de `treatments`.
2. La pregunta abierta del color **parte de una premisa falsa** (asume que el color mapea al `status`).
3. Dos requisitos del MINSA **parecían contradictorios**: el odontograma de evolución como lienzo vivo que se actualiza al completar tratamientos, y como serie de documentos fechados que se comparan entre sí.
4. El stub **no podía localizar** dos condiciones que la norma exige registrar (diastema y supernumerario no tienen número FDI propio: viven *entre* piezas).

## Decisión

> **Alcance de este documento.** Esto es un ADR: registra la **decisión y su porqué**. Las reglas SHALL detalladas (requirements + scenarios) **no** viven aquí — se generan como el change OpenSpec **`add-dental-chart`** y se validan con `openspec validate --strict`.

### D1 — El odontograma registra estado; `PLANNED`/`DONE` salen del aggregate

`docs/vision.md §2` ya decidió: *"el odontograma es el **estado** de la boca; el tratamiento es la **acción** que lo cambia"*, y `treatments` **depende de** `dental-chart`. El `status` del stub rompía ese corte en tres puntos:

- `PLANNED` es una **intención**, no un estado: una pieza no cambia porque alguien planificó tratarla. Pertenece a `treatment-plans`.
- `DONE` es **historia de acción**. Su *resultado* sí es estado (hay una obturación) — pero eso ya lo dice el tipo de condición.
- La FK `ToothCondition → Treatment` **invertía la dependencia declarada**: haría que `dental-chart` dependa de `treatments`.

La transición real no es de `status` sobre una fila que muta, sino de **tipo** sobre dos hechos distintos: la caries se **cierra** y la obturación se **abre**. El tratamiento que causó la transición vive en `treatments`.

Confirmado con el decisor: en la práctica peruana el plan de tratamiento se maneja en un módulo de planes (texto/listas), **no repintando el odontograma inicial**, que queda como evidencia estática.

### D2 — El color es la naturaleza del tipo, más una regla de render por contagio

La pregunta abierta del stub se **disuelve** en vez de responderse: el color no mapea al workflow, mapea a la naturaleza clínica de la condición.

- **Rojo** = requiere intervención (caries, fractura, movilidad, extruido, intruido, pieza no recuperable).
- **Azul** = no hay patología activa (obturación, corona, endodoncia, implante, sellante, ausente, en erupción, diastema, supernumerario sano).

La formulación **no** es "patología vs restaurado" — es "requiere intervención vs no la requiere". Solo así entran los casos que no son ninguna de las dos: una pieza **ausente** se dibuja con X **azul** (ya no hay nada que tratar), mientras que una pieza aún en boca pero no recuperable lleva X **roja** (indicación de exodoncia).

Dos tipos parecían tener color variable —supernumerario *"azul si está sano, rojo si tiene caries"* y giroversión *"azul o roja"*—. No lo tienen: el color depende de **otro hallazgo en la misma pieza**. Eso lo saca del dominio y lo convierte en una **regla de renderizado**: *una pieza con cualquier hallazgo que requiere intervención pinta todos sus marcadores en rojo*. La naturaleza de cada tipo queda **fija**, sin excepciones, y no se almacena color en ningún lado.

### D3 — Libro de asientos (append-only), no Event Sourcing

Los tres requisitos —lienzo vivo, comparación entre fechas, nada se sobrescribe— se cumplen con un **libro de asientos**: cada hallazgo es una fila inmutable con `abiertoEn` y `cerradoEn`. Nunca hay `UPDATE` sobre el estado clínico.

- *Estado hoy* = hallazgos sin cerrar.
- *Estado en una fecha* = abiertos antes de esa fecha y no cerrados aún en ella.
- *Comparar año 1 vs año 2* = dos proyecciones del mismo libro.

Se **descarta Event Sourcing** (ver alternativas): no aporta ninguna garantía clínica ni legal adicional sobre el libro de asientos, y exige infraestructura que el proyecto no tiene.

### D4 — El bloque diario es el Aggregate Root; el odontograma inicial es el primer bloque

Por norma, el tramo de evolución **se cierra y congela al terminar la atención** — no puede quedar abierto por meses. Eso da el límite natural del aggregate:

```
BloqueOdontograma(paciente, fecha, tipo, estado)      ← AGGREGATE ROOT

  INICIAL    [SELLADO junto con la consulta de admisión]
  EVOLUCION  [SELLADO al "Cerrar Cita"]
  EVOLUCION  [ABIERTO — el único mutable]
```

Con bloques diarios, **el odontograma inicial deja de ser un caso especial**: es simplemente el primer bloque, de tipo `INICIAL`. Un solo tipo de aggregate en vez de dos, acotado por diseño (un bloque = un día de trabajo, no 15 años de retención), con un invariante natural: **como máximo un bloque abierto por paciente**.

El estado consolidado se obtiene plegando los bloques; la comparación entre fechas, plegando hasta la fecha pedida.

### D5 — Coordinación por caso de uso transaccional, no por bus de eventos

El odontograma inicial y la consulta de admisión **sellan juntos**: ninguno de los dos puede modificarse de forma independiente después. Ese invariante cruza dos capacidades.

Se descarta el bus de eventos (ver alternativas) y se adopta un **caso de uso coordinador** por encima de ambos módulos, en **una sola transacción**: si falla el sellado del odontograma, hace rollback también del cierre de la consulta. Ninguno de los dos módulos depende del otro; el orquestador depende de los dos.

Esto **respeta la objeción de acoplamiento**: la geometría dental (piezas, caras, notación) se queda en `dental-chart` y no contamina `clinical-history`. Solo cambia el mecanismo de coordinación.

### D6 — El cierre diario es explícito en v1; el automático queda diferido

La norma admite dos disparadores: el botón "Cerrar Cita" y el corte automático al terminar el día.

**El corte automático no se puede construir hoy**: la regla ArchUnit `async_must_not_be_used_until_tenant_context_is_propagated` prohíbe `@Async` **y `@Scheduled`**, porque un hilo de fondo no hereda `IdentityContext`/`OrganizationContext` (ThreadLocal). Introducirlo exige cablear antes el `TenantContextTaskDecorator`. Además el corte tendría que respetar el huso de cada clínica (`OrganizationSettings.timezone`), no el del servidor.

v1: **solo cierre explícito**. El automático es trabajo posterior con su propia infraestructura. La inmutabilidad legal no se pierde: se garantiza con D7.

### D7 — Cronología lineal forzada **por expediente**, validada en el servidor

No puede existir un bloque abierto de una fecha anterior cuando se intenta registrar en el día presente. La precondición es del **caso de uso**, no de la interfaz:

```
IF paciente.tieneBloqueAbiertoDeFechaAnterior() THEN
    rechazar — el expediente tiene una atención sin cerrar
```

**Alcance por paciente, no por profesional.** La NTS N°139-MINSA exige secuencia cronológica ininterrumpida del **cuerpo de la historia clínica de ese paciente**; lo que el odontólogo haga con otros pacientes no altera la validez del documento. Un alcance global además rompería la operación real: en una clínica multi-odontólogo, el olvido de un colega detendría la agenda entera.

**El rechazo vive en el servidor**, no solo en la pantalla de contingencia. Mismo criterio que `clinical-history` ya cumple y testea (*"rechazar la edición de la información sellada aun por API directa"*): un bloqueo visual no es un bloqueo.

### D8 — La localización tiene tres formas, y la precisión es cara por cara

El stub solo contemplaba pieza y superficie. La norma exige registrar **diastema** y **diente supernumerario**, que no tienen número FDI propio: se ubican *entre* dos piezas.

- `EnSuperficie(pieza, cara)` — caries en 36-O
- `EnPieza(pieza)` — ausente 46, endodoncia 27
- `EntrePiezas(a, b)` — diastema 11-21, supernumerario 12-13

Esto elimina la entidad intermedia `RegistroPieza` que agrupaba por FDI (un supernumerario no entraría en ningún grupo): los hallazgos cuelgan directo del bloque, con localización rica.

La precisión es **estrictamente cara por cara**: en odontología el procedimiento se registra y se cobra por extensión de superficies (resina monocara vs. policara), y un asiento impreciso invalida el documento ante auditoría de SUSALUD o del MINSA. Si el año siguiente aparece caries en otra cara de la misma pieza, el sistema la distingue sin ambigüedad.

Algunos hallazgos llevan además un **grado** (movilidad I/II/III); el tipo declara si lo exige, en vez de multiplicar tipos por grado.

### D9 — La capacidad se parte: v1 solo el odontograma inicial

El modelo completo da ~10 requirements, por encima de la guía de granularidad del proyecto (`docs/vision.md §5`, ~7-8).

- **`add-dental-chart` (v1)**: odontograma **inicial** — crear, registrar hallazgos con las tres localizaciones, catálogo de tipos, especificaciones obligatorias, sellado atómico con la admisión, aislamiento por clínica.
- **Fase 2** (`update-dental-chart`, **junto con** la evolución SOAP de `update-clinical-history`): bloques diarios, asientos de apertura/cierre, cronología lineal, proyección a una fecha, y la operación que `treatments` consumirá.

Las dos fases 2 se construyen **juntas** porque son el **mismo momento clínico**: el "Cerrar Cita" sella la nota SOAP del día y el bloque del odontograma en una sola transacción. Separarlas obligaría a montar la coordinación del cierre diario dos veces.

Es además el mismo corte que ya usó `clinical-history` (documento inicial primero, evolución en fase 2), con la simetría a la vista.

## Modelo resultante

> **Instantánea congelada al momento de decidir.** El modelo **vivo** se consolida en `docs/domain.md` al archivar `add-dental-chart` (gobernanza, `docs/vision.md §4`). Si algún día divergen, manda `domain.md` — este ADR registra qué se decidió **entonces**, no qué está construido **hoy**.

### Glosario

El término "libro de asientos" no es habitual en software clínico. Antes del modelo, el vocabulario:

| Término | Significado |
|---|---|
| **Bloque** (*block*) | El odontograma de un paciente **en una fecha**. Es la unidad que se sella. Uno es `INITIAL` (admisión); el resto, `EVOLUTION` (controles). |
| **Asiento** (*entry*) | Un movimiento dentro de un bloque: **abre** o **cierra** un hallazgo. Nunca se edita ni se borra. |
| **Hallazgo** (*finding*) | Una condición clínica concreta: qué (tipo) y dónde (localización). Ej. caries en 36-O. |
| **Abrir** un hallazgo | Registrar que la condición **empieza a estar presente**. |
| **Cerrar** un hallazgo | Registrar que **dejó de estar presente** (se trató, se resolvió). No lo borra: queda con fecha de cierre. |
| **Sellar** un bloque | Congelarlo. Pasa a solo lectura, para siempre. |
| **Proyectar** | Calcular el estado de la boca **a una fecha**, plegando los asientos hasta ahí. |

### Semántica de proyección

Todo el ADR se reduce a cuatro reglas de conjuntos. Nada de esto se almacena: se calcula.

```
EstadoActual      = { h : abierto(h) ∧ ¬cerrado(h) }

EstadoEn(X)       = { h : aperturaDe(h) ≤ X  ∧  (¬cerrado(h) ∨ cierreDe(h) > X) }

Comparar(X, Y)    = EstadoEn(X) △ EstadoEn(Y)        (diferencia simétrica)
                    → resueltos: en X y no en Y · nuevos: en Y y no en X

ComoLlego         = EstadoEn(fecha del bloque INITIAL)
```

La comparación entre fechas que exige el MINSA es la tercera regla; el "lienzo vivo" es la primera. Ambas del mismo libro, sin datos duplicados ni diffs almacenados que puedan desincronizarse.

### Aggregate Root — `DentalChartBlock`

| Campo | Notas |
|---|---|
| `id` | |
| `organizationId` | `@TenantId` + **RLS propia**, no heredada por FK (la RLS de Postgres no es transitiva — lección pagada en `add-clinical-history`) |
| `patientId` | referencia por id, sin navegación de objeto |
| `type` | `INITIAL` \| `EVOLUTION` |
| `status` | `OPEN` \| `SEALED` |
| `examinedOn` | fecha del examen |
| `sealedAt` | `null` mientras esté `OPEN` |
| `specifications` | texto libre — **obligatorio por norma**; recoge lo que se mide pero no se dibuja (recesión gingival) |
| `entries[]` | los asientos |

⚠️ **Deliberadamente sin `clinicalHistoryId`.** Sería la FK que D5 evita. Y es redundante: `HistoriaClinica` es única por paciente y el bloque `INITIAL` también, así que `patientId` ya correlaciona ambos. Mismo criterio que ADR-0011 usó para el diagnóstico CIE10 (ubica por número FDI, no por fila del odontograma).

### Entidad — `DentalChartEntry` (el asiento)

| Campo | Notas |
|---|---|
| `movement` | `OPEN` \| `CLOSE` |
| `finding` | el hallazgo que abre o cierra |
| `source` | `EXAMINATION` \| `TREATMENT(id)` — de dónde vino el movimiento |
| `recordedAt` / `recordedBy` | **inmutables** (autoría; el vínculo a la colegiatura COP llega con `staff`) |

### Value Objects

| VO | Contenido | Nota |
|---|---|---|
| **`Location`** | `sealed` con tres variantes | ver abajo |
| **`ToothNumber`** | número FDI + cuadrante, posición y dentición **derivados** | ya existe como `FdiTooth` |
| **`ToothSurface`** | M · D · V · P/L · O/I | cuáles aplican depende de la pieza |
| **`Finding`** | `Location` + `FindingType` + `grade?` | lo que se abre y se cierra |
| **`Dentition`** | `PRIMARY` \| `MIXED` \| `PERMANENT` | **derivada** de `Patient.birthDate`, no almacenada |

```
sealed interface Location
   ├── SurfaceLocation(tooth, surface)    caries en 36-O
   ├── ToothLocation(tooth)               ausente 46, endodoncia 27
   └── BetweenTeethLocation(a, b)         diastema 11-21, supernumerario 12-13
```

Polimórfico y no un VO con campos nullable: cada variante lleva solo lo suyo, y `sealed` (Java 25) hace que el compilador exija agotar los tres casos. Elimina la entidad intermedia que agrupaba por pieza — un supernumerario no pertenece a ninguna pieza.

> **Dentición mixta no necesita caso especial**: como todo se localiza por FDI, que coexistan piezas `5x-8x` (temporales) con `1x-4x` (permanentes) sale del modelo sin reglas extra.

### Enumeración — `FindingType`

Tres atributos por valor, que convierten tres invariantes en estructura en vez de reglas sueltas:

- `nature`: `REQUIRES_INTERVENTION` (rojo) \| `NO_ACTIVE_PATHOLOGY` (azul) — **fija, sin excepciones** (D2)
- `scope`: `SURFACE` \| `TOOTH` \| `BETWEEN_TEETH` — determina la variante de `Location` admitida
- `requiresGrade`: solo movilidad (I/II/III)

| Tipo | nature | scope | Notación |
|---|---|---|---|
| `CARIES` · `FRACTURE` | 🔴 | superficie | — |
| `NON_RESTORABLE` | 🔴 | pieza | X roja |
| `MOBILITY` (grado I-III) | 🔴 | pieza | romano rojo |
| `EXTRUDED` · `INTRUDED` | 🔴 | pieza | ↑ / ↓ roja |
| `FILLING` · `SEALANT` | 🔵 | superficie | — |
| `CROWN` · `ROOT_CANAL` · `IMPLANT` | 🔵 | pieza | — |
| `MISSING` | 🔵 | pieza | X azul |
| `ERUPTING` | 🔵 | pieza | ↑ azul |
| `ROTATION` | 🔵 | pieza | ↻ (rojo por contagio) |
| `SUPERNUMERARY` | 🔵 | entre piezas | S en círculo |
| `DIASTEMA` | 🔵 | entre piezas | \|\| azul |

> ⚠️ **Catálogo tentativo** — sale de la sesión de modelado, no de una lectura exhaustiva de la norma. **Revisar contra la práctica clínica antes de fijarlo en la migración**: un tipo que falte después cuesta migración de datos.

### Invariantes

**Duros** — el aggregate rechaza:

| | Invariante |
|---|---|
| **INV-01** | Como máximo **un bloque `OPEN`** por paciente |
| **INV-02** | Exactamente **un bloque `INITIAL`** por paciente, y es el más antiguo |
| **INV-03** | Un bloque `SEALED` es **inmutable** — rechazado **en el servidor**, no solo en la UI |
| **INV-04** | No se abre un bloque nuevo si el paciente tiene uno `OPEN` **de fecha anterior** (D7) |
| **INV-05** | `examinedOn` de cualquier bloque ≥ `examinedOn` del `INITIAL` |
| **INV-06** | No se cierra un hallazgo que **no está abierto** |
| **INV-07** | No se abre un hallazgo **idéntico al que ya está abierto** |
| **INV-08** | Todo asiento `CLOSE` referencia un `OPEN` existente — **no hay cierres huérfanos** |
| **INV-09** | Una pieza con `MISSING` **no admite hallazgos de superficie** (contradicción física) |
| **INV-10** | `type.scope` ↔ variante de `Location` (estructural vía el enum) |
| **INV-11** | `type.requiresGrade` ⇔ `grade` presente |
| **INV-12** | `BetweenTeethLocation`: piezas **adyacentes reales**, en orden canónico (evita `11-21` y `21-11` como duplicados) |
| **INV-13** | La superficie debe ser **aplicable a esa pieza** (un incisivo no tiene oclusal) |
| **INV-14** | Todo `ToothNumber` ∈ conjunto FDI real (reusa `FdiTooth`, no redefine) |

**Blandos** — avisan, no bloquean:

| | Invariante |
|---|---|
| **INV-15** | Coherencia **dentición ↔ edad**. Un paciente de 40 años con hallazgo en la pieza 51 es raro pero clínicamente posible (temporal retenida): bloquearlo impediría registrar un caso real. Mismo criterio que el teléfono compartido en `update-patients` (avisa, no bloquea). |

### Regla de renderizado (fuera del dominio)

Una pieza con **cualquier** hallazgo `REQUIRES_INTERVENTION` pinta **todos** sus marcadores en rojo. Es lo que explica el color variable de `SUPERNUMERARY` y `ROTATION`: no es un dato del hallazgo, es contagio desde otro hallazgo de la misma pieza. No se almacena.

### Hechos del dominio

> ⚠️ **No son Domain Events, y no se implementan como tales.** El proyecto no tiene bus de eventos, y la regla ArchUnit `async_must_not_be_used_until_tenant_context_is_propagated` rompe la build ante `@Async`. Se listan porque **el dominio los reconoce como momentos significativos** —útil para nombrar operaciones y escenarios de prueba—, no como decisión de adoptar el patrón. Adoptarlo exigiría **su propio ADR** (ver alternativas descartadas).

| Hecho | Cuándo |
|---|---|
| Bloque creado | se inicia el examen |
| Hallazgo abierto | se registra una condición |
| Hallazgo cerrado | la condición deja de estar presente (examen o tratamiento) |
| Bloque sellado | "Cerrar Cita", o coordinado con la admisión si es el `INITIAL` |

**No existe "bloque reabierto"**: el sellado es unidireccional (D4), igual que `DRAFT→COMPLETED` en `clinical-history`. Admitir reapertura tiraría abajo la garantía de libro de actas que justifica todo el modelo.

## Consecuencias

- ✅ **Cierra la pregunta abierta del stub** sobre el color, disolviéndola: no había que elegir color para `EXISTING`, había que sacar el `status` del modelo.
- ✅ **El corte estado/acción de `vision.md §2` deja de estar solo en prosa** y pasa a estar en el modelo: sin `PLANNED`/`DONE` y sin FK a `Treatment`, `dental-chart` no puede depender de `treatments` por accidente.
- ✅ **Un solo aggregate** (`BloqueOdontograma`) cubre inicial y evolución, acotado por día. Evita el aggregate gigante que habría salido de modelar "el odontograma del paciente" sin límite temporal, relevante con la retención legal de 15 años.
- ✅ **Cumple los dos requisitos MINSA que parecían contradictorios** sin sacrificar ninguno: el libro de asientos es lienzo vivo (plegar todo) y serie comparable (plegar hasta una fecha) a la vez.
- ✅ **Sin infraestructura nueva**: ni event store, ni bus de eventos, ni scheduler. El proyecto sigue sin `@Async`/`@Scheduled`, y la regla ArchUnit que lo vigila permanece intacta.
- ⚠️ **Invalida parte del stub de `docs/domain.md`**: `ToothCondition.status`, la FK `→ Treatment` y la entidad `Tooth` como tabla dejan de aplicar. `Tooth` en particular **ya existe como código** (`FdiTooth`, construido con `add-clinical-history`): son 52 valores derivables por regla, no un catálogo con filas — a diferencia de CIE10 (14 939 filas, que sí es tabla). El stub se actualiza al archivar `add-dental-chart`.
- ⚠️ **El asiento automático desde `treatments` no se puede construir todavía**: esa capacidad es fila 7 del roadmap y no está propuesta. `dental-chart` expondrá la operación con sus reglas, sin consumidor — mismo patrón ya usado con `enabledForTreatment` en `clinical-history` (se construyó la señal antes que su consumidor, para no rehacer el contrato).
- ⚠️ **El cierre automático a fin de día queda fuera de v1** por el bloqueo de ArchUnit. Un bloque que quedó abierto es visible y el odontólogo lo cierra; D7 garantiza que no se pueda escribir el día siguiente sin cerrarlo.
- ⚠️ **La fase 2 acopla dos capacidades en una transacción** ("Cerrar Cita" sella nota SOAP + bloque del odontograma). Es deliberado, y por eso se decide construirlas juntas en vez de secuencialmente.

## Alternativas descartadas

- **Event Sourcing para el libro de evolución** — se consideró y se descartó. Cumple los mismos tres requisitos que el libro de asientos (append-only, lienzo vivo, proyección a cualquier fecha) pero exige event store, motor de replay, proyecciones, versionado de eventos y estrategia de snapshots. No aporta ninguna garantía clínica ni legal adicional. Costo específico en este proyecto: cada evento tendría que llevar `organizationId` y el replay bajo RLS es incómodo — justo el mecanismo cuyo mal entendimiento ya costó una auditoría completa en `add-clinical-history`. Si algún día aparece una razón que solo el Event Sourcing resuelva, se migra; el camino inverso es mucho más caro.
- **Bus de eventos de dominio para coordinar el sellado** — descartado por dos razones independientes. (1) La regla ArchUnit `async_must_not_be_used_until_tenant_context_is_propagated` **rompe la build** ante `@Async`. (2) Un evento síncrono pasaría la regla, pero sería una llamada a método con indirección encima: mismo hilo, misma transacción, control de flujo implícito y más difícil de testear — sin el desacoplamiento que justificaría el patrón. Adoptar eventos de dominio ameritaría su propio ADR, no colarse por la puerta de atrás de esta capacidad.
- **Llamada directa `clinical-history → dental-chart`** para sellar — descartada: invierte la dirección de dependencia declarada en el roadmap (`dental-chart` es fila 6 y depende de `clinical-history`, fila 4). El caso de uso coordinador evita la inversión sin introducir eventos.
- **Mover el odontograma inicial dentro de `clinical-history`** — se consideró seriamente, porque la regla clásica de DDD dice que lo que sella junto pertenece al mismo aggregate. Se descarta por acoplamiento conceptual: la geometría dental (52 piezas, caras, notación FDI, tipos de condición) no pertenece al historial médico general. El invariante "sellan juntos" se protege en el caso de uso coordinador con transacción atómica, una desviación pragmática y consciente de la regla del aggregate.
- **Bloqueo cronológico por profesional o global de la clínica** — descartado. La NTS N°139-MINSA ata la secuencia cronológica al expediente del paciente, no a la agenda del profesional. Un alcance global detendría la clínica entera a la mañana siguiente por un olvido, y en un escenario multi-odontólogo castigaría a quien no dejó el bloque abierto.
- **Bloqueo cronológico solo en la interfaz** — descartado: repetiría un error que `clinical-history` ya corrigió con un scenario y un test dedicados (*"rechazar la edición de la información sellada aun por API directa"*). Una pantalla de contingencia es cortesía de UX; la garantía es del servidor.
- **Color por instancia del hallazgo** (guardar rojo/azul en cada fila) — descartado: los dos casos que lo sugerían (supernumerario, giroversión) resultaron ser contagio desde otro hallazgo de la misma pieza. Guardarlo sería almacenar un dato derivable y arriesgar que quede desincronizado.
- **Un tipo por grado de movilidad** (`MOVILIDAD_I`, `MOVILIDAD_II`, `MOVILIDAD_III`) — descartado por combinatoria innecesaria: el grado es un atributo del hallazgo, y el tipo declara si lo exige.
- **Un documento hermano `dental-chart-domain-model.md`** con el modelo completo (aggregate, entities, VOs, policies, events, invariants, lifecycle, glossary) — propuesto en revisión y descartado. La estructura es buena, la ubicación no: sería la **tercera** copia del mismo modelo (este ADR + `docs/domain.md` + el nuevo), contra la regla de una sola fuente de verdad que el proyecto aplica en `CLAUDE.md`, en el propio `docs/domain.md` (*"sería duplicarlo, y dos fuentes acaban divergiendo"*) y en los skills `frontend-guard` y `commit`. Además **el hogar ya existe**: `docs/domain.md` se define a sí mismo como *"el modelo conceptual... documento vivo que crece a medida que cada capacidad se especifica y construye"*. La estructura pedida se cubre en dos lugares que ya existen: la sección **Modelo resultante** de este ADR (instantánea congelada de la decisión) y `docs/domain.md` (modelo vivo, consolidado al archivar). La idea de que un documento así *"alimentaría automáticamente ADR, spec y implementación"* no se sostiene: serían cuatro lugares sincronizados a mano.
- **Documentar los cambios de estado como "Domain Events"** — descartado el **nombre**, no el contenido. Los momentos significativos del dominio se listan en Modelo resultante como *hechos*, pero llamarlos Domain Events en un ADR aceptado dejaría entender que el patrón está decidido, cuando la regla ArchUnit lo bloquea y esta misma decisión (D5) lo descartó. La lista propuesta incluía además un `DentalChartReopened` que **contradice el modelo**: el sellado es unidireccional (D4), y admitir reapertura tiraría abajo la garantía de libro de actas que justifica todo el diseño.
- **Construir toda la capacidad de una vez** (~10 requirements) — descartado por la guía de granularidad de `docs/vision.md §5` y porque obligaría a montar la coordinación del cierre diario dos veces: una acá y otra al llegar la nota SOAP de `update-clinical-history`.
