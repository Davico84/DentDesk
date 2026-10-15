# Design

## Context

Ver `proposal.md` (Why). Estado actual observado:

- `Odontogram.tsx` ya distingue cinco destinos de click y llama a `onSelectLocation` con `TOOTH` (número de la pieza),
  `SURFACE` (cara), `BETWEEN_TEETH` (espacio entre piezas, `aria-label "Entre las piezas …"`) y `ARCH` (botones "Arco
  superior/inferior" y el carril del arco); el rango es un modo (`rangeMode`, botón "Registrar rango") con ancla interna
  (`rangeStart`) y su propia ayuda (`RangeModeHint`). Arco y rango tienen tooltip; pieza, cara y espacio, no.
- `FindingDialogForm` ofrece en un `SelectField` solo los tipos cuyo `scope` coincide con la localización tocada. El
  catálogo (`DentalFindingCatalogEntryResponse`) ya trae `scope` por tipo: SURFACE (6 tipos: caries, restauraciones,
  sellante…), TOOTH (22: corona, tratamiento de conducto, pieza ausente…), BETWEEN_TEETH (4: diastema, fusión,
  supernumeraria, transposición), TOOTH_RANGE (3: prótesis parcial fija y removible, aparato fijo), ARCH (3: edéntulo
  total, prótesis completa, aparato removible).
- No hay librería de recorridos guiados; el estado de cliente persistido usa Zustand `persist` (`useSidebarStore`).
- La búsqueda sin tildes ya existe en `modules/staff/utils/staffFilters.ts` (`normalizeForSearch`).

## Goals / Non-Goals

**Goals:**
- Que el usuario aprenda **en el gráfico real** qué tocar para cada localización, y que el diálogo lo saque del callejón
  cuando busca un hallazgo que no va en lo que tocó.
- Que los ejemplos y las reglas salgan del catálogo: si el catálogo cambia, la guía no queda desactualizada.

**Non-Goals:**
- Una librería de tours ni un sistema de onboarding general: el componente genérico cubre este caso y queda
  disponible, pero no se adoptan otras pantallas en este change.

## Decisions

### D1 — Recorrido guiado propio, sin librería

Componente genérico `core/components/GuidedTour`: recibe `steps` (`{ anchor: () => Element | null, title, body }`),
oscurece la pantalla con una capa que deja un **recorte** alrededor del ancla (rectángulo de `getBoundingClientRect`,
también para elementos SVG), y muestra una tarjeta junto al recorte con "Paso n de N", Anterior / Siguiente / Cerrar.
Antes de resaltar, lleva el ancla a la vista con `scrollIntoView({ block: "nearest", inline: "center" })` (el
odontograma se desliza dentro de su tarjeta en celular, `docs/frontend.md` §4.3) y recalcula al cambiar el tamaño o
desplazar. El `scroll` se escucha **en fase de captura** (`window.addEventListener("scroll", fn, true)`): el
desplazamiento del contenedor del odontograma no burbujea hasta `window`. Teclado: ← / → / Esc; foco atrapado en la
tarjeta; región `aria-live` con el paso; **al cerrar o terminar, el foco vuelve** a quien abrió la guía (el botón, o el
primer elemento enfocable del odontograma si se abrió sola). Respeta `prefers-reduced-motion` (sin transición del
recorte).

**La guía no deja interactuar con lo que hay debajo**, tampoco a través del recorte: la capa intercepta todos los
eventos de puntero (el recorte es solo visual). Un click en la pieza resaltada no abre el diálogo de hallazgo
(spec: la guía no registra ni modifica nada).

- *Alternativas descartadas:* `react-joyride` / `driver.js`. Suman una dependencia fuera del stack cerrado
  (`docs/architecture.md` §3) y estilos propios que no siguen los tokens del tema de cada clínica (`docs/frontend.md`
  §4.5). El caso necesita poco: un recorte, una tarjeta y teclado.

### D2 — Anclas en el odontograma

`Odontogram` marca los destinos con `data-guide="…"`: el número y la cara oclusal de una pieza de referencia, un espacio
entre piezas, el botón de arco y el de rango. Pieza de referencia: **16** (permanente superior; siempre está en el
gráfico). La guía no inventa geometría: resalta los elementos que de verdad reciben el click.

### D3 — Pasos y ejemplos desde el catálogo

Pasos: introducción → cara → pieza completa → entre piezas → arco → rango → "¿no encuentras el hallazgo?" (apunta al
diálogo, sin abrirlo). Cada paso de localización nombra hasta **3** tipos del catálogo con ese `scope`
(`findingTypeLabel`), en el orden del catálogo. Si un `scope` no tiene tipos, su paso se omite.

### D4 — "Ya la vio", por usuario y navegador

Store Zustand persistido `useGuidanceStore` (`seen: Record<userId, true>`), mismo patrón que `useSidebarStore`. La
guía se abre sola **solo con el bloque ya cargado y abierto**: `block != null && block.status === "OPEN" && !seen &&
!dialogLocation`. Mientras React Query carga, `block` es `undefined` y `block?.status === "SEALED"` daría `false`: sin
esta condición, se abriría un instante sobre un odontograma sellado. Se marca vista al terminarla o cerrarla. Lectura y escritura protegidas (sin `localStorage` disponible, la guía se comporta como no vista y no rompe).
Es una comodidad del navegador: otro equipo la vuelve a mostrar una vez, lo que se acepta.

### D5 — Orientación en el diálogo

Bajo el selector de tipo, el enlace "¿No encuentras el hallazgo?" despliega un buscador sobre **todos** los tipos del
catálogo (nombre sin tildes; `normalizeForSearch` pasa de `staff` a `core/utils`). Cada resultado muestra dónde se
registra. Al elegir uno:

| Tocado → alcance del elegido | Resultado |
|---|---|
| mismo alcance (tiene precedencia sobre las filas siguientes) | se elige en el selector |
| SURFACE (pieza N) → TOOTH | "Registrarla en la pieza N" → reabre el diálogo en la pieza N, tipo elegido |
| SURFACE o TOOTH (pieza N) → ARCH | "Registrarla en el arco superior/inferior" (`isUpperArchTooth(N)`) |
| SURFACE o TOOTH (pieza N) → TOOTH_RANGE | "Empezar un rango desde la N" → activa el modo rango con N como primera pieza; al elegir la última, el diálogo abre con el tipo elegido |
| TOOTH → SURFACE | explicación: tocar una cara de la pieza N (no se deduce cuál) |
| otro alcance → BETWEEN_TEETH | explicación: tocar el espacio entre las dos piezas |
| BETWEEN_TEETH, ARCH o TOOTH_RANGE → otro | explicación de qué tocar (no hay una única pieza de la que deducir) |

Nada cambia sin el click del usuario en la acción ofrecida. La intención pendiente (`{ type, location }` o
`{ type, rangeStart }`) la guarda `DentalChartEditor`, que ya orquesta el diálogo y el modo rango; `Odontogram` recibe
un `initialRangeStart` opcional.

- **Abrir el destino directo en el formulario.** `FindingDialog` hoy arranca en la **lista** si la localización ya
  tiene hallazgos (`editing === undefined`), y la preselección se perdería. Recibe un `initialType` opcional: con él,
  arranca en modo creación (`editing = null`) aunque haya hallazgos, y `FindingDialogForm` lo usa como valor inicial
  de `type`.
- **Ciclo de vida de la intención de rango.** Se descarta al salir del modo rango por cualquier vía (botón "Cancelar
  rango", Escape que suelta el ancla, cambio de pantalla) y al consumirse (se abre el diálogo con la última pieza). Si
  el usuario re-ancla en otra pieza (click en otra fila), la intención sigue y el rango empieza en la nueva ancla. Un
  "Registrar rango" posterior, sin intención, abre el diálogo como hoy.

### D6 — Dónde vive cada pieza

- `core/components/GuidedTour.tsx` (+ su capa y tarjeta en archivos propios, `react/no-multi-comp`): genérico, no
  conoce el odontograma.
- `modules/dental-chart/guidance/`: pasos (`chartGuideSteps.ts`, puro y testeable), `DentalChartGuide.tsx`,
  `FindingFinder.tsx` (el buscador del diálogo) y `relocation.ts` (las reglas de la tabla de D5, puras).
- `store/useGuidanceStore.ts`.

## Risks / Trade-offs

- **[Recorte desalineado al desplazar o al cambiar el tamaño]** → recalcular en `resize` y en `scroll` con captura
  (D1); test e2e a 375, 768 y 1280 px.
- **[Click a través del recorte]** → la capa intercepta el puntero en toda su superficie (D1).
- **[Apertura sobre un odontograma sellado durante la carga]** → condición con el bloque cargado y `OPEN` (D4).
- **[Intención de rango huérfana]** → se descarta al salir del modo rango (D5).
- **[La guía tapa el diálogo de hallazgo]** → la guía nunca abre el diálogo ni registra; si un diálogo está abierto, la
  guía no se abre sola.
- **[Odontograma con temporales o sin la pieza 16 dibujada]** → la 16 siempre se dibuja (fila permanente superior); si
  un ancla no existe, el paso se muestra sin recorte, centrado.
- **[Guía vista en un equipo y no en otro]** → aceptado (D4).

## Migration Plan

Solo frontend, sin cambios de API ni de base. Deploy normal; rollback revirtiendo el PR.
