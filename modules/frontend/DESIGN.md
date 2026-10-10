---
name: Dentdesk
description: Plataforma de gestión para clínicas dentales — la confianza clínica hecha software
colors:
  primary: "#7f1aa6"
  primary-foreground: "#ffffff"
  secondary-bg: "#e5f7f5"
  secondary-foreground: "#085e57"
  accent-bg: "#dbf6f3"
  accent-foreground: "#085e57"
  neutral-bg: "#ffffff"
  neutral-fg: "#241727"
  neutral-muted-bg: "#f7f3f8"
  neutral-muted-fg: "#736175"
  neutral-border: "#e5dbe7"
  ring: "#9f21cf"
  destructive: "#d32222"
  success: "#2c965c"
  warning: "#e18b0a"
typography:
  body:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  heading:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.01em"
rounded:
  sm: "6px"
  md: "8px"
  lg: "10px"
  full: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.primary-foreground}"
    rounded: "{rounded.sm}"
    padding: "8px 16px"
    height: "40px"
  button-primary-hover:
    backgroundColor: "{colors.primary}"
  button-outline:
    backgroundColor: "{colors.neutral-bg}"
    textColor: "{colors.neutral-fg}"
    rounded: "{rounded.sm}"
  card:
    backgroundColor: "{colors.neutral-bg}"
    rounded: "{rounded.lg}"
    padding: "24px"
  input:
    backgroundColor: "{colors.neutral-bg}"
    rounded: "{rounded.sm}"
    height: "40px"
    padding: "8px 12px"
---

# Design System: Dentdesk

## 1. Overview

**Creative North Star: "La Confianza Clínica"**

Dentdesk es el software de gestión para clínicas dentales — no un producto de consumo, ni un panel
de ingeniería. Cada pantalla la usa el staff de la clínica (administración, recepción, odontólogos)
mientras atiende a una persona real, así que el sistema tiene que sostener dos cosas a la vez: la
seriedad de manejar historia clínica (datos sensibles, retención legal, consecuencias reales de un
error) y la calidez de una recepción diaria (rápido, sin fricción, sin sonar a hospital frío). El
púrpura amatista es la firma — inusual para software B2B (que por reflejo va a azul corporativo),
deliberadamente cálido y con carácter; el teal menta es el respiro que lo enfría cuando hace falta.

Esto **rechaza** explícitamente: el azul-SaaS genérico (todo el software B2B se ve igual), el gris
clínico-frío de "software de hospital de los 2000", y el gradiente cian-magenta que produce
"parece hecho por IA" cuando se usa como panel de marca (retirado del login en una revisión reciente
precisamente por esto).

**Key Characteristics:**
- Superficies planas por defecto; la sombra aparece solo como respuesta a un estado, nunca decorativa.
- Esquinas suavemente redondeadas (6-10px) — ni afiladas tipo dashboard corporativo, ni pastilla
  extrema tipo app de consumo.
- Un solo acento de marca (el amatista) hace el trabajo pesado; el teal es de apoyo, no compite.
- Sin webfont custom: stack de fuente del sistema (rápido, sin parpadeo de carga, se siente nativo
  del SO).
- El color de marca es **personalizable por organización**, pero solo desde un catálogo curado —
  nunca un picker de hex libre (ver Named Rule en Colores).

## 2. Colors

La paleta tiene un solo acento saturado (el amatista) y un segundo tono de apoyo mucho más suave (el
teal, casi pastel) — no es una paleta de "3-4 colores compitiendo", es un acento con un respiro.

### Primary
- **Amatista Clínica** (`#7f1aa6`, hsl(292 72% 36%)): el color de marca. Botones primarios, enlaces,
  el anillo de foco, la barra de progreso. Se usa con moderación — no cubre superficies grandes, marca
  la acción, no decora.

### Secondary
- **Menta Clínica** (fondo `#e5f7f5` / texto `#085e57`, hsl(174 44% 93%) / hsl(175 84% 20%)): el
  respiro. Fondos de píldora, botones secundarios, superficies de apoyo — nunca el protagonista.

### Neutral
- **Blanco** (`#ffffff`): superficie base (fondo, card, popover).
- **Tinta Amatista** (`#241727`, hsl(289 26% 12%)): texto principal — un negro con un ligerísimo
  sesgo hacia el hue de marca, no un gris puro.
- **Bruma** (`#f7f3f8` / `#736175`): fondos muted (skeletons, filas alternas) y su texto secundario.
- **Borde Amatista** (`#e5dbe7`): bordes y separadores — con el mismo sesgo de hue que la tinta.

### Semantic (no themeable — nunca cambian con la marca de la organización)
- **Destructivo** (`#d32222`): errores, acciones irreversibles.
- **Éxito** (`#2c965c`): confirmaciones, estado activo.
- **Aviso** (`#e18b0a`): advertencias no bloqueantes (ej. contraste insuficiente al elegir marca).

### Named Rules
**La Regla del Catálogo Curado.** El color de marca de una organización se elige de un catálogo
fijo de presets pre-aprobados (Teal, Violeta, Índigo, Esmeralda, Ámbar, Rosa, Pizarra) — nunca un
`<input type="color">` libre. Cada preset ya garantiza contraste AA por construcción; el editor no
puede producir una marca ilegible ni fea. La identidad de producto (esta paleta, este documento) es
fija y no se tematiza; solo el acento de marca fluye por organización.

**La Regla del Semántico Intocable.** Éxito/aviso/error nunca adoptan el color de marca de ninguna
organización. Un botón "eliminar" siempre es rojo, sin importar qué tan violeta sea el resto de la
pantalla.

## 3. Typography

**Body Font:** ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto (stack de sistema)
**Display Font:** la misma familia, en peso 700 — no hay un segundo family para títulos

**Character:** Sin webfont importado, a propósito — el sistema confía en la fuente nativa del SO en
vez de comprometerse a una identidad tipográfica editorial. Encaja con el registro "herramienta de
trabajo diario", no "producto de marca".

### Hierarchy
- **Heading** (700, 1.5rem/24px, line-height 1.2, tracking -0.01em): títulos de pantalla (`h1` de
  cada screen).
- **Title** (600, 1.125rem/18px): títulos de tarjeta, encabezados de diálogo.
- **Body** (400, 0.875rem/14px, line-height 1.5): el tamaño por defecto de casi toda la UI — labels,
  inputs, texto de tabla. Deliberadamente más chico que el body-de-lectura web (14px, no 16px):
  es una herramienta densa de uso diario, no contenido para leer largo.
- **Label** (500, 0.75-0.875rem): etiquetas de campo, badges, eyebrows.

## 4. Elevation

Sistema plano por defecto. Las tarjetas (`Card`) usan una sombra mínima (`shadow-sm`) casi
imperceptible en reposo — la separación de superficies la hace el borde (`border-border`), no la
sombra. La sombra sube de intensidad solo como respuesta a un estado (un diálogo modal, nunca una
tarjeta en reposo).

### Shadow Vocabulary
- **Ambient** (`shadow-sm`, ~0 1px 2px rgba(0,0,0,0.05)): el único nivel usado en reposo — cards,
  contenedores de lista.
- **Modal** (`shadow-lg`): diálogos (`DialogContent`) — el único elemento que se "levanta" de verdad,
  con un scrim `bg-black/80` detrás.

### Named Rules
**La Regla del Plano-Por-Defecto.** Las superficies son planas en reposo. La sombra existe para
señalar una capa flotante real (un modal sobre el contenido), no para decorar una tarjeta que ya está
delimitada por su borde.

## 5. Components

### Buttons
- **Shape:** radio suave (`rounded-md`, 6px) — ni afilado ni pastilla.
- **Primary:** fondo amatista sólido, texto blanco, `h-10` (40px) por defecto, `h-11` (44px, el
  mínimo táctil) en flujos críticos como el login.
- **Hover / Focus:** el primario oscurece 10% en hover (`hover:bg-primary/90`); el foco es un anillo
  de 2px en el color de marca con offset de 2px sobre el fondo — visible siempre, nunca suprimido.
- **Secondary / Outline / Ghost:** outline usa un borde neutro + fondo base, sube a `accent` en
  hover; ghost no tiene fondo hasta el hover. Ambos conservan el mismo anillo de foco que el primario.
- **Destructive:** mismo shape, fondo semántico rojo — reservado para acciones irreversibles de
  verdad (nunca "dar de baja", que es reversible).

### Cards / Containers
- **Corner Style:** `rounded-xl` (10px) — más redondeado que botones/inputs, para que una tarjeta se
  lea como un contenedor, no como un control.
- **Background:** blanco puro, borde neutro sutil.
- **Shadow Strategy:** `shadow-sm` únicamente (ver Elevación).
- **Internal Padding:** header/content en `p-6` (24px), con `gap-1.5` (6px) entre título y
  descripción.

### Inputs / Fields
- **Style:** borde neutro, fondo base, `rounded-md` (6px), `h-10` (40px) por defecto, `h-11` en
  formularios de auth.
- **Focus:** anillo de 2px en el color de marca, igual que los botones — consistencia de foco en
  toda la superficie interactiva.
- **Error:** borde y anillo cambian a rojo semántico vía `aria-invalid`, nunca solo color (el mensaje
  de error siempre acompaña).
- **Con ícono:** el ícono decorativo va a la izquierda, `text-muted-foreground`, nunca compite con el
  valor tecleado.

### Dialogs
- **Style:** centrado, `rounded-lg`, scrim `bg-black/80` (fuerte, aísla el contenido de verdad),
  entra con fade + zoom sutil (95%→100%).
- **Estructura:** título + descripción arriba, acciones abajo a la derecha (cancelar como `ghost`,
  confirmar como `default` o `destructive` según la consecuencia).
- **Foco atrapado** dentro del diálogo (heredado de Radix), cierre con `Esc`.

### Badges / Chips
- **Style:** píldora completa (`rounded-full`), fondo tenue del color semántico + texto del mismo
  tono más oscuro (ej. estado activo: fondo éxito/10, texto éxito) — nunca el color semántico sólido
  a pantalla completa en un chip pequeño.

### Marca de organización (Componente firma)
El color primario de toda la interfaz autenticada se deriva del preset de marca elegido por cada
organización (ver Colores → Regla del Catálogo Curado). El resto del sistema —tipografía, radios,
sombras, semánticos— nunca cambia. Es el único punto de personalización real, y es intencionalmente
angosto.

## 6. Do's and Don'ts

### Do:
- **Do** usar el amatista (`#7f1aa6`) con moderación — botones de acción, foco, enlaces. No pintar
  superficies grandes con él.
- **Do** mantener las tarjetas planas en reposo (`shadow-sm` como techo); reservar sombra fuerte para
  capas flotantes reales (modales).
- **Do** ofrecer el color de marca de una organización solo desde el catálogo curado de presets —
  nunca un input de color libre.
- **Do** mantener el foco visible (anillo de 2px, offset 2px) en todo elemento interactivo, sin
  excepción.
- **Do** usar 14px como tamaño de texto por defecto en la UI de trabajo (tablas, formularios,
  labels) — es una herramienta densa, no contenido editorial.

### Don't:
- **Don't** usar gradientes decorativos tipo cian→magenta como fondo de marca — se lee como "hecho
  por IA" (ya se retiró del panel de login del login por esta razón exacta).
- **Don't** dejar que el color semántico (éxito/aviso/error) cambie con la marca de ninguna
  organización — son intocables.
- **Don't** usar `border-left`/`border-right` de más de 1px como acento decorativo en cards o alerts.
- **Don't** anidar cards dentro de cards.
- **Don't** usar un tamaño de tap/click menor a 44px en flujos críticos (login, acciones de alto
  costo de error).
- **Don't** suprimir el anillo de foco por estética — es accesibilidad, no un detalle visual opcional.
