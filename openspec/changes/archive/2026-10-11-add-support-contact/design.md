# Design — add-support-contact

## Context

- Referencia: HSO, `openspec/changes/archive/2026-10-06-add-support-contact` de `D:/proyectos/HSOFACOP`. Allá el contacto vive en `project.config.json`, el archivo de marca de su **plantilla**, y se muestra como una línea de enlaces en el panel de marca. Dentdesk no tiene esa plantilla: su marca de producto está en código.
- **Forma elegida por el usuario** en el lienzo de diseño "Contacto de soporte Dentdesk" (claude.ai/artifact/QgsUCHmE1UTPC2Rc2KyMDj), en dos rondas:
  1. primero la opción B (botón discreto que despliega una tarjeta con persona, horario y dos botones), implementada y probada;
  2. al verla, el usuario la encontró con demasiada información y espacio, y eligió la **C2 · Fila compacta**: una píldora con la pregunta y dos botones redondos de solo ícono. Descartadas C1 (una línea de enlaces con texto) y C3 (desplegable mínimo).
- `ServerWarmupScreen`, en `stuck`, decía "Si el problema continúa, avisa al administrador.".
- Contacto: WhatsApp `51959396384`, correo `davicova84@gmail.com` (decisión del usuario). Sin nombre ni horario a la vista (salieron con la C2).

## Goals / Non-Goals

**Goals:** que quien no puede entrar vea cómo escribir a soporte sin recargar el login (una fila de ~60 px), y que la espera larga lo ofrezca de entrada.

**Non-Goals:** los del proposal.

## Decisions

### D1. El contacto es una constante del producto

`core/config/supportContact.ts` exporta `supportContact = { whatsapp, email, product }` (`as const`) y `whatsappUrl(whatsapp, text)`. Es el soporte **de Dentdesk**: no cambia por clínica, así que no va en la base ni en la marca de la organización.

- Un test fija el formato: `whatsapp` solo dígitos, 8–15 (el que pide `wa.me`: código de país sin `+` ni espacios); `email` con forma de correo.

*Descartado:* variables de entorno de Vite (un paso más en Vercel y un estado "sin configurar" que el producto no necesita) y el `project.config.json` de HSO (infraestructura de su plantilla).

### D2. `SupportRow`: la fila compacta, genérica

`core/components/SupportRow`: recibe `whatsapp`, `email`, `product` y un `lead` opcional (la pregunta: "¿Problemas para entrar?" por defecto, "¿Sigue sin entrar?" en la espera). No importa la constante: quien la usa le pasa `supportContact`.

- Una `<section aria-label="Soporte">` en forma de píldora (`rounded-full`, borde, `bg-muted/40`): la pregunta a la izquierda y dos botones redondos de 44 px a la derecha.
- **WhatsApp**: botón primario (`bg-primary`), `target="_blank"`, `rel="noopener noreferrer"`, `aria-label` "Escribir a soporte por WhatsApp (se abre en otra pestaña)", `https://wa.me/<dígitos>?text=<encodeURIComponent("Hola, necesito ayuda con Dentdesk")>`.
- **Correo**: botón con borde, `mailto:`, `aria-label` "Escribir a soporte por correo, <dirección>".
- **Solo ícono** (`MessageCircle`, `Mail`, `aria-hidden`): el nombre va en `aria-label` y, a la vista, en un tooltip (`HintTooltip`, nunca `title`, `docs/frontend.md` §4.1). Como el tooltip repite lo que ya dice el nombre, va con `repeatsLabel` para no anunciarlo dos veces.
- Solo tokens, con modo oscuro.

*Descartado:* la tarjeta con persona y horario (opción B, primera ronda): el usuario la encontró con demasiada información para un caso poco frecuente.

### D3. Dónde se muestra

| Pantalla | Qué |
|---|---|
| Login, registro | `SupportRow` bajo el pie del formulario, siempre a la vista (escritorio y celular) |
| Espera 4–90 s | nada (el servidor está despertando; aún no hay problema que reportar) |
| Espera > 90 s (`stuck`) | `SupportRow` bajo "Reintentar", con "¿Sigue sin entrar?" |
| Sin red (`offline`) | nada: sin red, WhatsApp y el correo no se abren de forma útil |

El panel de marca (`BrandPanel`) no cambia.

## Risks / Trade-offs

- **Botones de solo ícono**: menos explícitos que con texto. Mitigación: la pregunta al lado da el contexto, los íconos son los convencionales (globo de conversación, sobre) y el tooltip nombra cada uno.
- **Datos públicos**: el número y el correo quedan en una página pública; los recogen bots. Mitigación a futuro: un contacto de soporte propio de Dentdesk; es editar la constante.
- `wa.me` sin WhatsApp instalado abre WhatsApp Web: aceptable.
