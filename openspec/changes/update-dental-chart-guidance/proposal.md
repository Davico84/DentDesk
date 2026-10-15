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
- **"¿No encuentras el hallazgo?" en el diálogo**: busca en **todo** el catálogo. Si el hallazgo elegido se registra en
  otro alcance, dice dónde y, cuando la ubicación se puede deducir de lo que se tocó, ofrece ir directo (de una cara a
  la pieza completa, de una pieza a su arco, o empezar un rango desde esa pieza); si no, explica qué tocar.
- **Solo frontend.** El catálogo ya informa el alcance de cada tipo (`scope`); no cambia la API ni el contrato.

## Capabilities

### New Capabilities
<!-- Ninguna. -->

### Modified Capabilities
- `dental-chart`: se agregan dos requirements de orientación al registrar (la guía y la ayuda dentro del diálogo). La
  regla de localización vigente ("Registro de hallazgos con localización precisa") no cambia: la guía la explica, no
  la relaja.

## Non-goals

- **Cambiar cómo se registra** un hallazgo o qué localización admite cada tipo (`ADR-0012`, `ADR-0017`).
- Guías para otras pantallas (historia clínica, notas de consulta): si sirve, se generaliza en otro change.
- Recordar en el servidor si el usuario ya vio la guía: es una comodidad del navegador (`docs/frontend.md` §3, estado
  de cliente persistido).
- Un tutorial con datos de ejemplo: la guía usa el odontograma del paciente abierto.

## Impact

- **Frontend**: módulo `dental-chart` (pasos de la guía, botón, ayuda en `FindingDialogForm`); un componente genérico de
  recorrido guiado en `core/components` (oscurecer y resaltar un elemento, pasos, teclado), sin librería nueva; un store
  persistido para "ya vio la guía" por usuario.
- **Odontograma**: marcas de anclaje (`data-*`) en las zonas que la guía resalta; ningún cambio visual fuera de la guía.
- **Docs**: `docs/frontend.md` (patrón de recorrido guiado reutilizable).
