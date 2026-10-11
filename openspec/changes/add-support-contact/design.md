# Design — add-support-contact

## Context

- Referencia: HSO, `openspec/changes/archive/2026-10-06-add-support-contact` de `D:/proyectos/HSOFACOP`. Allá el contacto vive en `project.config.json`, el archivo de marca de su **plantilla**, y se muestra como una línea de enlaces en el panel de marca. Dentdesk no tiene esa plantilla: su marca de producto está en código.
- **Forma elegida por el usuario**: opción B del lienzo de diseño "Contacto de soporte Dentdesk" (claude.ai/artifact/QgsUCHmE1UTPC2Rc2KyMDj): un botón discreto bajo el formulario que despliega una tarjeta con persona, horario y dos botones. Descartada la A (tarjeta fija en el panel de marca): siempre a la vista, pero recarga el login para el caso común, en que nadie necesita soporte.
- `ServerWarmupScreen`, en `stuck`, dice "Si el problema continúa, avisa al administrador.".
- Datos: David Velarde · Soporte Dentdesk, de 9:00 a 20:00 (el horario en que el servidor está despierto, `docs/deployment.md` §3), WhatsApp `51959396384`, correo `davicova84@gmail.com`.

## Goals / Non-Goals

**Goals:** que quien no puede entrar encuentre a quién escribir y sepa cuándo le responden, sin recargar el login; y que la espera larga lo ofrezca de entrada.

**Non-Goals:** los del proposal.

## Decisions

### D1. El contacto es una constante del producto

`core/config/supportContact.ts` exporta:

```ts
export const supportContact = {
  name: "David Velarde",
  role: "Soporte Dentdesk",
  hours: "de 9:00 a 20:00",
  whatsapp: "51959396384",
  email: "davicova84@gmail.com",
} as const;
export function whatsappUrl(whatsapp: string, text: string): string;
```

- Un test fija el formato: `whatsapp` solo dígitos, 8–15 (el que pide `wa.me`: código de país sin `+` ni espacios); `email` con forma de correo.
- Las iniciales salen de `initialsOf(name)` (`core/utils/initials`, de `add-app-shell`): "DV".

*Descartado:* variables de entorno de Vite. Un paso más en Vercel y un estado "sin configurar" que el producto no necesita. *Descartado:* el `project.config.json` de HSO, infraestructura de su plantilla.

### D2. `SupportCard`: la tarjeta, genérica

`core/components/SupportCard`: recibe por props `name`, `role`, `hours`, `whatsapp`, `email`, `product` y un `lead` opcional (la frase de arriba: "¿Necesitas ayuda? Te atiende" en el login, "¿Sigue sin entrar? Escríbeme" en la espera). No importa la constante: quien la usa le pasa `supportContact`, así es genérica y se prueba con cualquier dato.

- Las iniciales en un círculo (`Monogram` de `core/components`, tono `secondary`), el nombre y rol en negrita, el horario debajo.
- Dos botones-enlace de 44 px en fila: **WhatsApp** (ícono de globo de conversación, `target="_blank"`, `rel="noopener noreferrer"`, texto visible "WhatsApp" + `<span class="sr-only">(se abre en otra pestaña)</span>`, `https://wa.me/<dígitos>?text=<encodeURIComponent("Hola, necesito ayuda con Dentdesk")>`) y **Correo** (`mailto:`, misma pestaña). Íconos de `lucide-react` (`MessageCircle`, `Mail`) con `aria-hidden`.
- Superficie `bg-muted/40` con borde y `rounded-2xl`, como las tarjetas de la app; solo tokens, con modo oscuro.
- Es una `<section aria-label="Soporte">`.

### D3. `SupportDisclosure`: el botón que la despliega

`core/components/SupportDisclosure`: un `<button>` de 44 px de alto, en forma de píldora discreta (borde, fondo `muted`, ícono de ayuda), con "¿Problemas para entrar? Habla con soporte", `aria-expanded` y `aria-controls` apuntando a la tarjeta. Al pulsarlo, la tarjeta aparece **debajo, en el flujo** (no flotante).

- *Por qué en el flujo y no un popover flotante* (como lo dibuja la maqueta): no suma dependencias (no hay `popover` en `core/ui`), no tapa el formulario en celular y no necesita manejar foco ni `Escape`. La maqueta mostraba la idea; el comportamiento real es un acordeón de un solo panel.
- La animación de aparición respeta "reducir movimiento" (`MotionConfig` de `RootLayout`).
- Mismo componente en escritorio y celular: no hay versión por ancho que pueda duplicarse.

### D4. Dónde se muestra

| Pantalla | Qué |
|---|---|
| Login, registro | `SupportDisclosure` bajo el pie del formulario ("¿No tienes cuenta?…"), cerrado al entrar |
| Espera 4–90 s | nada (el servidor está despertando; aún no hay problema que reportar) |
| Espera > 90 s (`stuck`) | `SupportCard` abierta bajo "Reintentar", con `lead` "¿Sigue sin entrar? Escríbeme" |
| Sin red (`offline`) | nada: sin red, WhatsApp y el correo no se abren de forma útil |

El panel de marca (`BrandPanel`) no cambia.

## Risks / Trade-offs

- **Datos públicos**: el nombre, el número y el correo quedan en una página pública; los recogen bots. Mitigación a futuro: un contacto de soporte propio de Dentdesk; es editar la constante.
- **Menos visible que la tarjeta fija** (opción A): quien está trabado tiene que ver el botón. Mitigación: va justo bajo el formulario, con texto que nombra el problema ("¿Problemas para entrar?"); y en la espera larga, el caso más probable de "no puedo entrar", la tarjeta está abierta de entrada.
- `wa.me` sin WhatsApp instalado abre WhatsApp Web: aceptable.
