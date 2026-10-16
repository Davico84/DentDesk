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
- Que el usuario aprenda **en el gráfico real** qué tocar para cada localización, y que pueda buscar un hallazgo por nombre
  y llegar siempre a dónde se registra, sin callejones sin salida.
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

### D5 — Un selector con búsqueda, y "modo ubicar" cuando el destino no se deduce

*Revisado tras la prueba manual (2026-10-15) y una segunda revisión de Codex.* La primera versión ponía un subpanel
"¿No encuentras el hallazgo?" dentro del diálogo: dos niveles, dos controles para una sola decisión (el select del tipo
y el buscador del tipo), un error de validación al perder el foco del select, y casos que terminaban en "cierra y vuelve
a tocar". Se reemplaza por:

**Selector con búsqueda (`GroupedCombobox`, genérico en `core/components`).** "Tipo de hallazgo" pasa a ser un combobox propio sobre el patrón
ARIA 1.2 (`role="combobox"` + `listbox` en línea, dentro del diálogo, sin portal), con búsqueda sin tildes
(`normalizeForSearch`, que pasa de `staff` a `core/utils`). Dos grupos: **"Para esta zona"** (el alcance tocado) y
**"En otras zonas"** (cada opción dice dónde se registra). En **edición** solo se lista el alcance actual: la UI no
mueve un hallazgo de lugar (para eso se elimina y se registra donde corresponde). *Descartado* `cmdk`/shadcn Command:
no está en el stack cerrado (`docs/architecture.md` §3) y su popover en portal compite con el foco del `Dialog` de Radix.

Detalles que el combobox tiene que resolver (todos con test):

- **Escape no cierra el diálogo mientras la lista está abierta.** Radix escucha Escape en `document` en **fase de
  captura** (`react-dismissable-layer`): un `stopPropagation` en el combobox llega tarde. El combobox expone si su lista
  está abierta y `DialogContent` usa `onEscapeKeyDown={(e) => { if (listOpen) e.preventDefault(); }}`; el combobox
  cierra su lista en su propio `onKeyDown`. Con la lista cerrada, Escape cierra el diálogo como hoy.
- **Integración con react-hook-form** vía `Controller` (valor = `type`). Al cambiar el tipo se limpian `subtype`,
  `condition` y `grade` en el mismo handler (un campo derivado se limpia en cada disparador que lo invalida).
- **Sin error prematuro.** Las opciones usan `onMouseDown={preventDefault}` (el input no pierde el foco al hacer click en
  la lista) y el `onBlur` del campo se llama solo cuando el foco sale del combobox completo (`relatedTarget` fuera del
  contenedor). Así "Selecciona el tipo" no aparece mientras el usuario está eligiendo.
- **Texto y valor.** El input muestra la etiqueta del tipo elegido; escribir filtra; al salir sin elegir, el texto vuelve
  a la etiqueta del valor vigente (no queda texto suelto que parezca elegido). Elegir otra zona **no** cambia el valor
  del formulario: solo muestra la acción.
- **Lista larga** (38 tipos): `max-h` con scroll dentro del diálogo; la opción activa se mantiene a la vista
  (`scrollIntoView({ block: "nearest" })`) al moverse con flechas. A 375 px la lista va en línea, debajo del input, sin
  portal.

**Elegir un tipo de otra zona** muestra, bajo el selector, dónde se registra y **una** acción (nada cambia sin ella):

| Tocado → alcance del elegido | Acción |
|---|---|
| mismo alcance | se elige, sin acción extra |
| SURFACE (pieza N) → TOOTH | "Registrarlo en la pieza N": reabre el diálogo en la pieza N con el tipo |
| SURFACE o TOOTH (pieza N) → ARCH | "Registrarlo en el arco superior/inferior" (`isUpperArchTooth(N)`) |
| SURFACE o TOOTH (pieza N) → TOOTH_RANGE | "Empezar un rango desde la pieza N": modo ubicar de rango con N como ancla |
| cualquier otro caso (TOOTH → SURFACE, → BETWEEN_TEETH, desde espacio/arco/rango) | "Elegir en el odontograma": cierra el diálogo y activa el modo ubicar con ese tipo |

Así **ningún caso termina en una explicación sin salida**. `relocation.ts` devuelve `select`, `goTo`, `startRange` o
`place` (antes `explain`).

**Modo ubicar = generalización del modo rango.** Un solo estado en `DentalChartEditor`, que reemplaza `rangeMode`:

```ts
type Placement = { scope: FindingScope; type?: string; rangeStart?: number } | null;
```

- "Registrar rango" es `{ scope: "TOOTH_RANGE" }` sin tipo; "Elegir en el odontograma" y "Buscar hallazgo" lo activan
  con tipo; "Empezar un rango desde N", con `rangeStart`.
- **El ancla del rango sube al editor.** Hoy `Odontogram` guarda `rangeStart` adentro y lo resetea en el render; con
  `placement` el ancla vive en un solo lugar: `Odontogram` pasa a ser **controlado** (`placement` + `onPlacementChange`),
  sin estado propio de rango. Desaparecen el reseteo en render y los callbacks puente (`initialRangeStart`,
  `onRangeAnchorCleared`) de la primera versión.
- Mientras está activo, **solo las zonas de ese alcance responden**: el resto se atenúa y queda inerte **también para el
  teclado** (sin `tabIndex`, `aria-disabled`), no solo con `pointer-events-none`. En ARCH, los botones de arco; en
  TOOTH_RANGE, los números de pieza (dos clicks, la misma fila, como hoy).
- `PlacementHint` (reemplaza `RangeModeHint`): "Ubicando: Corona — toca el número de la pieza · Cancelar".
- Al tocar una zona válida se abre el diálogo con esa localización y `initialType`, y `placement` vuelve a `null`.
- Se cancela con "Cancelar", con Escape (en rango, el primer Escape suelta el ancla y el segundo sale del modo) y al
  cambiar de pantalla (es estado local). Re-anclar el rango en otra fila conserva el tipo. Nunca queda una intención
  pendiente fuera del modo.
- Mientras hay modo ubicar, la guía no se abre sola y su botón queda deshabilitado (como hoy con el rango); "Buscar
  hallazgo" también, para no superponer dos modos. Sin catálogo cargado, "Buscar hallazgo" está deshabilitado y el
  combobox muestra "Cargando…". Con el bloque `SEALED` no hay barra, así que tampoco hay modo ubicar.
- Para TOOTH → SURFACE o BETWEEN_TEETH, el aviso nombra la pieza tocada ("toca una cara de la pieza 36"), aunque
  respondan todas las caras o espacios: el usuario puede haberse equivocado de pieza.

**Entrada desde la barra.** Botón "Buscar hallazgo" en la barra del odontograma: abre un diálogo chico con el mismo
selector sobre todo el catálogo, agrupado por zona; al elegir, cierra y activa el modo ubicar con ese tipo. Es la
respuesta a "busco por nombre y lo registro" sin tocar antes el gráfico. *Descartado* un modo ubicar "sin tipo" donde
el tipo se elige al tocar: es el flujo de hoy (tocar primero) y no responde a quien parte del nombre.

**`FindingDialog` con `initialType`.** Con él arranca en el formulario (no en la lista) aunque la localización tenga
hallazgos, con el tipo elegido.

**Guía.** El último paso ("¿No encuentras el hallazgo?") pasa a resaltar el botón "Buscar hallazgo" y explica las dos
vías: escribir el nombre en "Tipo de hallazgo" o usar ese botón.

### D6 — Dónde vive cada pieza

- `core/components/guided-tour/`: `GuidedTour` + su capa y tarjeta en archivos propios: genérico, no conoce el
  odontograma.
- `modules/dental-chart/guidance/`: pasos (`chartGuideSteps.ts`, puro y testeable), `DentalChartGuide.tsx`,
  `findingTypeGroups.ts` (los grupos del selector desde el catálogo), `FindingSearchDialog.tsx` (la entrada desde la
  barra), `PlacementHint.tsx`, `placement.ts` (el estado del modo ubicar) y `relocation.ts` (la tabla de D5, pura).
- `core/components/GroupedCombobox.tsx`: el selector con búsqueda, genérico (no conoce el catálogo).
- `store/useGuidanceStore.ts`.

## Risks / Trade-offs

- **[Recorte desalineado al desplazar o al cambiar el tamaño]** → recalcular en `resize` y en `scroll` con captura
  (D1); test e2e a 375, 768 y 1280 px.
- **[Click a través del recorte]** → la capa intercepta el puntero en toda su superficie (D1).
- **[Apertura sobre un odontograma sellado durante la carga]** → condición con el bloque cargado y `OPEN` (D4).
- **[El diálogo se cierra al pasar a "Elegir en el odontograma" y parece que falló]** → `PlacementHint` aparece en el
  mismo render, con el tipo y qué tocar (D5).
- **[Intención huérfana]** → solo existe dentro del modo ubicar; salir del modo la descarta (D5).
- **[Zonas inertes que siguen en el orden de Tab]** → sin `tabIndex` y con `aria-disabled` durante el modo (D5).
- **[Combobox propio: accesibilidad y móvil]** → patrón ARIA 1.2 completo (flechas, Enter, Escape cierra la lista sin
  cerrar el diálogo, `aria-activedescendant`), lista en línea sin portal; tests de teclado y e2e a 375 px.
- **[La guía tapa el diálogo de hallazgo]** → la guía nunca abre el diálogo ni registra; si un diálogo o el modo ubicar
  están activos, la guía no se abre sola.
- **[Odontograma con temporales o sin la pieza 16 dibujada]** → la 16 siempre se dibuja (fila permanente superior); si
  un ancla no existe, el paso se muestra sin recorte, centrado.
- **[Guía vista en un equipo y no en otro]** → aceptado (D4).

## Migration Plan

Solo frontend, sin cambios de API ni de base. Deploy normal; rollback revirtiendo el PR.
