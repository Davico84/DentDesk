## Why

El frontend usa **Tailwind CSS 3.4**, pero el registro actual de **shadcn/ui** genera componentes para **Tailwind v4**. Cada componente que se agregue habría que adaptarlo a mano a v3; los primeros van a llegar enseguida con `add-app-shell` (`sheet` para el cajón móvil, `progress`). Migrar antes del shell es lo más barato: el frontend todavía es chico y la migración no se mezcla con una pantalla nueva.

Además, **ninguna spec protege el sistema de tema** (tokens de color en `globals.css`, modo oscuro por clase, tema de cada clínica aplicado en runtime), así que la migración no tendría un contrato verificable. Hoy ningún test compila el CSS (`vitest` corre con `css: false`). Este change agrega ese contrato.

Es el segundo ítem del checklist de mejoras traídas del proyecto hermano HSO (`docs/vision.md` §8; HSO: `update-tailwind-v4`, 2026-09-28).

## What Changes

- **Migración a Tailwind v4**, solo frontend, **sin cambio visual intencionado**:
  - `tailwindcss` 4 + plugin `@tailwindcss/vite`; se eliminan `tailwind.config.cjs`, `postcss.config.cjs`, `autoprefixer` y `tailwindcss-animate` (→ `tw-animate-css`).
  - Configuración **CSS-first** en `src/styles/globals.css`: `@import "tailwindcss"`, `@theme inline` que mapea los tokens y `@custom-variant dark` (modo oscuro por clase `dark`, como hoy).
  - Tokens con **color completo** (`--primary: hsl(292 72% 36%)`) en lugar de canales sueltos (`292 72% 36%`). Los **valores** de la paleta no cambian.
  - Utilidades renombradas por v4 (`shadow-sm`→`shadow-xs`, `rounded-sm`→`rounded-xs`, `outline-none`→`outline-hidden`, ancho por defecto de `ring`…) con `@tailwindcss/upgrade` y revisión manual.
- **Tema de cada clínica**: `deriveTheme` pasa a emitir color completo (`hsl(H S% L%)`) en vez de canales, para que el `<style>` que inyecta `ThemeManager` siga funcionando con el nuevo formato. La caché local guarda los colores de marca, no los tokens, así que no hay nada que migrar.
- **Colores fijos del login → tokens fijos de producto** (decisión del usuario): el botón de ingreso, los puntos de acento, el medidor de contraseña y los arcos animados del fondo usan tokens `--brand-*` con **los mismos valores que hoy en claro y en oscuro**. El tema de la clínica no los pisa, así que el login se ve idéntico.
- **Nuevos requirements en `project-foundation`**: "Tema definido por tokens CSS" y "Sin colores literales en componentes", con tests que compilan el CSS real y vigilan colores literales. Excepciones justificadas: los colores normativos del odontograma (NTS 188) y su paleta de papel, y los presets de marca (son datos).
- **Docs**: `frontend.md` §4.5 (formato de tokens y v4), `architecture.md` y `ADR-0001` (Tailwind 4).

## Capabilities

### New Capabilities

_(ninguna)_

### Modified Capabilities

- `project-foundation`: agrega el contrato del tema (tokens, modo oscuro por clase) y la prohibición de colores literales con sus excepciones.

`organization-branding` no cambia de comportamiento: sus requirements (tema derivado, contraste, modo oscuro, semánticos intactos) se verifican como regresión, sin delta.

## Non-goals

- Rediseño visual, o cambiar valores de la paleta.
- Paleta en OKLCH (el formato de color completo lo permite después sin tocar componentes).
- Agregar componentes de shadcn (`sheet`, `progress`…): llegan con `add-app-shell`.
- Convertir a tokens los colores del odontograma: son normativos (rojo/azul de NTS 188) o deliberadamente fijos (el odontograma es un papel, igual en claro y oscuro).

## Impact

- **Frontend**: `package.json` (deps), `vite.config.ts`, `src/styles/globals.css`, archivos de config que se borran, `modules/core/theme/deriveTheme.ts` (+ tests), componentes y hook de `modules/auth`, utilidades renombradas en `src/`.
- **Tests nuevos**: compilación real del CSS con `@tailwindcss/node` (devDependency), paridad de tokens claro/oscuro, ausencia de colores literales.
- **Navegadores**: Tailwind v4 requiere Safari 16.4+, Chrome 111+, Firefox 128+.
- **Sin cambios** de backend ni de contrato.
