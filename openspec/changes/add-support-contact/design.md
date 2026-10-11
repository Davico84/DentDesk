# Design — add-support-contact

## Context

- Referencia: HSO, `openspec/changes/archive/2026-10-06-add-support-contact` de `D:/proyectos/HSOFACOP`. Allá el contacto vive en `project.config.json`, el archivo de marca de su **plantilla** (con esquema, validador y un CI que ensaya la plantilla con otra identidad). Dentdesk no tiene esa plantilla: su marca de producto está en código (`core/ui/logo`, `BrandPanel`, tokens `--brand-*`).
- `BrandPanel` (`core/components`, de `add-cold-start-warmup`) solo se ve desde `lg` (1024 px); en celular el login muestra el logo arriba y el formulario.
- `ServerWarmupScreen`, en `stuck`, dice "Si el problema continúa, avisa al administrador."; en escritorio también muestra el `BrandPanel`.
- Contacto decidido por el usuario: WhatsApp `51959396384`, correo `davicova84@gmail.com`.

## Goals / Non-Goals

**Goals:** un contacto de soporte visible en las pantallas sin sesión (escritorio y celular) y en la espera larga, sin duplicarse en la misma vista.

**Non-Goals:** los del proposal.

## Decisions

### D1. El contacto es una constante del producto

`core/config/supportContact.ts` exporta `supportContact = { whatsapp: "51959396384", email: "davicova84@gmail.com" }` (tipado `as const`) y `whatsappUrl(text)`. Es el soporte **de Dentdesk**: no cambia por clínica, así que no va en la base ni en la marca de la organización.

- Un test fija el formato: `whatsapp` solo dígitos, 8–15 (el que pide `wa.me`: código de país sin `+` ni espacios); `email` con forma de correo. Es la validación que en HSO hacía su esquema de configuración.

*Descartado:* variables de entorno de Vite (`VITE_SUPPORT_*`). Agregan un paso más al despliegue en Vercel y un estado "sin configurar" que aquí no hace falta: el producto siempre tiene un soporte. Cambiar el contacto = editar la constante y desplegar.

*Descartado:* el `project.config.json` de HSO: es infraestructura de su plantilla, que Dentdesk no usa.

### D2. `ContactLinks` genérico en `core/components`

- Recibe el contacto por props (`whatsapp`, `email`, `product`), no lo importa: así es genérico y se prueba con cualquier dato. Quien lo usa le pasa `supportContact`.
- Variante visual `tone: "onBrand" | "default"`: sobre el panel de marca, texto `text-white/80` con foco blanco; sobre el fondo, `text-muted-foreground` y el anillo `ring`. `showTitle` opcional (en la espera larga el título ya lo da la frase de antes).
- WhatsApp: `https://wa.me/<dígitos>?text=<encodeURIComponent("Hola, necesito ayuda con Dentdesk")>`, `target="_blank"`, `rel="noopener noreferrer"`. El nombre accesible avisa la pestaña nueva: texto visible "Escríbenos por WhatsApp" + `<span class="sr-only">(se abre en otra pestaña)</span>`.
- Correo: `mailto:<email>`, misma pestaña; el texto visible es la dirección.
- Íconos de `lucide-react` (`MessageCircle`, `Mail`) con `aria-hidden`.
- Estructura: título "¿Necesitas ayuda?" y una lista (`<ul>`) de enlaces.

### D3. Dónde se muestra

| Pantalla | Escritorio (≥ 1024 px) | Celular |
|---|---|---|
| Login, registro | en el `BrandPanel`, bajo la frase (tono `onBrand`, separado con `border-white/20`) | debajo del formulario y del pie (tono `default`, `lg:hidden`) |
| Espera 4–90 s | en el `BrandPanel` | — |
| Espera > 90 s (`stuck`) | panel **y** "Si el problema continúa, escríbenos:" bajo "Reintentar" | "Si el problema continúa, escríbenos:" bajo "Reintentar" |
| Sin red (`offline`) | en el `BrandPanel` | — |

- En `stuck` se acepta la repetición en escritorio: en ese estado el contacto es la acción principal después de "Reintentar".
- En `offline` no se agrega bajo el mensaje: sin red, WhatsApp y el correo no se abren de forma útil. (En escritorio sigue en el panel, que es el mismo de siempre.)

## Risks / Trade-offs

- **Datos públicos**: los bots recogen correos y números expuestos en la web. Mitigación a futuro: un contacto de soporte propio de Dentdesk, separado del personal; es editar la constante.
- `wa.me` sin WhatsApp instalado abre WhatsApp Web: aceptable.
- El panel de marca crece hacia abajo: se revisa con capturas que el pie "© Dentdesk" no se encime a 1024 px de alto 700.
