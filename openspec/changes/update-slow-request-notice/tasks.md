> Solo frontend. Antes de crear o modificar cada componente, hook o store: skill `frontend-guard`.

## 1. Detección

- [ ] 1.1 `store/useSlowRequestStore` (ids lentos, `slowSince`, `waiting`) con sus tests (design D1)
- [ ] 1.2 Interceptores en `core/config/httpClient.ts`: timer de 4 s por petición, marcar lenta y limpiar en éxito y en error; tests con MSW (rápida sin marca; lenta marcada y limpiada al responder; dos lentas, el aviso sigue hasta la última; error limpia; sin timers pendientes)

## 2. Aviso

- [ ] 2.1 `core/components/SlowRequestNotice` con los estados "Despertando el servidor…", "Está tardando más de lo normal" (Reintentar + `SupportRow`) y "Sin conexión a internet", reutilizando los umbrales de `core/utils/serverWarmup.ts` (design D2)
- [ ] 2.2 `RootLayout` lo monta solo con el bootstrap terminado (design D3)
- [ ] 2.3 Tests por scenario (timers falsos, petición retenida con MSW): petición rápida sin aviso; lenta con aviso en `status` que desaparece al responder; varias lentas; error; 90 s con Reintentar y soporte; sin conexión; durante el arranque solo la pantalla de arranque
- [ ] 2.4 Capturas del aviso (los tres estados) en claro y oscuro, 375 y 1280 px; `pnpm validate` verde

## 3. Docs

- [ ] 3.1 `docs/frontend.md` (el aviso global vive en el cliente HTTP; no hace falta en cada pantalla) y `docs/deployment.md` §1
- [ ] 3.2 `openspec validate update-slow-request-notice --strict`
