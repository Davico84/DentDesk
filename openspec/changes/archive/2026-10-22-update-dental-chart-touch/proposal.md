## Why

En un celular, registrar un hallazgo en una cara es a la suerte. A 375 px el odontograma se ve al ~46 % (el dibujo mide
~1.100 unidades y entra en ~512 px): la cara oclusal de una pieza permanente queda en ~10 px, las laterales entre 5 y
10 px y las de una temporal entre 4 y 8 px; el espacio entre dos piezas, unos 5 px. Una zona táctil cómoda pide al
menos 44 px. El odontólogo que registra desde el celular toca la cara de al lado o la pieza entera sin querer.

## What Changes

- **Pieza ampliada al tocar con el dedo**: tocar una pieza abre un panel inferior con esa pieza en grande
  (~240 px): sus cinco caras tocables (cada una ≥ 44 px, con su nombre), "Toda la pieza", los espacios con sus vecinas
  ("Entre la 16 y la 15") y los hallazgos que la pieza ya tiene. Elegir una opción abre el registro de hallazgos como
  hoy, con esa localización.
- **Con mouse no cambia nada**: el click sigue yendo directo a la cara, al número o al espacio. Se decide por cada toque,
  no por el tipo de pantalla: en una laptop táctil, el mouse va directo y el dedo abre la pieza ampliada.
- **Convive con los modos existentes**: en modo ubicar, el panel solo ofrece lo que ese alcance admite (caras, toda la
  pieza o espacios); en modo rango, tocar una pieza sigue anclando el rango, sin panel; los arcos siguen con sus
  botones de la barra.
- **Solo frontend.** No cambia la API, el contrato ni qué localizaciones admite cada hallazgo.

## Capabilities

### New Capabilities
<!-- Ninguna. -->

### Modified Capabilities
- `dental-chart`: se agrega un requirement de cómo se elige la localización en pantallas táctiles. La regla de
  localización vigente ("Registro de hallazgos con localización precisa") no cambia: el panel es otra forma de tocar
  lo mismo.

## Non-goals

- **Zoom del gráfico** o pellizcar para ampliar: descartado por el usuario; obliga a ampliar y desplazar en cada
  registro y las temporales siguen chicas.
- **Lista de botones de caras sin dibujo**: descartada; pierde la referencia visual de qué cara es cuál.
- Rediseñar el odontograma para celular (otra disposición de las filas): el dibujo sigue siendo el de la NTS 188.
- Cambiar el registro en sí (el diálogo de hallazgo, el selector de tipo, el modo ubicar).

## Impact

- **Frontend**: módulo `dental-chart` (`Odontogram` decide entre click directo y panel según el tipo de puntero de
  cada toque; panel nuevo con la pieza ampliada sobre el `Sheet` de `core/ui`; la geometría de la corona pasa a un
  util compartido entre el dibujo y el panel).
- **Docs**: `docs/frontend.md` §4.3 (responsividad: zonas táctiles chicas en dibujos densos → panel ampliado).
