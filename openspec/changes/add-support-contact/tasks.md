> Solo frontend. Antes de crear o modificar cada componente: skill `frontend-guard`. Forma visual: opción B del lienzo "Contacto de soporte Dentdesk".

## 1. Contacto

- [x] 1.1 `core/config/supportContact.ts` (nombre, rol, horario, WhatsApp, correo) y `whatsappUrl`; test del formato (WhatsApp 8–15 dígitos, correo válido) y de la URL con el mensaje codificado (design D1)
- [x] 1.2 `core/components/SupportCard` con sus tests: iniciales, nombre, horario, `lead`; WhatsApp con `target`/`rel` y aviso de pestaña nueva en el nombre accesible; `mailto:` (design D2)
- [x] 1.3 `core/components/SupportDisclosure` con sus tests: cerrado al inicio, abre y cierra con `aria-expanded`, `aria-controls` apunta a la tarjeta (design D3)

## 2. Pantallas

- [x] 2.1 `AuthLayout`: `SupportDisclosure` bajo el pie del formulario, en login y registro (design D4)
- [x] 2.2 `ServerWarmupScreen` en `stuck`: `SupportCard` abierta bajo "Reintentar" con "¿Sigue sin entrar? Escríbeme"; nada en `offline` ni en `warming`
- [x] 2.3 Tests por scenario en login/registro y en la espera (sin "avisa al administrador"; sin tarjeta en `offline`)
- [x] 2.4 Capturas de login (cerrado y abierto), registro y espera larga, en claro y oscuro, a 375 y 1280 px, comparadas con la maqueta; `pnpm validate` verde

## 3. Docs

- [x] 3.1 `docs/deployment.md` §7 y `docs/vision.md` §8 al día
- [x] 3.2 `openspec validate add-support-contact --strict`
