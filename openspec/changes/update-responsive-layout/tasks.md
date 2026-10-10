> Solo frontend. Antes de modificar cada componente o hook: skill `frontend-guard`. Pantallas existentes: revisión visual con capturas antes/después (reparto de skills: `impeccable` audita pantallas existentes).

## 0. Ya hecho en la rama

- [x] 0.1 Barra lateral contraída sin barra de scroll horizontal + e2e de regresión (`fix(app-shell)`, reportado por el usuario)

## 1. Odontograma

- [ ] 1.1 Capturas "antes" del odontograma (abierto y sellado) a 375, 768 y 1280 px, claro y oscuro
- [ ] 1.2 Fila del dibujo en su propio contenedor con scroll y `min-width: 600px`; SVG a `width: 100%`, `height: auto`, `max-width` natural; barra de herramientas, resumen, orientación y leyenda fuera del scroll (design D1)
- [ ] 1.3 Etiquetas de fila posicionadas en porcentaje de la altura del dibujo (design D2)
- [ ] 1.4 `core/hooks/useHorizontalOverflow` + indicación "Desliza para ver toda la boca" y degradado en el borde con contenido (design D3); tests del hook
- [ ] 1.5 Tests del odontograma que siguen pasando (clicks en pieza, superficie, arco, rango y entre-piezas) tras el cambio de tamaño

## 2. Lista de hallazgos y ficha

- [ ] 2.1 `FindingTable` y `FindingCards`, uno por archivo; `FindingList` elige por `useMediaQuery` (design D4); eliminar con 44 px y el mismo `aria-label` en la vista de bloques
- [ ] 2.2 Tests por scenario de *Lista de hallazgos legible en celular* (bloques a 375 con los cuatro datos y eliminar; tabla desde 640)
- [ ] 2.3 `PatientDetail`: documentos clínicos en grilla de 3 columnas bajo 640 px (design D5)
- [ ] 2.4 Copy de la ayuda de `DentalChartEditor` a tú (design D6)

## 3. Verificación y docs

- [ ] 3.1 e2e en `e2e/app-shell.spec.ts`: a 375 px el dibujo mide ≥ 600 px, se desplaza dentro de su tarjeta y se ve la indicación; a 1280 px no hay scroll en el dibujo ni indicación; las etiquetas quedan a la altura de su fila (375/768/1280)
- [ ] 3.2 Capturas "después" y comparación: en escritorio el odontograma no cambia de tamaño
- [ ] 3.3 `pnpm validate` verde; `pnpm build`
- [ ] 3.4 `docs/frontend.md` §4.3 (contenido ancho: scroll dentro de su tarjeta, nunca la página) y `docs/vision.md` §8 (checklist de HSO cerrado)
- [ ] 3.5 `openspec validate update-responsive-layout --strict`
