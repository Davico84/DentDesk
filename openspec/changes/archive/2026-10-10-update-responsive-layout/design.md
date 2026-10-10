# Design — update-responsive-layout

## Context

- `Odontogram.tsx` dibuja un SVG de `CHART_WIDTH` = 1100 unidades (`viewBox`), con una columna HTML de etiquetas de 88 px a su izquierda cuyas alturas son px fijos (`LANE_H`, `rangeStripHeights`, `rowHeights`). Todo va dentro de una tarjeta `overflow-x-auto`.
- En pantallas angostas el SVG, que es hijo flex, se encoge con el contenedor (alto proporcional), pero la columna de etiquetas conserva sus alturas en px: ese desfase produce las bandas vacías.
- Ancho disponible para el dibujo a 768 px (tablet, barra de íconos de 64 px, `PageContainer` `sm:p-6`, tarjeta `px-4`): ~620 px. Es el tamaño que hoy se lee y se usa en tablet (coronas de ~26 px).
- `FindingList.tsx` es una tabla de 4 columnas (+ eliminar) en un `overflow-x-auto`.
- Ya existen `useMediaQuery` (`core/hooks`) y el shim de `matchMedia` en los tests (jsdom = 1024 px), de `add-app-shell`.

## Goals / Non-Goals

**Goals:** odontograma legible y tocable en celular sin que la página se desplace; etiquetas alineadas a cualquier ancho; hallazgos completos a la vista en celular; ficha pareja en celular.

**Non-Goals:** los del proposal (sin rediseño del odontograma para celular, sin tocar pantallas que ya están bien).

## Decisions

### D1. Ancho mínimo del dibujo: 600 px, con scroll solo en el dibujo

- La fila del dibujo (etiquetas + SVG) va en su propio contenedor `overflow-x-auto`, con `min-width: 600px` en su contenido. La barra de herramientas, el resumen, "Derecha / Izquierda del paciente" y la leyenda quedan **fuera** del scroll, así siempre se ven.
- El SVG pasa a `width: 100%` con `height: auto` (sin el atributo `width` fijo): escala con el contenedor hasta su ancho natural (1100) y no baja de 600 − 88.
- 600 px cubre los ~620 px de tablet, así que a 768 px no aparece scroll y el tamaño es el de hoy; solo por debajo de eso (celular) el dibujo se desliza.

*Alternativa descartada:* que toda la tarjeta haga scroll (como hoy). La barra de herramientas ("Registrar rango") quedaría fuera de la vista al deslizar.

### D2. Etiquetas que escalan con el dibujo, sin JS

La columna de etiquetas se estira a la altura de la fila (`self-stretch`, que en flex es la altura del SVG ya escalado) y cada etiqueta se posiciona en **porcentaje** de esa altura (`top: rowY[i] / height * 100%`). Las etiquetas conservan su tamaño de letra (no se escalan como el SVG) y siempre quedan a la altura de su fila.

*Alternativa descartada:* medir el ancho con `ResizeObserver` y multiplicar las alturas. Más código y un render extra; los porcentajes lo resuelven con CSS.

*Alternativa descartada:* llevar las etiquetas dentro del SVG. A 600 px escalarían a ~6 px de letra.

### D3. Indicación de que hay más a los lados

Mientras el dibujo no entre completo (`scrollWidth > clientWidth`), se muestra bajo él "Desliza para ver toda la boca" y un degradado en el borde hacia donde queda contenido. Se detecta con un hook genérico `useHorizontalOverflow(ref)` en `core/hooks` (`ResizeObserver` + evento `scroll`), que devuelve si sobra a la izquierda y a la derecha. Sin `ResizeObserver` (jsdom) devuelve "no sobra": el comportamiento se prueba en el e2e.

### D4. Lista de hallazgos: bloques bajo 640 px

`FindingList` elige con `useMediaQuery("(min-width: 640px)")` entre la tabla actual y una lista (`<ul>`) de bloques: sigla + tipo en la primera línea, localización y estado debajo, eliminar a la derecha con 44 px de objetivo táctil y el mismo `aria-label`. Se renderiza **uno** de los dos (no ambos con clases `hidden`) para no duplicar el contenido para lectores de pantalla ni en los tests. Las dos vistas son componentes separados (`FindingTable`, `FindingCards`), uno por archivo.

### D5. Acciones de la ficha

Bajo 640 px, los tres documentos clínicos van en una grilla de 3 columnas iguales (ícono sobre el nombre); desde 640 px, como hoy. Editar y Dar de baja quedan en su propio renglón. Solo clases en `PatientDetail`.

### D6. Copy

La ayuda de `DentalChartEditor` pasa a tú: "activa «Registrar rango» arriba, elige la primera pieza y después la última".

## Risks / Trade-offs

- **El SVG sin `width` fijo** podría cambiar el tamaño en escritorio si el contenedor es más ancho que 1100 px. Mitigación: `max-width: 1100px` en el SVG (su tamaño natural de hoy). Las capturas antes/después a 1280 px lo confirman.
- **Hit areas del odontograma a 600 px**: iguales a las de tablet hoy (~26 px por corona); en celular se tocan, aunque con menos holgura que en escritorio. Es lo decidido (scroll lateral, no rediseño).
- **Tests de jsdom** no ven el scroll ni el degradado: lo cubre el e2e de anchos.
