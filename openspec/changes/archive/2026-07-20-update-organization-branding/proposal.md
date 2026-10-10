## Why

Hoy el `ADMIN` de una organización elige el color de marca con un `<input type="color">` libre
(`brandPrimary`/`brandSecondary`, validados solo por formato hex `#RRGGBB`) — cualquier hex pasa. El
motor de theming (`deriveTheme`) ya garantiza que el texto siga siendo legible sobre ese color
(contraste AA, con aviso si no alcanza), pero no garantiza que el resultado se vea **bien** ni
**consistente** con la identidad de Dentdesk: dos clínicas pueden terminar con paletas que chocan entre
sí o que simplemente no combinan con el resto del producto (tipografía, espaciado, el resto de la
paleta fija). Para un SaaS que quiere ofrecer personalización **controlada** (no un editor de diseño),
un catálogo curado de marcas pre-aprobadas resuelve esto: sigue habiendo personalización real por
clínica, pero el resultado siempre es bueno.

## What Changes

- El `ADMIN` deja de teclear un hex libre y en su lugar **elige un preset** de un catálogo curado y
  fijo de marcas (nombre + color(es)), pre-validadas para contraste AA contra la identidad de producto
  de Dentdesk.
- `deriveTheme` **no cambia su matemática**: sigue derivando `primary`/`secondary`/`accent`/`ring` +
  neutros a partir de un color de entrada; el preset simplemente **es** ese color de entrada, en vez de
  un hex tecleado a mano.
- Como cada preset del catálogo ya pasa el umbral de contraste por diseño, el modo de fallo "el
  administrador eligió un color con contraste insuficiente" deja de ser alcanzable desde la UI: el
  editor ya no puede producir una marca ilegible ni mediocre. `contrastWarning` deja de dispararse para
  una selección normal (se mantiene como red de seguridad interna, no como flujo esperado).
- Se formaliza el modelo de **3 capas** que ya gobierna implícitamente el theming, ahora explícito:
  1. **Identidad de producto (fija)**: tipografía, layout, motivo de marca — nunca se tematiza por
     organización.
  2. **Marca del tenant (controlada)**: solo el color, elegido de la lista curada, fluye a
     `primary`/`accent`/`ring`.
  3. **Bloqueado (nunca)**: colores semánticos (éxito/aviso/error), estructura de neutros, favicon del
     producto.
- **BREAKING**: `UpdateBrandingRequest`/`BrandingResponse` cambian de forma — `brandPrimary`/
  `brandSecondary` (hex libre) se reemplazan por una referencia al preset elegido (p. ej. una clave de
  catálogo). Cualquier organización con un color personalizado hoy (hex libre ya guardado) pierde esa
  personalización exacta al desplegar el cambio — ver Non-goals sobre migración de datos existentes.

## Capabilities

### New Capabilities

(ninguna — es una modificación de una capacidad ya construida)

### Modified Capabilities

- `organization-branding`:
  - **"Edición de la marca restringida al administrador"**: el `ADMIN` ya no envía un color en
    formato hex libre — envía la clave de un preset del catálogo. Un valor que no corresponda a un
    preset del catálogo se rechaza como entrada inválida (mismo lugar donde hoy se rechaza un hex mal
    formado).
  - **"Legibilidad garantizada (contraste)"**: el escenario "color de marca con contraste
    insuficiente" deja de ser alcanzable eligiendo del catálogo (todo preset ya pasa AA por
    construcción); el requirement se reformula para reflejar que la garantía la da el catálogo, no
    una advertencia reactiva ante cualquier hex.

## Impact

- **Backend**: `OrganizationBranding` (entity), `BrandingService`, `BrandingController`,
  `UpdateBrandingRequest`/`BrandingResponse` (DTOs) — el campo hex libre se reemplaza por una
  referencia a preset; nueva migración Flyway para el cambio de columna/constraint. El catálogo de
  presets en sí (cuántos, qué colores) es una decisión de diseño de apply-time, no de este proposal.
- **Contrato**: `contracts/openapi.json` cambia de forma (campo(s) nuevo(s), campo(s) removido(s)) →
  regenerar cliente orval, `ContractDriftIT`/`codegen-drift` deben mantenerse en verde.
- **Frontend**: `BrandingSettingsScreen` (el `<input type="color">` + texto libre se reemplazan por un
  selector del catálogo), `modules/branding/schemas/branding.ts` (el regex hex se reemplaza por
  validación de clave de preset), `deriveTheme.ts` sin cambios de lógica.
- **Sin impacto** en: identidad de producto/login (Dentdesk no se tematiza por tenant, ver
  `docs/vision.md`), el flujo de registro, ni ningún identificador interno (`com.odontorisas`, roles de
  BD) — fuera de alcance de este change.

## Non-goals

- **No** define la composición exacta del catálogo (cuántos presets, qué colores) — es una decisión de
  diseño de apply-time (con `frontend-design`/`ui-ux-pro-max`), no un requirement de este spec.
- **No** migra las organizaciones que ya tengan un `brandPrimary`/`brandSecondary` guardado hoy a un
  preset equivalente — ese mapeo (o su ausencia) se decide en `design.md`/`tasks.md`, no aquí.
- **No** toca la identidad de producto ni el login/registro (Dentdesk no se tematiza por tenant en esas
  pantallas) — son tracks de producto aparte.
- **No** rediseña el logo (URL externa con fallback) ni el favicon — sin cambios en esos requirements.
- **No** reabre el rename de identificadores internos (`com.odontorisas`, roles de BD) — decisión ya
  cerrada (ver `CLAUDE.md`).
