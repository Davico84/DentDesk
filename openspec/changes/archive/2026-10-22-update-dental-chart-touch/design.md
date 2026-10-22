# Design

## Context

Ver `proposal.md` (Why). Estado observado en `Odontogram.tsx`:

- El dibujo es un SVG con `viewBox` de ~1.100 unidades de ancho y `CHART_MIN_WIDTH = 600` px (con el rótulo de fila de
  88 px, el SVG queda en ~512 px a 375 de pantalla, y se desliza dentro de su tarjeta).
- Cada pieza (`toothRow`) tiene tres destinos de click, cada uno con su `role="button"`, `tabIndex` y
  `activateOnEnterOrSpace`: el **número** (→ `TOOTH`), cada **cara** (`polygon` → `SURFACE`, geometría en
  `crownSlots`) y el **espacio** con la vecina (`rect` → `BETWEEN_TEETH`). Los arcos van por botones de la barra.
- El **modo ubicar** (`placement`) ya decide qué responde (`responds(scope)`); el **modo rango** ancla con un toque en
  el número. Ambos viven en `DentalChartEditor` y `Odontogram` es controlado.
- En las piezas anteriores la cara central (incisal) es una franja de ~16 % del alto de la corona; ampliada a 200 px
  mediría ~31 px.
- `core/ui/sheet.tsx` (shadcn/Radix) ya trae `side="bottom"`; lo usa `MobileNav`.
- El `matchMedia` simulado de los tests solo entiende `min-width`/`max-width`.

## Goals / Non-Goals

**Goals:**
- Que con el dedo cada cara, la pieza completa y los espacios se elijan sin errores (área ≥ 44 px).
- Que con mouse y teclado nada cambie.

**Non-Goals:**
- Zoom del dibujo, otra disposición del odontograma o cambiar el diálogo de registro.

## Decisions

### D1 — Se decide por cada toque, no por el tipo de pantalla

El grupo de cada pieza registra el `pointerType` del último `pointerdown` (`touch` | `mouse` | `pen`) y el `click`
decide: con `touch` abre la pieza ampliada; con `mouse`, `pen` o teclado (Enter/Espacio) va directo como hoy.

- **El teclado no consulta el puntero.** Enter/Espacio entra por `onKeyDown` → `activateOnEnterOrSpace`, que llama a
  la selección directa; la decisión de abrir el panel vive **solo** en el `onClick`.
- **El tipo se consume.** El `click` lee el `pointerType` guardado y lo **borra** en el acto: un `click` sin
  `pointerdown` previo (sintético, de accesibilidad, de un navegador viejo) no hereda un `touch` de un toque anterior
  y va directo.
*Alternativa descartada:* `matchMedia("(pointer: coarse)")`. Mira el puntero **principal** del dispositivo: una laptop
táctil con mouse quedaría en un modo u otro para los dos, y en los tests habría que enseñarle a la simulación a
responder esa consulta. El tipo de cada toque es exacto y se prueba con `userEvent.pointer({ keys: "[TouchA]" })` y
con `locator.tap()` en Playwright.

### D2 — Con el dedo, toda la pieza es una sola zona

Con `touch`, tocar el número **o cualquier cara** de la pieza abre la pieza ampliada de esa pieza: el dedo no distingue
la cara, así que no se le pide. El espacio entre dos piezas, si se acierta con el dedo, sigue yendo directo (es la
única zona que el panel también ofrece y no hay ambigüedad sobre cuál es).

### D3 — Panel inferior con la pieza ampliada (`ToothMagnifier`)

`Sheet` desde abajo con:
- **Título** "Pieza 16" y el tipo de pieza (permanente / temporal).
- **La pieza dibujada en grande** (~240 px de lado, centrada): las cinco caras como `polygon` con `role="button"`,
  su nombre visible dentro o junto a la cara y la orientación ya resuelta (vestibular arriba en superiores, etc.,
  igual que en el dibujo). Las caras con hallazgos se pintan con su color, como en el odontograma.
- **Proporciones propias del panel** para que todo cumpla 44 px: en las anteriores la franja incisal sube a ~24 % del
  alto (en el dibujo es ~16 %). Es una lupa, no el documento: no cambia el odontograma.
- **Botones** "Toda la pieza 16", "Entre la 16 y la 15" y "Entre la 16 y la 17" (solo las vecinas de la misma fila;
  en los extremos, una sola), cada uno ≥ 44 px de alto.
- **"Ya registrado"**: la lista de hallazgos de la pieza, de sus caras y de sus espacios, con la descripción de
  `describeFinding`.
- **Alto y bordes del celular**: el contenido (cabecera + pieza + botones + lo registrado) supera los 500 px; el panel
  usa `max-h-[85vh]` con `overflow-y-auto` y relleno inferior `max(1.5rem, env(safe-area-inset-bottom))` para que la
  barra de inicio de iOS no tape los últimos botones.
- **Accesibilidad**: los rótulos de las caras llevan `pointerEvents="none"` (no roban el toque al polígono); cada cara
  tiene nombre accesible ("Cara oclusal de la 16, sin hallazgos"); al abrir, el foco va al título del panel
  (`onOpenAutoFocus`) en vez de al ✕, y al cerrar vuelve a la pieza tocada (Radix).
- Elegir una opción cierra el panel y llama a `onSelectLocation` con la misma localización que daría el click directo:
  el registro, el modo ubicar y el `initialType` siguen funcionando igual. Cerrar (✕, deslizar, Escape, tocar afuera)
  no registra nada.

La geometría de la corona (`crownSlots`) pasa de `Odontogram.tsx` a `dental-chart/utils/toothGeometry.ts`, con un
parámetro de proporción mínima de la cara central para el panel; el dibujo la usa sin cambios.

### D4 — Modos

| Situación | Toque con el dedo en una pieza |
|---|---|
| Sin modo | abre la pieza ampliada con todo |
| Ubicar `SURFACE` | abre la pieza ampliada; solo las caras activas |
| Ubicar `TOOTH` | va directo a la pieza completa (no hay nada que elegir) |
| Ubicar `BETWEEN_TEETH` | abre la pieza ampliada; solo los espacios activos. **La pieza responde al toque** en este modo aunque en el dibujo su número y sus caras estén inertes: el espacio mide ~5 px y sin la pieza no habría cómo llegar a él con el dedo |
| Ubicar `ARCH` | nada (la pieza no responde, como hoy) |
| Rango (con o sin tipo) | ancla o cierra el rango, como hoy, sin panel |
| Odontograma sellado | nada |

Lo que el alcance no admite queda atenuado, sin `tabIndex` y con `aria-disabled`, igual que en el dibujo.

### D5 — Dónde vive cada pieza

- `dental-chart/components/ToothMagnifier.tsx`: el panel (conoce el dominio: caras, hallazgos, modos).
- `dental-chart/utils/toothGeometry.ts`: `crownSlots` y el cálculo de la corona ampliada.
- `Odontogram.tsx`: el `pointerType` por pieza y el estado de qué pieza está ampliada.

## Risks / Trade-offs

- **[Un toque abre el panel y otro click sintético llega al dibujo]** → el panel se abre desde el `click` (no desde
  `pointerup`), y el `Sheet` es modal: lo que hay debajo no recibe el segundo toque.
- **[La guía paso a paso enseña a tocar la cara directa]** → en celular, la guía sigue resaltando la cara y la pieza;
  su texto pasa a decir "toca la pieza y elige la cara" cuando el último toque fue con el dedo. Si complica, queda
  como mejora aparte y se anota.
- **[Proporción del panel distinta del dibujo en anteriores]** → aceptado: es una lupa para elegir; el registro guarda
  la cara, no la geometría.
- **[Navegadores sin `pointerType`]** → sin `PointerEvent` el click va directo, como hoy.

## Migration Plan

Solo frontend, sin cambios de API ni de base. Rollback revirtiendo el PR.

## Open Questions

Ninguna bloqueante. Si la guía (`chartGuideSteps`) necesita texto distinto para celular, se resuelve en el apply con
el usuario (ver Risks).
