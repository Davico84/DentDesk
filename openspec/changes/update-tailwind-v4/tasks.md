> Solo frontend. Rama apilada sobre `update-api-docs-exposure` (comparten `docs/vision.md` §8): mergear ese PR primero.

## 1. Antes de migrar

- [x] 1.1 Capturas de referencia (design D8): login, registro, listado y ficha del paciente, Historia Clínica, odontograma, consultas y branding; claro y oscuro; con y sin un preset de marca; 375, 768 y 1280 px
- [x] 1.2 Test de colores literales con la lista de excepciones (design D7): debe fallar hoy por los literales de `modules/auth`

## 2. Migración

- [x] 2.1 `npx @tailwindcss/upgrade` sobre árbol limpio y revisión del diff archivo por archivo (design D6)
- [x] 2.2 `@tailwindcss/vite` en `vite.config.ts`; borrar `tailwind.config.cjs`, `postcss.config.cjs`, `autoprefixer`, `tailwindcss-animate`; agregar `tw-animate-css` y `@tailwindcss/node` (dev) (design D1)
- [x] 2.3 `globals.css`: tokens con color completo, `@theme inline`, radios, `@custom-variant dark` (design D2/D4)
- [x] 2.4 Revisar `ring` sueltos, `outline-none`, sombras y radios renombrados (design D6)

## 3. Tema de la clínica y login

- [x] 3.1 `deriveTheme` emite `hsl(H S% L%)`; ajustar `deriveTheme.test.ts` y `ThemeManager.test.ts`; test de que el tema derivado no incluye `--brand-*` (design D3; scenario *El tema de la organización emite tokens válidos*)
- [x] 3.2 Tokens `--brand-*` en `globals.css` con los valores de la tabla de design D5
- [x] 3.3 `AuthSubmitButton`, `LoginForm`, `RegisterForm`, `usePasswordStrength` → utilidades `brand-*`; `SmileArcBackground` lee el token y usa `globalAlpha` (design D5)
- [x] 3.4 El test 1.2 pasa (scenarios *Sin colores literales fuera de las excepciones* y *La lista de excepciones no esconde archivos…*)

## 4. Tests del contrato y verificación

- [x] 4.1 Test de compilación real con `@tailwindcss/node`: utilidades desde tokens, `bg-primary/10`, `bg-brand-cta`, `dark:` por clase; paridad de tokens claro/oscuro (design D7; scenarios de *Tema definido por tokens CSS*)
- [x] 4.2 Revisión visual contra las capturas de 1.1, incluidos diálogos y tooltips (animaciones), y el login con un preset aplicado (scenario *Los colores del login no cambian con el tema de la clínica*)
- [x] 4.3 `pnpm validate` verde; `pnpm build` sin warnings nuevos; e2e smoke verde

## 5. Docs y cierre

- [x] 5.1 `docs/frontend.md` §4.5 (formato de color completo, `@theme inline`, tokens `brand-*`, lista de excepciones) y §4.5.1 (el tema de la clínica emite color completo); `docs/architecture.md` y `docs/adr/0001` (Tailwind 4)
- [x] 5.2 `openspec validate update-tailwind-v4 --strict`
