## Why

Dentdesk dice ser un monorepo modular, pero **hoy ninguna regla vigila los límites entre módulos**. Una auditoría de los imports reales (2026-11-24) lo confirmó:

- **Frontend:** la spec `project-foundation` ya promete *"`modules/core` no depende de otros módulos"*, y es cierto, pero **por costumbre, no por regla**: ESLint solo tiene `react/no-multi-comp`. Entre módulos de negocio hay imports directos por ruta profunda (`clinical-history → staff / dental-chart / patients / appointments`, `appointments → staff / patients`, `dental-chart → staff`, `auth → staff`, `home → patients`) que nadie declaró y que un import nuevo puede ampliar sin que nada lo note. No hay barrels ni API pública por módulo.
- **Backend:** ArchUnit vigila **capas** (`presentation`/`service`/`persistence`) y tenancy, pero **no las dependencias entre paquetes `service.*`**. Hay un **ciclo real, `auth ↔ staff`**: `AuthService → StaffService` y `StaffService → EmailAlreadyExistsException / InvalidInvitationException` (viven en `service.auth`). Nada lo detecta.

Es el mismo patrón que `docs/testing.md §5` ya documenta: una regla escrita en prosa se incumple. Se cierra ahora porque `appointments` acaba de sumar un módulo y cuatro aristas (en ambos lados) y es barato hacerlo antes de que el grafo siga creciendo.

## What Changes

- **Frontend:** un **mapa único de dependencias permitidas entre módulos** en `eslint.config.js` que genera reglas `no-restricted-imports` por módulo. `core` no importa de ningún módulo de negocio; cada módulo de negocio solo importa de `core` y de los módulos que **declara** (el grafo actual, tal cual). Un import **estático con alias `@/modules/...`**, dentro de `src/modules/**` y fuera del mapa, **rompe `pnpm lint`**. No cubre imports relativos ni dinámicos, ni `screens/`, `routes/`, `layouts/` ni `store/`.
- **Backend:** una regla ArchUnit **`slices()` sin ciclos** sobre `service.<dominio>`, más un **mapa de dependencias permitidas** entre esos paquetes (el grafo actual).
- **Backend, saneamiento mínimo:** mover `EmailAlreadyExistsException` e `InvalidInvitationException` de `service.auth` a `service.staff` para **romper el ciclo `auth ↔ staff`**. Queda la dirección `auth → staff`, que ya existía. Sin cambio de contrato HTTP.
- **Cada regla se prueba contra una violación real** (`docs/testing.md §5`): el test inyecta un import/dependencia prohibida y comprueba que la regla falla. Las reglas ArchUnit nuevas incluyen el chequeo de que el importador no está vacío.
- **ADR-0021** con la decisión, el estado real encontrado y las alternativas descartadas.
- **Docs:** `docs/frontend.md`, `docs/backend.md`, `docs/testing.md` y `docs/vision.md` (estado de capacidades) reflejan las reglas y **dicen con honestidad qué se vigila y qué no**.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `project-foundation`: ADDED requirements de límites entre módulos (frontend y backend) y de reglas de arquitectura comprobadas contra violación. No se modifica ningún requirement existente.

## Impact

- **Frontend:** `modules/frontend/eslint.config.js` (+ un test de Vitest que ejecuta ESLint sobre un archivo falso). Ningún archivo de `src/` cambia: el mapa se escribe para que el código actual **pase** (tests exentos, ver design).
- **Backend:** `service.auth` / `service.staff` (2 clases movidas + imports de `AuthService`, `StaffService` y `AuthServiceTest`; el manejador de excepciones **no** las referencia); nueva clase de test en `architecture/`.
- **Contrato OpenAPI:** sin cambios. **Base de datos:** sin cambios. **Normativa:** sin impacto (no se toca `regulatory-matrix.md`).
- **Incluye `appointments`** (archivado y en producción 2026-11-24): el grafo de `design.md` se recalculó sobre `dev` con la agenda integrada (`895f11c`), así que el mapa nace con `appointments` (front) y `service.appointment` (back) declarados.

## Non-goals

- **No** introducir barrels (`index.ts`) ni una API pública por módulo en el frontend. Se importa por ruta profunda hoy y se sigue pudiendo; ver `design.md` (alternativa descartada).
- **No** eliminar las dependencias actuales entre módulos (`clinical-history → staff`, etc.): son legítimas (la credencial clínica atraviesa la historia). Se **declaran**, no se rediseñan.
- **No** vigilar `presentation` ni `infra` entre dominios (controllers que usan servicios de varios dominios, composition root).
- **No** vigilar `screens/`, `routes/`, `layouts/` ni `store/` (orquestan módulos por diseño). Se registra que `core` importa de `@/routes/paths`, `@/store` y `@/layouts/RootLayout`, y **no** se cambia.
- **No** agregar `eslint-plugin-boundaries` ni `dependency-cruiser` (ver alternativas en `design.md`).
- **No** tocar `docs/compliance/regulatory-matrix.md` ni el contrato de la API.
