## Why

La regla de `multi-tenancy` es *"toda entidad de **negocio** lleva `organization_id` + RLS"*, y se sostiene sobre una lista de excepciones que **vive repartida en prosa** (`ADR-0003`, `docs/architecture.md`, comentarios de migraciones y de clases Java). Una auditoría de las migraciones de `dev` (V1–V31, 2026-11-24) mostró que esa lista tiene huecos:

- **`invitations` tiene `organization_id` y no tiene RLS, y ningún ADR la cubre.** Guarda hashes de token y de código de verificación y correos del personal. Hoy se aísla solo por código: el listado filtra por `OrganizationContext` en la consulta, `resend`/`revoke` cargan por id y comparan la organización *después* de leer (y bloquear) la fila, y la entidad no lleva `@TenantId`. El código es correcto hoy; **no hay red debajo y nadie lo decidió por escrito.**
- **`ADR-0003` (settings/branding sin RLS) se escribió antes de `platform-admin`.** Sus condiciones de invalidación mencionan un panel de superadministrador que opere sobre otra organización, y `PlatformOrganizationService.provision` ya guarda `OrganizationSettings` de organizaciones nuevas en producción. Nadie lo revalidó.
- **Nada impide que la próxima tabla con `organization_id` entre sin RLS.** Un test de aislamiento por capacidad es obligatorio por regla (`docs/testing.md §5`), pero no hay ningún control que detecte una tabla **sin clasificar**. `appointments` (en apply) es la próxima.

Es el mismo patrón de "regla en prosa que se incumple" que ya se cerró para capas y `@Async`. Se cierra con un inventario que rompe la build, no con otro párrafo.

## What Changes

- **Test de inventario** (`TenantTableInventoryIT`, contra PostgreSQL real, conectado como rol de aplicación no superusuario) que lee el catálogo (`pg_class`, `pg_attribute`, `pg_policies`) y exige que **toda tabla de `public` esté clasificada** en exactamente una de cuatro listas declaradas con motivo:
  1. aisladas **por organización** (con `organization_id`): RLS habilitada, **forzada** y con política;
  2. aisladas **por identidad** (`memberships`, `professional_credential`): RLS habilitada, forzada y con política;
  3. **excepciones por clínica** (con `organization_id` y **sin** RLS): `invitations`, `organization_settings`, `organization_branding`, cada una con motivo y ADR;
  4. **sin `organization_id`** (tenant raíz, identidad global, catálogos): `organizations`, `users`, `refresh_tokens`, `password_reset_tokens`, `platform_admins`, `cie10_code`, `dental_finding_catalog`, `app_metadata`.
  El test falla ante tabla sin clasificar, entrada obsoleta, RLS ausente o no forzada, o tabla con `organization_id` mal clasificada. La lógica de clasificación es una función pura probada con instantáneas sintéticas (sin Docker), de modo que **se ve fallar**.
- **ADR-0022**: clasifica cada tabla, **documenta `invitations` como excepción conocida** (motivo, riesgo y condiciones que la invalidan) y **revalida `ADR-0003` contra `platform-admin`**. Conclusión adelantada tras leer el código: **válida con matiz** (el `organizationId` de `provision` lo genera el propio servicio al crear la organización, no viene de la petición; lo que cambia es que la invariante "siempre del token" deja de ser literal). El ADR deja constancia de qué se verificó y qué no.
- **`ADR-0003`** solo anota su estado (*"Válida con matiz — ver ADR-0022"*), como pide `docs/adr/README.md`.
- **Docs:** `docs/backend.md §8.1`, `docs/architecture.md` (aislamiento), `docs/testing.md` y `docs/vision.md` apuntan al inventario y a la lista única de clasificación.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `multi-tenancy`: ADDED requirements de inventario de tablas y cobertura de RLS. No se modifica ningún requirement existente ni su `Purpose`.

## Impact

- **Backend (solo tests):** una clase de test nueva (+ su función pura de clasificación y sus pruebas). **Ningún código de producción cambia.**
- **Base de datos:** **sin migraciones.** **Contrato OpenAPI:** sin cambios. **Frontend:** sin cambios.
- **Docs:** `docs/adr/0022-…` (nuevo), `docs/adr/0003-…` (solo su línea de estado), `docs/backend.md`, `docs/architecture.md`, `docs/testing.md`, `docs/vision.md`.
- **Normativa:** sin fila nueva en `regulatory-matrix.md`. `invitations` guarda correos del personal (dato personal) y `ADR-0019` ya fija que Dentdesk es el *encargado*; este change **no cita artículos** ni afirma cumplimiento: documenta el riesgo técnico.
- **Convive con `add-appointments`** (en apply): `appointments` aparecerá como tabla **sin clasificar** y el test fallará hasta que se declare. Quien llegue segundo la declara en la lista 1 (con RLS) — task 4.
- **Convive con `update-module-boundaries`** (propuesto, en pausa): ambos editan `docs/vision.md` en zonas distintas; conflicto textual trivial.

## Non-goals

- **No** agregar RLS a ninguna tabla, **ni a `invitations`**. Eso es la **opción B**: exige diseñar el acceso por token sin contexto de organización (el alta por invitación ocurre antes de que exista sesión) y una migración sobre una tabla en producción. Queda pendiente de un `/opsx:explore` y se reevalúa con este inventario ya en pie.
- **No** crear migraciones ni tocar el esquema.
- **No** cambiar código de producción (`StaffService`, repositorios, entidades), aunque se anoten observaciones (p. ej. `findByIdForUpdate` bloquea una fila de otra clínica antes de rechazarla).
- **No** tocar `add-appointments` ni su tabla `appointments`.
- **No** extender la suite de integración completa al rol no superusuario (deuda ya anotada en `AbstractTenantAwareIntegrationTest`).
- **No** editar `ADR-0003` más allá de su línea de estado.
