# Design — update-tailwind-v4

## Context

- Tailwind **3.4** con configuración JS: `tailwind.config.cjs` mapea `colors.primary = "hsl(var(--primary))"` (y `card`, `success`, `warning`…), radios desde `--radius`, `darkMode: ["class"]`, plugin `tailwindcss-animate`. PostCSS con `tailwindcss` + `autoprefixer`.
- Tokens en `src/styles/globals.css` como **canales HSL** (`--primary: 292 72% 36%`), en `:root` y `.dark`.
- **Tema por organización en runtime** (`organization-branding`): `deriveTheme` calcula los tokens a partir del color de marca y los devuelve como canales (`hslStr` → `"292 72% 36%"`); `ThemeManager` los inyecta en un `<style id="odr-org-theme">` con bloques `:root` y `.dark`. La caché local (`odr-branding`) guarda los colores de marca en hex, no los tokens. **Esto no existe en HSO**: es la diferencia principal con su migración.
- `vitest` corre con `css: false`: ningún test compila el CSS.
- Colores literales en `src/` (sin tests ni generados), 27 en total:
  - **login** (`modules/auth`): `AuthSubmitButton` (`#7f1aa6`/`#671a86`, hover `#711794`/`#7a1e9c`; fijos a propósito, el comentario explica que `--primary` cambia en oscuro), puntos de `LoginForm` y el ícono de `RegisterForm` (`hsl(174 70% 40%)`, `hsl(152 55% 38%)`), medidor de `usePasswordStrength` (ámbar, teal, verde) y los arcos del `<canvas>` de `SmileArcBackground` (`rgba(77,208,191,α)`, `rgba(221,195,230,α)`);
  - **odontograma**: `findingColor.ts` (rojo/azul normativos de NTS 188) y `chartPalette.ts` (papel, tinta, divisores: el odontograma se dibuja como papel, igual en claro y oscuro);
  - **presets de marca** (`branding/presets.ts`): los hex de cada preset son datos.
- Antecedente: misma migración en HSO (`update-tailwind-v4`), que se reutiliza salvo en el tema por organización y en las excepciones.

## Goals / Non-Goals

**Goals:** Tailwind 4 con configuración CSS-first; UI visualmente igual, también con el tema de una clínica aplicado; tokens compatibles con el registro actual de shadcn; contrato de tema cubierto por tests; cero colores literales fuera de una lista justificada.

**Non-Goals:** los del proposal (rediseño, OKLCH, componentes nuevos, tokens en el odontograma).

## Decisions

### D1. Plugin de Vite, sin PostCSS

`@tailwindcss/vite` en `vite.config.ts`; se borran `postcss.config.cjs`, `postcss` (si nada más lo usa) y `autoprefixer`, porque v4 agrega los prefijos con Lightning CSS.

*Alternativa descartada:* `@tailwindcss/postcss`, que solo tiene sentido si otra herramienta exige PostCSS. Acá todo pasa por Vite.

### D2. Tokens con color completo y `@theme inline` (patrón shadcn v4)

- `:root` y `.dark` pasan de `--primary: 292 72% 36%` a `--primary: hsl(292 72% 36%)`, con los mismos valores.
- `@theme inline { --color-primary: var(--primary); … }` crea las utilidades. `inline` hace que la utilidad referencie `var(--primary)` directamente: el cambio claro/oscuro y el `<style>` de la clínica siguen funcionando en runtime, y los modificadores de opacidad (`bg-primary/10`) se resuelven con `color-mix()`.
- Radios: `--radius-lg: var(--radius)`, `--radius-md: calc(var(--radius) - 2px)`, `--radius-sm: calc(var(--radius) - 4px)`, equivalentes exactos a los de hoy.

*Alternativa descartada:* mantener los canales y escribir `--color-primary: hsl(var(--primary))`. Funciona, pero se aparta del formato que emite shadcn y obligaría a adaptar cada componente que llegue del registro.

### D3. El tema de la clínica emite color completo

`deriveTheme` cambia `hslStr` para devolver `hsl(H S% L%)`. `ThemeManager` no cambia: escribe lo que recibe. La caché guarda hex y el tema se vuelve a derivar al cargar, así que un navegador con caché vieja no queda con tokens en el formato anterior. Los tests de `deriveTheme` y `ThemeManager` se ajustan al formato nuevo, y se agrega uno que comprueba que el tema derivado **no** incluye tokens `--brand-*` (D5).

### D4. Modo oscuro por clase

`@custom-variant dark (&:where(.dark, .dark *));` mantiene el comportamiento actual (clase `dark` en `<html>`, que maneja `ThemeToggle`) y la aplica también al propio elemento que tiene la clase.

### D5. Colores fijos del login → tokens fijos de producto (decisión del usuario)

Tokens nuevos en `globals.css`, **con los mismos valores de hoy** en `:root` y `.dark`, expuestos como utilidades `brand-*`:

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `--brand-cta` | `#7f1aa6` | `#671a86` | fondo del botón de ingreso |
| `--brand-cta-hover` | `#711794` | `#7a1e9c` | hover del botón |
| `--brand-teal` | `hsl(174 70% 40%)` | igual | puntos e ícono de acento, medidor "Buena" |
| `--brand-green` | `hsl(152 55% 38%)` | igual | punto de acento, medidor "Fuerte" |
| `--brand-amber` | `hsl(38 92% 46%)` | igual | medidor "Regular" |
| `--brand-arc-teal` | `rgb(77 208 191)` | igual | arco destacado del fondo |
| `--brand-arc-lilac` | `rgb(221 195 230)` | igual | arcos del fondo |
| `--brand-hero-from` / `-via` / `-to` | `#671a86` / `#7f1aa6` / `#481864` | `#481864` / `#671a86` / `#2b0e39` | gradiente del panel de marca (`AuthLayout`); agregado en el apply: el test de literales no lo veía por el `_` después del hex |

- Por qué tokens propios y no `success`/`warning`/`primary`: esos cambian en modo oscuro o con la marca de la clínica, y el login dejaría de verse igual. Es exactamente la razón por la que hoy son literales.
- El `<canvas>` de `SmileArcBackground` no puede usar clases: lee el token con `getComputedStyle(document.documentElement).getPropertyValue("--brand-arc-teal")` y aplica la transparencia animada con `ctx.globalAlpha` en vez de `rgba(…, α)`.
- `deriveTheme` nunca emite `--brand-*` (D3), así que el tema de la clínica no los pisa.

### D6. Migración asistida y revisión manual

Correr `npx @tailwindcss/upgrade` sobre un árbol limpio (renombra utilidades en `src/`, convierte la configuración y actualiza dependencias) y después **revisar el diff** archivo por archivo. La herramienta no conoce nuestros tokens (hay que dejar el `@theme inline` de D2) ni los valores por defecto que cambian:

- **borde por defecto** `currentColor`: ya cubierto por `* { @apply border-border }`;
- **`ring` por defecto** de 1px y `currentColor`: verificar que no queden `ring` sueltos sin ancho ni color;
- `outline-none` → `outline-hidden` donde oculta el foco nativo junto a `focus-visible:ring-*`;
- `tailwindcss-animate` → `tw-animate-css` (`@import "tw-animate-css"`): los diálogos y tooltips de Radix usan sus clases `animate-in`/`fade-in`.

### D7. Tests del contrato de tema

- **Compilación real**: un test de Vitest en entorno `node` compila `globals.css` con `compile()` de `@tailwindcss/node` (devDependency, misma versión que `tailwindcss`) y pide candidatos (`bg-primary`, `bg-primary/10`, `text-muted-foreground`, `bg-brand-cta`, `dark:hidden`). Verifica que referencian `var(--token)` y que `dark:` genera un selector con `.dark`.
- **Paridad claro/oscuro**: el mismo test extrae los tokens de `:root` y `.dark` y comprueba que cada token de color existe en los dos.
- **Sin literales**: un test recorre `src/**/*.{ts,tsx}` (excluye `services/generated` y `*.test.*`) y falla ante `#rgb`/`#rrggbb`, `rgb(`, `hsl(`. La **lista de excepciones** va en el propio test, con el motivo de cada archivo: `dental-chart/utils/findingColor.ts` (NTS 188), `dental-chart/utils/chartPalette.ts` (papel fijo), `branding/presets.ts` (datos) y `core/theme/deriveTheme.ts` (genera los tokens del tema de cada clínica; agregado en el apply). El regex de hex no usa `` al final: dentro de una clase arbitraria al hex le sigue `_` (`#671a86_0%`), que `` toma como parte de la palabra. Si un archivo de la lista ya no tiene literales, el test falla y pide sacarlo. El regex cubre también funciones de color modernas (`oklch`, `lab`, `hwb`, `color()`…) y espacio antes del paréntesis; no busca colores con nombre, que chocan con clases legítimas (`text-white`). *(Revisión externa.)*

### D8. Revisión visual

Login, registro, listado y ficha del paciente, Historia Clínica, odontograma, consultas y branding, en claro y oscuro, con y sin un preset de marca aplicado, a 375, 768 y 1280 px. Se comparan contra capturas tomadas antes de migrar.

## Risks / Trade-offs

- **Diferencias visuales sutiles** (sombras y radios reescalados, `color-mix` en vez de `hsl/α`) → revisión de D8 con capturas de antes y después. *Medido al aplicar:* 84 pares idénticos con umbral 12; con umbral 0, diferencia máxima de **1/255 por canal** en 1878 píxeles (redondeo de `color-mix` en las pantallas con `bg-primary/5` y `/10`). *(Revisión externa.)*
- **El upgrade tool reescribe de más o de menos** → árbol limpio y diff revisado archivo por archivo.
- **Navegadores viejos** sin `@property`/`color-mix` → requisito de v4 aceptado.
- **`@tailwindcss/node` es una API de bajo nivel** → solo se usa en tests; si cambia, el test falla de forma visible, no en silencio.
- **Animaciones de Radix** si `tw-animate-css` no cubre alguna clase de `tailwindcss-animate` → se revisa en D8 abriendo diálogos y tooltips.

## Migration Plan

Solo frontend. Merge = despliegue. Rollback = revertir el PR (vuelven la configuración JS, PostCSS y las dependencias v3).
