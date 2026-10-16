## Why

En el odontograma, **dónde** se hace click decide **qué** hallazgos ofrece el diálogo: el número de la pieza abre los
de pieza completa (corona, tratamiento de conducto…), una cara los de superficie (caries, restauración…), el espacio entre piezas
los de diastema o supernumerario, y los botones de arco y de rango los suyos. Nada de eso se ve en pantalla: quien
busca "corona" tras hacer click en una cara no la encuentra y no sabe por qué. Solo los botones de arco y rango tienen
una explicación (un tooltip); el resto se aprende por ensayo y error, sobre un documento clínico.

## What Changes

- **Guía paso a paso sobre el odontograma real**: oscurece la pantalla y resalta, en el gráfico del paciente, dónde se
  hace click para cada alcance (cara, pieza completa, entre piezas, arco, rango), con ejemplos de hallazgos de cada uno
  tomados del catálogo. Se abre sola la primera vez que el usuario tiene un odontograma editable; después queda el botón
  **"¿Cómo registro hallazgos?"** en la barra del odontograma.
- **Buscar el hallazgo por nombre y llegar siempre a dónde se registra**: "Tipo de hallazgo" pasa a ser un selector con
  búsqueda sobre todo el catálogo (primero los de la zona tocada, después "En otras zonas"). Si el elegido va en otra
  zona, ofrece ir directo cuando se deduce de lo tocado (de una cara a la pieza completa, de una pieza a su arco, o un
  rango desde esa pieza) y, si no, **"Elegir en el odontograma"**: un modo ubicar (generalización del modo rango) donde
  solo responden las zonas válidas para ese tipo. También se puede empezar por el nombre con **"Buscar hallazgo"** en la
  barra del odontograma.
- **Solo frontend.** El catálogo ya informa el alcance de cada tipo (`scope`); no cambia la API ni el contrato.

## Capabilities

### New Capabilities
<!-- Ninguna. -->

### Modified Capabilities
- `dental-chart`: se agregan dos requirements de orientación al registrar (la guía, y la búsqueda por nombre con el modo ubicar). La
  regla de localización vigente ("Registro de hallazgos con localización precisa") no cambia: la guía la explica, no
  la relaja.

## Non-goals

- **Cambiar cómo se registra** un hallazgo o qué localización admite cada tipo (`ADR-0012`, `ADR-0017`).
- Guías para otras pantallas (historia clínica, notas de consulta): si sirve, se generaliza en otro change.
- Recordar en el servidor si el usuario ya vio la guía: es una comodidad del navegador (`docs/frontend.md` §3, estado
  de cliente persistido).
- Un tutorial con datos de ejemplo: la guía usa el odontograma del paciente abierto.

## Impact

- **Frontend**: módulo `dental-chart` (pasos de la guía, botón, selector con búsqueda en `FindingDialogForm`, modo ubicar que reemplaza el modo rango); un componente genérico de
  recorrido guiado en `core/components` (oscurecer y resaltar un elemento, pasos, teclado), sin librería nueva; un store
  persistido para "ya vio la guía" por usuario.
- **Odontograma**: marcas de anclaje (`data-*`) en las zonas que la guía resalta; el modo rango pasa a ser un caso del
  modo ubicar (aviso propio y zonas no válidas atenuadas e inertes); botón "Buscar hallazgo" en su barra.
- **Docs**: `docs/frontend.md` (patrón de recorrido guiado reutilizable y combobox accesible propio).
