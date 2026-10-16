> Solo frontend (sin cambios de API). Antes de tocar el frontend, skill `frontend-guard`. Pantalla autenticada: sistema
> de diseño existente (`docs/frontend.md` §4), sin `frontend-design`. Tests derivados de los Scenario del delta.
> Revisión de Codex (2026-10-15) incorporada: `initialType` en `FindingDialog`, capa que bloquea el puntero, apertura
> solo con el bloque cargado y abierto, `scroll` con captura, ciclo de vida de la intención de rango, retorno de foco.

## 1. Piezas genéricas

- [x] 1.1 Mover `normalizeForSearch` de `modules/staff/utils/staffFilters.ts` a `core/utils/search.ts` (con su test) y que `staff` lo importe desde ahí
- [x] 1.2 `core/components/GuidedTour` (design D1): capa que **intercepta el puntero en toda su superficie** con recorte solo visual alrededor del ancla (también SVG), tarjeta "Paso n de N" con Anterior/Siguiente/Cerrar, `scrollIntoView` del ancla, recálculo en `resize` y en `scroll` **con captura**, teclado (← → Esc), foco en la tarjeta y **devuelto** al cerrar, `aria-live`, sin transición con `prefers-reduced-motion`; paso sin ancla → tarjeta centrada. Un componente por archivo
- [x] 1.3 Tests del `GuidedTour`: navegación con mouse y teclado, Esc cierra, anuncio del paso, ancla inexistente, `onClose` al terminar o cerrar, foco devuelto, un click sobre la zona resaltada no llega al elemento de abajo
- [x] 1.4 `store/useGuidanceStore.ts` persistido (design D4): visto por `userId`, lecturas y escrituras protegidas; test

## 2. Guía del odontograma

- [x] 2.1 Anclas `data-guide` en `Odontogram` (design D2): número y cara oclusal de la 16, un espacio entre piezas, botón de arco y botón de rango; sin cambio visual
- [x] 2.2 `chartGuideSteps.ts` (design D3), puro: pasos en orden, hasta 3 ejemplos por `scope` desde el catálogo, se omite un `scope` sin tipos; test
- [x] 2.3 `DentalChartGuide` + botón "¿Cómo registro hallazgos?" en la barra del odontograma; apertura automática solo con `block != null && block.status === "OPEN" && !seen && !dialogLocation` (design D4); marca vista al cerrar o terminar
- [x] 2.4 Tests de los Scenario de la guía: abre sola la primera vez; no la segunda y se abre con el botón; el paso de pieza completa resalta el número y nombra ejemplos del catálogo; tocar la zona resaltada no abre el registro; anterior/siguiente/cerrar sin registrar nada; anuncio del paso; odontograma sellado sin guía ni botón, **también mientras el bloque carga**

## 3. Búsqueda por nombre y modo ubicar (D5 revisado tras la prueba manual)

> La primera versión (subpanel "¿No encuentras el hallazgo?" en el diálogo, `FindingFinder`) se reemplaza: dos niveles,
> dos controles para una decisión y casos sin salida. Segunda revisión de Codex (2026-10-15) incorporada en D5.

- [x] 3.1 `FindingDialog` acepta `initialType`: con él arranca en el formulario (no en la lista) aunque la localización tenga hallazgos, con ese tipo elegido
- [x] 3.2 `relocation.ts`: el caso sin destino deducible pasa de `explain` a `place` (modo ubicar con ese tipo); test de cada fila de la tabla de D5
- [x] 3.3 `GroupedCombobox` en `core/components` + `findingTypeGroups` (ARIA 1.2, lista en línea sin portal; detalles en D5): `Controller` de RHF que limpia subtype/condition/grade al cambiar el tipo, `onBlur` solo al salir del combobox completo, opciones con `onMouseDown` preventDefault, Escape con la lista abierta no cierra el `Dialog` (`onEscapeKeyDown` + `preventDefault`), búsqueda sin tildes, grupos "Para esta zona" / "En otras zonas" con dónde se registra cada uno, flechas/Enter/Escape (Escape cierra la lista, no el diálogo), `aria-activedescendant`, "sin resultados" sin tocar el resto del formulario; en edición solo el alcance actual. Reemplaza al `SelectField` del tipo y a `FindingFinder` (eliminado)
- [x] 3.4 Elegir un tipo de otra zona muestra dónde se registra y una sola acción (D5): ir a la pieza o al arco, empezar el rango, o "Elegir en el odontograma"; nada cambia sin aceptarla
- [x] 3.5 `placement` en `DentalChartEditor` reemplaza `rangeMode` y el ancla sube al editor: `Odontogram` controlado (`placement` + `onPlacementChange`), sin `rangeStart` propio ni los puentes `initialRangeStart`/`onRangeAnchorCleared` (D5); "Registrar rango" = `{ scope: TOOTH_RANGE }`; al tocar una zona válida abre el diálogo con `initialType` y vuelve a `null`; se cancela con "Cancelar", Escape (en rango, primero suelta el ancla) y al desmontar; re-anclar conserva el tipo
- [x] 3.6 `Odontogram` en modo ubicar: solo responden las zonas del alcance; el resto atenuado, sin `tabIndex` y con `aria-disabled`; `PlacementHint` reemplaza `RangeModeHint` con el tipo y qué tocar; la guía no se abre sola y su botón y "Buscar hallazgo" quedan deshabilitados durante el modo; el aviso nombra la pieza tocada cuando viene de ella
- [x] 3.7 Botón "Buscar hallazgo" en la barra del odontograma: diálogo chico con el selector sobre todo el catálogo; al elegir, activa el modo ubicar. El último paso de la guía lo resalta (`data-guide`) y explica las dos vías
- [x] 3.8 Migrar los tests existentes al nuevo modelo antes de agregar los nuevos: `Odontogram.test.tsx` (los de `rangeMode`, ahora `placement`), `FindingDialog.test.tsx` (el tipo ya no es un `<select>`), `FindingFinder.test.tsx` (se elimina o se rehace sobre el combobox) y el e2e del enlace en `app-shell.spec.ts`; el flujo de "Registrar rango" sin tipo sigue igual
- [x] 3.9 Tests de los Scenario del delta: tipo de la zona tocada; corona desde una cara de la 16 (también con hallazgos previos); arco inferior desde la 36; rango desde la 14; destino no deducible → modo ubicar → registro con el tipo; zonas inertes con mouse y teclado; cancelar sin registrar ni dejar intención; buscar desde la barra; sin resultados; nada cambia sin confirmar; edición solo con su alcance; teclado del combobox (Escape cierra la lista y no el registro); re-anclar conserva el tipo; guía deshabilitada durante el modo

## 4. Cierre

- [x] 4.1 e2e: la guía a 375, 768 y 1280 px con el recorte sobre su ancla y sin desplazamiento lateral de la página. El `CHART` de `app-shell.spec.ts` está `SEALED`: el test responde con un bloque `OPEN` y arranca sin la guía vista. Además: el selector de tipo y el modo ubicar a 375 px (lista visible dentro del diálogo, aviso sin desplazamiento lateral); el e2e del enlace "¿No encuentras el hallazgo?" se reemplaza
- [x] 4.2 `pnpm validate` verde y el e2e de responsividad; tests que comparan por campo y no por texto, y esperan las animaciones de salida (`waitFor`); captura para el usuario antes de commitear; prueba manual en local
- [x] 4.3 `docs/frontend.md`: patrón `GuidedTour` (cuándo usarlo, anclas `data-guide`, capa que bloquea el puntero) y el combobox propio en §4; `openspec validate update-dental-chart-guidance --strict` y medir a mano que ningún requirement supere 500 caracteres
