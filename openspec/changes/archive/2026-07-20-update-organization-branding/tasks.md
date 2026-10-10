## 1. Backend — modelo y migración

- [x] 1.1 Crear `enum BrandPreset` (paquete `common`, mismo lugar que otros enums de dominio cerrados) con un catálogo inicial mínimo de claves (composición visual real definida en 4.1, no aquí — D1/D5 de `design.md`).
- [x] 1.2 `V11__organization_branding_presets.sql`: `DROP COLUMN brand_primary`, `DROP COLUMN brand_secondary`, `ADD COLUMN brand_preset VARCHAR(32)` + `CHECK (brand_preset IN (...))` sobre `organization_branding` (D3).

## 2. Backend — capa de aplicación

- [x] 2.1 Actualizar `OrganizationBranding` entity: reemplazar `brandPrimary`/`brandSecondary` por `brandPreset` (tipo `BrandPreset`, nullable).
- [x] 2.2 Actualizar `Branding` (record de servicio), `BrandingService` (persistir/limpiar por preset; conservar la lógica de "todo vacío → `deleteById`").
- [x] 2.3 Actualizar `UpdateBrandingRequest`/`BrandingResponse` (DTOs): `brandPreset` reemplaza los dos campos hex; validación de clave inexistente en el catálogo → error 400 (scenario "Valores de entrada inválidos").
- [x] 2.4 Verificar que `BrandingController` (autorización ADMIN, organización de sesión) no necesita cambios más allá del tipo del campo.

## 3. Contrato

- [x] 3.1 Regenerar `contracts/openapi.json` (springdoc) reflejando el nuevo shape de `UpdateBrandingRequest`/`BrandingResponse`.
- [x] 3.2 `pnpm generate:api` (orval) — confirmar que el cliente generado expone `BrandPresetKey` como unión de literales (mismo patrón que `PatientRequestDocumentType`).
- [x] 3.3 Verificar `ContractDriftIT` (backend) y `codegen-drift` (CI frontend) en verde.

## 4. Frontend — catálogo y formulario

- [x] 4.1 Crear `modules/branding/presets.ts`: catálogo clave→`{brandPrimary, brandSecondary, label}` (colores curados, con `frontend-design`/`ui-ux-pro-max` para la composición visual — Non-goal del proposal, decisión aquí).
- [x] 4.2 Actualizar `modules/branding/schemas/branding.ts`: reemplazar el regex hex por validación de `BrandPresetKey` (enum del contrato).
- [x] 4.3 Actualizar `BrandingSettingsScreen.tsx`: reemplazar el `<input type="color">` + texto libre por un selector del catálogo (swatch + nombre por preset); mantener el preview en vivo y el aviso de `contrastWarning` ya existentes, alimentados con el hex resuelto del preset elegido vía `deriveTheme` (sin tocar `deriveTheme.ts`).
- [x] 4.4 Confirmar que `useBrandingApi.ts`/`useUpdateBranding` siguen funcionando con el nuevo shape del contrato (solo cambia qué campo se envía).

## 5. Tests — derivados de los Scenario (docs/testing.md §0)

- [x] 5.1 Backend: actualizar/crear tests de `BrandingService`/`BrandingControllerIT` para los 6 scenarios de "Edición de la marca restringida al administrador" (actualiza marca con preset válido; no-admin rechazado; clave de preset inexistente → 400; URL de logo insegura → 400; restaurar color a default; quitar toda la personalización).
- [x] 5.2 Backend: test de que el `CHECK` de Postgres rechaza una clave fuera del enum a nivel de BD (defensa en profundidad, no solo Bean Validation).
- [x] 5.3 Frontend: test unitario que recorre **todo** el catálogo de `presets.ts` y afirma `contrastWarning === false` para cada uno vía `deriveTheme` — cierra el scenario "Ningún preset del catálogo tiene contraste insuficiente": si alguien agrega un preset mal vetado, este test lo atrapa antes que un `ADMIN` real.
- [x] 5.4 Frontend: actualizar tests de `BrandingSettingsScreen`/`useBrandingApi` al nuevo flujo de selección por preset (en vez de escribir un hex).
- [x] 5.5 `mvn verify` (backend) y `pnpm validate` (frontend) en verde.

## 6. Cierre

- [x] 6.1 Commit(s) por scope (backend / contrato / frontend) según `docs/commits.md`.
- [x] 6.2 `openspec validate update-organization-branding --strict` en verde antes de abrir PR.
- [x] 6.3 Al archivar: sincronizar `openspec/specs/organization-branding/spec.md` y marcar la capacidad en `docs/vision.md`.
