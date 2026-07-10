## 1. Base de datos

- [x] 1.1 Crear `V8__organization_branding.sql`: tabla `organization_branding` (`organization_id` PK/FK → `organizations`, `ON DELETE CASCADE`; `brand_primary`, `brand_secondary`, `logo_url`, todos nullable). **Sin RLS** (como `organization_settings`); sin backfill
- [x] 1.2 GRANT de la tabla al rol de app (o confiar en `ALTER DEFAULT PRIVILEGES` de `V6`)

## 2. Backend

- [x] 2.1 `persistence/entity/OrganizationBranding.java` (1:1 con `Organization` vía `@MapsId`, campos nullable) + `OrganizationBrandingRepository`
- [x] 2.2 `service/organization/BrandingService`: leer la marca de la organización del contexto; actualizarla (upsert). **Si tras la actualización todos los campos quedan `null`, ELIMINAR la fila** (sin personalización = sin fila, design.md D6)
- [x] 2.3 `presentation/dto`: `BrandingResponse` y `UpdateBrandingRequest` (color `#RRGGBB` con `@Pattern`, `logoUrl` **solo `https://`** con `@Pattern` estricto —rechaza javascript:/data:/http—; campos nullable) + **`@Schema(example = ...)`** en cada campo para que el contrato OpenAPI sea autoexplicativo
- [x] 2.4 `presentation/controller/BrandingController`: `GET /organizations/me/branding` (autenticado; devuelve siempre un objeto, exista o no la fila) y `PUT /organizations/me/branding` (**solo `ADMIN`** — autorización por rol); el `me` = organización del contexto, nunca de la petición
- [x] 2.5 Tests backend (slice web + service): actualización por ADMIN, denegación a no-ADMIN, validación de color, **rechazo de URL no-https (javascript:/data:/http)**, restaurar campo a null, **quitar toda la personalización elimina la fila**

## 3. Contrato y tipos

- [ ] 3.1 Regenerar `contracts/openapi.json` (desde el backend real) y el cliente orval (`pnpm generate:api`); comprobar el gate "sin drift"

## 4. Frontend — derivación de tema (el núcleo)

- [ ] 4.1 Utilidad `deriveTheme(colors)` en `modules/core`: hex→HSL, mapeo de tokens (design.md D3) con **lightness clamping** por modo (claro L>85%→80%; oscuro L<50%→55–65%) y **derivación del secundario** cuando es null (tinte del primario); mapas claro y oscuro
- [ ] 4.2 Función de contraste WCAG (luminancia relativa) que elige `-foreground` (design.md D4) y expone si el color no alcanza AA (para el aviso del editor)
- [ ] 4.3 Tests unitarios de la derivación (Vitest): color oscuro→foreground claro; color claro→foreground oscuro; **clamping de L por modo**; **secundario derivado del primario cuando es null**; semánticos intactos; neutros conservan S/L; flag de contraste insuficiente

## 5. Frontend — aplicación en runtime

- [ ] 5.1 **`ThemeManager`** (módulo sin React, design.md D5.1): `apply(theme)` escribe CSS vars en `:root`/`.dark` · `reset()` restaura los defaults · `setLogo(url?)`. Testeable sin React y reutilizable por la vista previa del editor
- [ ] 5.2 Hook `useBranding` **clavado al `organizationId` activo** (no al login, design.md D5): obtiene la marca (`GET /organizations/me/branding`), deriva y llama a `ThemeManager.apply`; re-aplica al **cambiar de organización activa**; `ThemeManager.reset()` en logout
- [ ] 5.3 **Cache-first (design.md D8)**: cache **indexada por organización** (`{activeOrgId, branding:{<orgId>:{...}}}`) en `localStorage`; pintar la última organización activa al arranque; tras `/auth/refresh`, si el `organizationId` real no coincide con `activeOrgId` → `reset()` antes de aplicar; **limpiar en logout**; revalidar comparando el payload
- [ ] 5.4 Integrar el logo con **`<img src>` únicamente** (nunca `dangerouslySetInnerHTML`) y **`onError` → logo del producto** (design.md D7). El favicon NO se toca: siempre el del producto
- [ ] 5.5 Verificar que las pantallas de **auth conservan la marca del producto** (no aplican branding)
- [ ] 5.6 Tests (Vitest + MSW): tema aplicado al montar con sesión; **re-aplicado al cambiar de organización**; reset en logout; **cache de otra organización se descarta**; **logo caído cae al del producto**; auth sin branding

## 6. Frontend — pantalla de ajustes (ADMIN)

- [ ] 6.1 Ruta/pantalla de **marca** protegida por rol `ADMIN` (RouteGuard): color picker(s), input de URL del logo, guardar
- [ ] 6.2 **Vista previa en vivo**: aplica `deriveTheme` sin persistir; aviso de contraste insuficiente
- [ ] 6.3 Hook TanStack Query (mutación) sobre el fetcher generado; al guardar, refetch/aplicar la marca; toasts; validación Zod en paridad (color hex + `logoUrl` solo https)
- [ ] 6.4 Tests (Vitest + MSW): guardar como ADMIN aplica el tema; validación en cliente; preview no persiste

## 7. Documentación (al archivar)

- [ ] 7.1 `docs/domain.md`: entidad `OrganizationBranding` (1:1 con `Organization`, sin RLS)
- [ ] 7.2 `docs/frontend.md`: mecanismo de tematización por organización (tokens CSS, `deriveTheme`, `useBranding`, contraste)
- [ ] 7.3 `docs/vision.md` + `readme`: marcar `organization-branding` como construida (estado)

## 8. Cierre

- [ ] 8.1 `openspec validate add-organization-branding --strict`
- [ ] 8.2 `mvn verify` (backend) y `pnpm validate` (frontend) en verde
- [ ] 8.3 Afinar visualmente los umbrales de lightness clamping (design.md D3) contra colores de marca reales
- [ ] 8.4 Commits por scope (backend / frontend / docs / openspec), Conventional Commits en español
