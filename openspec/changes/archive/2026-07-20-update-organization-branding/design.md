## Context

`organization-branding` (construida, `openspec/specs/organization-branding/spec.md`) hoy persiste
`brandPrimary`/`brandSecondary` como hex libre (`OrganizationBranding` entity, `varchar(9)`,
`@Pattern(regexp = "^#[0-9a-fA-F]{6}$")` en `UpdateBrandingRequest`). El frontend
(`BrandingSettingsScreen.tsx`) expone un `<input type="color">` + texto libre. `deriveTheme.ts` toma
esos dos hex, deriva `primary`/`secondary`/`accent`/`ring` + neutros por modo, y calcula
`-foreground`/`contrastWarning` (WCAG AA). Este change reemplaza el **origen** del color (hex libre →
preset elegido) sin tocar `deriveTheme`.

## Goals / Non-Goals

**Goals:**
- El `ADMIN` elige un preset de un catálogo fijo en vez de teclear un hex.
- `deriveTheme` recibe los mismos dos hex de entrada que siempre recibió — su lógica no cambia una línea.
- El backend valida la **referencia** (¿existe el preset?), no el **contenido visual** (¿se ve bien?)
  — la calidad visual la garantiza el catálogo por construcción, no una validación en runtime.

**Non-Goals:**
- Componer el catálogo real (cuántos presets, qué colores) — decisión de apply-time (frontend-design/ui-ux-pro-max), no de este design.
- Migrar datos: qué pasa con un `brandPrimary` hex ya guardado hoy — ver Risks.
- Tocar el logo, el favicon, o cualquier requirement no listado en el proposal.

## Decisions

### D1 — El preset es un enum Java (closed set), no una tabla
Sigue `docs/backend.md` (conjunto cerrado → enum, app + `CHECK` en BD + contrato), mismo patrón que
`DocumentType`/`PatientStatus`. Alternativa descartada: una tabla `brand_presets` seedeada por Flyway
— más flexible (agregar un preset sin redeploy) pero es sobre-ingeniería para un catálogo que el
proposal ya declara "fijo y curado"; si en el futuro el catálogo necesita administrarse dinámicamente,
se migra entonces (YAGNI).

### D2 — El backend valida la CLAVE del preset; el catálogo visual (hex + nombre) vive en el frontend
`OrganizationBranding.brandPreset` persiste solo la **clave** (`enum BrandPreset { ... }`,
`varchar` + `CHECK` en Postgres). El contrato expone `brandPreset?: BrandPresetKey` (unión de
literales, generada por orval — mismo patrón que `PatientRequestDocumentType`). El **valor visual**
(qué hex de `primary`/`secondary` le corresponde a cada clave, su nombre para mostrar, su swatch) vive
en un catálogo estático del **frontend** (`modules/branding/presets.ts`), no en el backend.

Por qué: el backend gana con esto (a) no reinterpretar hex en Java para nada — persistencia e
integridad referencial, cero lógica de color; (b) iterar el catálogo visual (afinar un hex, agregar
uno nuevo) es un cambio de **frontend únicamente**, sin migración Flyway. El backend SHALL seguir
siendo la fuente de verdad de **qué claves existen** (el enum + `CHECK`) para que un valor fuera del
catálogo se rechace ahí — el frontend no puede inventar una clave que el backend no reconozca.

Alternativa descartada: que el backend resuelva `brandPreset` → hex y devuelva `brandPrimary`/
`brandSecondary` ya resueltos en `BrandingResponse` (mantendría el contrato de salida sin tocar
`deriveTheme` en absoluto). Se descarta porque duplicaría el catálogo visual en dos lugares (Java +
frontend) o forzaría al backend a ser la fuente de los hex — contradice D2 arriba y complica iterar
el catálogo sin tocar Java/migraciones.

### D3 — Migración: reemplaza columnas, no las conserva en paralelo
`V11__organization_branding_presets.sql`: `ALTER TABLE organization_branding DROP COLUMN
brand_primary, DROP COLUMN brand_secondary, ADD COLUMN brand_preset VARCHAR(32) CHECK (brand_preset
IN (...))`. Se descarta mantener ambas formas en paralelo (columna vieja + nueva) — el proposal ya
declara el cambio **BREAKING**, y sostener las dos formas duplicaría validación sin necesidad real
(greenfield, sin tráfico de producción real que dependa del shape viejo).

### D4 — `contrastWarning` se mantiene como defensa en profundidad, no se elimina
`deriveTheme` sigue calculando `foregroundFor`/`contrastWarning` aunque el catálogo ya garantice AA
por construcción — si alguna vez un preset se agrega mal vetado, la defensa en runtime sigue
protegiendo de texto ilegible (spec: "el sistema NUNCA SHALL presentar texto ilegible por diseño").
No se propone quitar código de `deriveTheme.ts`.

## Risks / Trade-offs

- **[Riesgo] Organizaciones con `brandPrimary`/`brandSecondary` hex ya guardado pierden esa
  personalización exacta al desplegar (columnas se eliminan, sin mapeo hex→preset más cercano)** →
  **Mitigación**: aceptable en esta etapa (proyecto pre-lanzamiento, sin clientes reales con
  personalización en producción todavía — ver `docs/vision.md`). Si esto cambia antes de aplicar el
  change, revisar esta decisión con el user antes de escribir la migración.
- **[Riesgo] El catálogo "fijo" limita más la personalización que un picker libre** → **Mitigación**:
  es la decisión de producto explícita que motiva este change (personalización controlada, no un
  editor de diseño) — no es un defecto a mitigar, es el objetivo.
- **[Trade-off] Mover el catálogo visual al frontend significa que el backend no puede validar "este
  preset se ve bien"** → aceptado: el backend valida integridad referencial (¿existe la clave?), la
  calidad visual la garantiza el proceso de curación humana/diseño al definir el catálogo, no una
  regla en runtime.

## Migration Plan

1. Backend: `enum BrandPreset` (paquete `common`, mismo lugar que otros enums de dominio) +
   `V11__organization_branding_presets.sql` (drop columnas hex, add `brand_preset` + `CHECK`).
2. Backend: `OrganizationBranding` entity, DTOs, `BrandingService`/`BrandingController` actualizados
   al nuevo campo.
3. Contrato: regenerar `contracts/openapi.json` (springdoc) → `pnpm generate:api` (orval) — verificar
   `ContractDriftIT`/`codegen-drift` en verde.
4. Frontend: `modules/branding/presets.ts` (catálogo clave→hex+nombre, contenido decidido en
   apply-time), `BrandingSettingsScreen.tsx` (selector de preset en vez de color picker),
   `branding.ts` schema Zod (clave de preset en vez de regex hex) — `deriveTheme.ts` sin cambios.
5. Sin rollback especial: es una migración de columna en un proyecto sin datos de producción reales
   que preservar (ver Risks).

## Open Questions

- ¿Cuántos presets en el catálogo inicial y cuáles colores? — se resuelve en `tasks.md`/apply-time
  con `frontend-design`/`ui-ux-pro-max`, no bloquea este design.
