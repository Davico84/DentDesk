# Proposal — update-responsive-layout

## Why

Último ítem del checklist de HSO (`docs/vision.md` §8): revisión responsive a 375 y 768 px. `add-app-shell` dejó un e2e que garantiza que ninguna pantalla privada se desplaza de costado (`e2e/app-shell.spec.ts`), pero no desborda ≠ se puede usar. La revisión de capturas (todas las pantallas privadas, claro/oscuro, con y sin preset) encontró:

1. **Odontograma ilegible en celular.** A 375 px el SVG se encoge a ~30 % (coronas de ~8 px): no se lee ni se puede tocar una superficie.
2. **Bandas vacías en el odontograma angosto.** La columna de etiquetas (Permanente / Temporal) mide en px fijos mientras el SVG se escala: a 375 px quedan franjas en blanco entre las etiquetas y las piezas, y las etiquetas dejan de estar al lado de su fila.
3. **Tabla de hallazgos cortada** a 375 px: la columna Estado queda fuera de la vista (se puede desplazar, pero nada lo indica).
4. **Acciones de la ficha desparejas** en celular: Historia Clínica, Consultas y Odontograma caen en tres renglones de ancho distinto.
5. **Copy**: la ayuda de la lista de hallazgos usa voseo ("activá", "elegí"); el resto de la app usa tú.
6. **Barra de scroll en la barra lateral contraída** (reportado por el usuario): el contenedor de la navegación se encogía a 18 px y el arco del ítem activo lo desbordaba. **Ya corregido** en esta rama (`fix(app-shell)`, con e2e de regresión) porque se veía en todas las pantallas.

Historia Clínica, consultas, listado, formulario, Inicio y Marca se revisaron y están bien a 375 y 768 px.

## What Changes

- **Odontograma con ancho mínimo legible** (decisión del usuario): por debajo de ese ancho no se encoge más; se desplaza de costado **dentro de su tarjeta**, con una indicación visible de que hay más a los lados. La página sigue sin desplazarse de costado.
- **Etiquetas de fila alineadas a cualquier ancho**: la columna Permanente/Temporal escala junto con el SVG, sin bandas vacías.
- **Lista de hallazgos apilada en celular**: cada hallazgo como un bloque (sigla y tipo arriba; localización y estado abajo) en vez de la tabla de cuatro columnas. Desde 640 px, la tabla actual.
- **Acciones de la ficha en celular**: los tres documentos clínicos en una grilla pareja; Editar y Dar de baja en su renglón.
- **Copy**: la ayuda de la lista de hallazgos pasa a tú.
- El e2e de anchos suma la comprobación de que el odontograma a 375 px conserva su ancho mínimo.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `dental-chart`: nuevo requirement de lectura del odontograma y su lista de hallazgos en pantallas angostas.

## Non-goals

- Rediseñar el odontograma para celular (otra disposición de piezas, zoom con gestos): el registro clínico se hace en tablet o escritorio.
- Tocar las pantallas que ya se ven bien.
- Cambiar los breakpoints o el shell (`add-app-shell`, `docs/frontend.md` §4.3).
- El contraste del resumen "Por hacer / Realizado" en modo oscuro (pendiente aparte).

## Impact

- Solo frontend: `modules/dental-chart/components/Odontogram.tsx`, `FindingList.tsx`; `modules/patients/components/PatientDetail.tsx`; `e2e/app-shell.spec.ts`.
- Sin cambios de API, base de datos ni dependencias.
- `docs/vision.md` §8: cierra el checklist de HSO.
