> Solo frontend. Antes de crear o modificar cada componente: skill `frontend-guard`. Forma visual: C2 · Fila compacta del lienzo "Contacto de soporte Dentdesk" (la opción B, tarjeta desplegable, se implementó primero y se reemplazó).

## 1. Contacto

- [x] 1.1 `core/config/supportContact.ts` (WhatsApp, correo, producto) y `whatsappUrl`; test del formato (WhatsApp 8–15 dígitos, correo válido) y de la URL con el mensaje codificado (design D1)
- [x] 1.2 `core/components/SupportRow` con sus tests: pregunta, botones de solo ícono con nombre accesible y sin `title`, WhatsApp con `target`/`rel` y aviso de pestaña nueva, `mailto:` con la dirección, tooltip al enfocar, `lead` configurable (design D2)

## 2. Pantallas

- [x] 2.1 `AuthLayout`: `SupportRow` bajo el pie del formulario, en login y registro (design D3)
- [x] 2.2 `ServerWarmupScreen` en `stuck`: `SupportRow` bajo "Reintentar" con "¿Sigue sin entrar?"; nada en `offline` ni en `warming`
- [x] 2.3 Tests por scenario en login/registro y en la espera (sin "avisa al administrador"; sin fila en `offline`)
- [x] 2.4 Capturas de login y espera larga, en claro y oscuro, a 375 y 1280 px, comparadas con la maqueta C2; `pnpm validate` verde

## 3. Docs

- [x] 3.1 `docs/deployment.md` §7 y `docs/vision.md` §8 al día
- [x] 3.2 `openspec validate add-support-contact --strict`
