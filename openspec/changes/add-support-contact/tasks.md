> Solo frontend. Antes de crear o modificar cada componente: skill `frontend-guard`.

## 1. Contacto

- [ ] 1.1 `core/config/supportContact.ts` con el contacto y `whatsappUrl`; test del formato (WhatsApp 8–15 dígitos, correo válido) y de la URL con el mensaje codificado (design D1)
- [ ] 1.2 `core/components/ContactLinks` (props `whatsapp`, `email`, `product`, `tone`, `showTitle`) con sus tests: enlaces, `target`/`rel`, aviso de pestaña nueva en el nombre accesible, `mailto:` (design D2)

## 2. Pantallas

- [ ] 2.1 `BrandPanel`: el contacto bajo la frase, tono `onBrand` (design D3)
- [ ] 2.2 `AuthLayout`: el contacto bajo el formulario solo en celular (`lg:hidden`), tono `default`
- [ ] 2.3 `ServerWarmupScreen` en `stuck`: "Si el problema continúa, escríbenos:" + enlaces; sin contacto bajo el mensaje en `offline`
- [ ] 2.4 Tests por scenario: panel de marca con los enlaces; bloque de celular presente con `lg:hidden` (sin duplicarse en escritorio); espera larga sin "avisa al administrador"; `offline` sin enlaces bajo el mensaje
- [ ] 2.5 Capturas de login, registro y espera (`stuck`) en claro y oscuro, a 375 y 1280 px (y 1024×700 para el panel); `pnpm validate` verde

## 3. Docs

- [ ] 3.1 `docs/deployment.md` §7 y `docs/vision.md` §8 al día
- [ ] 3.2 `openspec validate add-support-contact --strict`
