## 1. Base de datos

- [ ] 1.1 Crear `V8__organization_branding.sql`: tabla `organization_branding` (`organization_id` PK/FK → `organizations`, `ON DELETE CASCADE`; `brand_primary`, `brand_secondary`, `logo_url`, `favicon_url`, todos nullable). **Sin RLS** (como `organization_settings`); sin backfill
- [ ] 1.2 GRANT de la tabla al rol de app (o confiar en `ALTER DEFAULT PRIVILEGES` de `V6`)

## 2. Backend

- [ ] 2.1 `persistence/entity/OrganizationBranding.java` (1:1 con `Organization` vía `@MapsId`, campos nullable) + `OrganizationBrandingRepository`
- [ ] 2.2 `service/organization/BrandingService`: leer la marca de la organización del contexto; actualizarla (upsert). Validación de negocio mínima
- [ ] 2.3 `presentation/dto`: `BrandingResponse` y `UpdateBrandingRequest` (color `#RRGGBB` con `@Pattern`, URLs con validación de formato; campos nullable)
- [ ] 2.4 `presentation/controller/BrandingController`: `GET /organizations/me/branding` (autenticado) y `PUT /organizations/me/branding` (**solo `ADMIN`** — autorización por rol); el `me` = organización del contexto, nunca de la petición
- [ ] 2.5 Tests backend (slice web + service): actualización por ADMIN, denegación a no-ADMIN, validación de color/URL, restaurar campo a null

## 3. Contrato y tipos

- [ ] 3.1 Regenerar `contracts/openapi.json` (desde el backend real) y el cliente orval (`pnpm generate:api`); comprobar el gate "sin drift"

## 4. Frontend — derivación de tema (el núcleo)

- [ ] 4.1 Utilidad `deriveTheme(colors)` en `modules/core`: hex→HSL, mapeo de tokens de marca/neutros/semánticos (design.md D3), produciendo mapas **claro y oscuro**
- [ ] 4.2 Función de contraste WCAG (luminancia relativa) que elige `-foreground` (design.md D4) y expone si el color no alcanza AA (para el aviso del editor)
- [ ] 4.3 Tests unitarios de la derivación (Vitest): color oscuro→foreground claro; color claro→foreground oscuro; semánticos intactos; neutros conservan S/L (solo cambia hue); flag de contraste insuficiente

## 5. Frontend — aplicación en runtime

- [ ] 5.1 Hook `useBranding`: obtiene la marca (`GET /organizations/me/branding`) durante el **bootstrap** (splash de `RootLayout`), aplica CSS vars en `:root`/`.dark`, sustituye `<link rel="icon">` y expone el logo; **resetea a defaults en logout** (design.md D5, D7)
- [ ] 5.2 Integrar el logo de la organización en la cabecera del app shell (usa `logoUrl` o el logo del producto por defecto)
- [ ] 5.3 Verificar que las pantallas de **auth conservan la marca del producto** (no aplican branding)
- [ ] 5.4 Tests (Vitest + MSW): tema aplicado al montar con sesión; reset en logout; auth sin branding

## 6. Frontend — pantalla de ajustes (ADMIN)

- [ ] 6.1 Ruta/pantalla de **marca** protegida por rol `ADMIN` (RouteGuard): color picker(s), inputs de URL de logo/favicon, guardar
- [ ] 6.2 **Vista previa en vivo**: aplica `deriveTheme` sin persistir; aviso de contraste insuficiente
- [ ] 6.3 Hook TanStack Query (mutación) sobre el fetcher generado; al guardar, refetch/aplicar la marca; toasts; validación Zod en paridad con el backend
- [ ] 6.4 Tests (Vitest + MSW): guardar como ADMIN aplica el tema; validación en cliente; preview no persiste

## 7. Documentación (al archivar)

- [ ] 7.1 `docs/domain.md`: entidad `OrganizationBranding` (1:1 con `Organization`, sin RLS)
- [ ] 7.2 `docs/frontend.md`: mecanismo de tematización por organización (tokens CSS, `deriveTheme`, `useBranding`, contraste)
- [ ] 7.3 `docs/vision.md` + `readme`: marcar `organization-branding` como construida (estado)

## 8. Cierre

- [ ] 8.1 `openspec validate add-organization-branding --strict`
- [ ] 8.2 `mvn verify` (backend) y `pnpm validate` (frontend) en verde
- [ ] 8.3 Resolver las Open Questions del `design.md` (espacio de color, uno/dos colores, cache de tema, formato de favicon)
- [ ] 8.4 Commits por scope (backend / frontend / docs / openspec), Conventional Commits en español
