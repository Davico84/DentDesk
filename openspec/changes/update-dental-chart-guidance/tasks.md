> Solo frontend (sin cambios de API). Antes de tocar el frontend, skill `frontend-guard`. Pantalla autenticada: sistema
> de diseño existente (`docs/frontend.md` §4), sin `frontend-design`. Tests derivados de los Scenario del delta.
> Revisión de Codex (2026-10-15) incorporada: `initialType` en `FindingDialog`, capa que bloquea el puntero, apertura
> solo con el bloque cargado y abierto, `scroll` con captura, ciclo de vida de la intención de rango, retorno de foco.

## 1. Piezas genéricas

- [ ] 1.1 Mover `normalizeForSearch` de `modules/staff/utils/staffFilters.ts` a `core/utils/search.ts` (con su test) y que `staff` lo importe desde ahí
- [ ] 1.2 `core/components/GuidedTour` (design D1): capa que **intercepta el puntero en toda su superficie** con recorte solo visual alrededor del ancla (también SVG), tarjeta "Paso n de N" con Anterior/Siguiente/Cerrar, `scrollIntoView` del ancla, recálculo en `resize` y en `scroll` **con captura**, teclado (← → Esc), foco en la tarjeta y **devuelto** al cerrar, `aria-live`, sin transición con `prefers-reduced-motion`; paso sin ancla → tarjeta centrada. Un componente por archivo
- [ ] 1.3 Tests del `GuidedTour`: navegación con mouse y teclado, Esc cierra, anuncio del paso, ancla inexistente, `onClose` al terminar o cerrar, foco devuelto, un click sobre la zona resaltada no llega al elemento de abajo
- [ ] 1.4 `store/useGuidanceStore.ts` persistido (design D4): visto por `userId`, lecturas y escrituras protegidas; test

## 2. Guía del odontograma

- [ ] 2.1 Anclas `data-guide` en `Odontogram` (design D2): número y cara oclusal de la 16, un espacio entre piezas, botón de arco y botón de rango; sin cambio visual
- [ ] 2.2 `chartGuideSteps.ts` (design D3), puro: pasos en orden, hasta 3 ejemplos por `scope` desde el catálogo, se omite un `scope` sin tipos; test
- [ ] 2.3 `DentalChartGuide` + botón "¿Cómo registro hallazgos?" en la barra del odontograma; apertura automática solo con `block != null && block.status === "OPEN" && !seen && !dialogLocation` (design D4); marca vista al cerrar o terminar
- [ ] 2.4 Tests de los Scenario de la guía: abre sola la primera vez; no la segunda y se abre con el botón; el paso de pieza completa resalta el número y nombra ejemplos del catálogo; tocar la zona resaltada no abre el registro; anterior/siguiente/cerrar sin registrar nada; anuncio del paso; odontograma sellado sin guía ni botón, **también mientras el bloque carga**

## 3. Orientación en el diálogo

- [ ] 3.1 `relocation.ts` (design D5), puro: dado lo tocado y el `scope` elegido, devuelve "elegir", "ir a…" (pieza, arco, iniciar rango) o "explicar…"; el mismo alcance tiene precedencia; test de cada fila de la tabla
- [ ] 3.2 `FindingFinder` en `FindingDialogForm`: enlace "¿No encuentras el hallazgo?", buscador sin tildes sobre todo el catálogo, cada resultado con dónde se registra; mismo alcance → se elige en el selector
- [ ] 3.3 `FindingDialog` acepta `initialType`: con él arranca en el formulario (no en la lista) aunque la localización tenga hallazgos, con ese tipo elegido (design D5)
- [ ] 3.4 `DentalChartEditor` guarda la intención pendiente y la ejecuta solo al aceptar: reabre el diálogo en la pieza o el arco con `initialType`, o activa el rango con `initialRangeStart` y abre el diálogo con el tipo al elegir la última pieza; la intención de rango se descarta al salir del modo rango (botón, Escape, cambio de pantalla) y se mantiene si se re-ancla
- [ ] 3.5 Tests de los Scenario del diálogo: caries desde una cara; corona desde una cara de la 16 (también con la 16 con hallazgos previos); arco inferior desde la 36 abre su registro; rango desde la 14 y cancelarlo sin registrar ni dejar intención; superficie y entre piezas solo explican; búsqueda sin resultados; nada cambia sin aceptar

## 4. Cierre

- [ ] 4.1 e2e: la guía a 375, 768 y 1280 px con el recorte sobre su ancla y sin desplazamiento lateral de la página. El `CHART` de `app-shell.spec.ts` está `SEALED`: el test responde con un bloque `OPEN` y arranca sin la guía vista
- [ ] 4.2 `pnpm validate` verde y el e2e de responsividad; tests que comparan por campo y no por texto, y esperan las animaciones de salida (`waitFor`); captura para el usuario antes de commitear; prueba manual en local
- [ ] 4.3 `docs/frontend.md`: patrón `GuidedTour` (cuándo usarlo, anclas `data-guide`, capa que bloquea el puntero) en §4; `openspec validate update-dental-chart-guidance --strict` y medir a mano que ningún requirement supere 500 caracteres
