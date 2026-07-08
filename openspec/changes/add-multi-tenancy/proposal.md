## Why

OdontoRisas se concibió como el sistema de gestión de **una** clínica dental. El producto pasa a ser un **SaaS multi-tenant**: varias organizaciones independientes comparten la instalación, y sus datos —clínicos, y por tanto sensibles— nunca pueden cruzarse. Además, un mismo profesional (habitual en odontología) debe poder trabajar en varias organizaciones **con una sola identidad**, sin duplicar correos ni cuentas.

La capa que resuelve esto —identidad, organización, membresía, aislamiento— se diseña **genérica y reutilizable**: es el andamiaje de plataforma sobre el que se monta el negocio dental, y está pensada para servir de base a otros SaaS. Por eso el tenant se llama `Organization`, no `Clinic`: una clínica es *una* organización. Esta separación coincide con la distinción plataforma/negocio que ya recoge `docs/vision.md §5`.

**Por qué ahora y no después.** Hoy lo único construido es `authentication`: una tabla `users`, una tabla `refresh_tokens` y un JWT con tres claims. El dominio clínico (`Patient`, `Appointment`, `Treatment`, `Invoice`…) todavía es **conceptual** en `docs/domain.md`. Introducir el modelo multi-tenant ahora significa que el 90% del dominio nazca ya aislado. Hacerlo después de `patients` o `billing` obliga a migrar cada tabla, cada consulta, cada test y datos reales de producción.

Esto convierte la capacidad en un **bloqueo duro**: ninguna capacidad de negocio (`users`, `patients`, …) debe proponerse antes de archivar esta.

## What Changes

Se adopta el modelo estándar de SaaS multi-tenant, separando tres conceptos que hoy están fundidos en `User`:

- **Identidad (`User`)**: existe **una sola vez** en toda la plataforma. Global, sin organización y **sin rol**. Solo email (único global), contraseña, nombre y estado.
- **Organización (`Organization`)**: el *tenant*, el cliente del SaaS. Es lo que se aísla. Lleva `type` (`CLINIC` por defecto en este producto) y un `OrganizationSettings` 1:1 (zona horaria, locale, moneda) como punto de extensión.
- **Membresía (`Membership`)**: la **entidad puente** identidad↔organización, que carga el **rol** y la pertenencia. Un usuario puede pertenecer a varias organizaciones, con un rol en cada una. Es el corazón del modelo multi-tenant.

Cambios concretos:

- **Nueva capacidad técnica `multi-tenancy`**: entidades `Organization`, `OrganizationSettings`, `Membership`; aislamiento de datos entre organizaciones; y contexto de organización resuelto en cada petición autenticada.
- **Aislamiento por Row-Level Security en dos claves**: las tablas de **negocio** se aíslan por organización (`organization_id`, `@TenantId` + RLS); la tabla puente `memberships` se aísla por **identidad** (RLS sobre `user_id`). `users` y `refresh_tokens` se consultan por credencial (email / hash) y no llevan RLS. Ver `design.md`.
- **BREAKING — `User` pierde el rol**: el rol se traslada a `Membership`. Afecta a `authentication` (`AccessClaims`, claim del JWT, `User.role`, `RouteGuard`).
- **BREAKING — Registro de usuario**: pasa a crear, en una sola transacción, la identidad (`User`), la organización (`Organization` + `OrganizationSettings`) y la membresía de administrador (`Membership` con rol `ADMIN`). El request incorpora el nombre de la organización.
- **BREAKING — JWT**: el access token emite el claim de organización (`oid`); el `role` pasa a derivarse de la membresía. Los tokens previos se invalidan.
- **BREAKING — Contrato OpenAPI**: cambian `RegisterRequest` y `UserResponse`. `api-type-contracts` exige regenerar `contracts/openapi.json` y los artefactos de orval en este mismo change.
- **Regla de dominio nueva y permanente**: toda entidad de negocio pertenece a una organización. Se consolida en `docs/domain.md` y `docs/backend.md`.
- **Regla de acceso explícita**: el `organizationId` se lee **solo** del token firmado (vía `OrganizationContext`), nunca de la petición (`@RequestHeader`/`@PathVariable`/`@RequestBody`).
- **Test de aislamiento obligatorio**: se añade a `docs/testing.md` como gate por capacidad — *la organización A no puede ver datos de la organización B*.

**Decisiones de alcance ya cerradas** (detalle y alternativas en `design.md`):

- El `email` es **único global** (una identidad por persona). Login `POST /auth/login {email, password}` sin cambios.
- El `slug` de la organización es **inmutable** (aunque cambie el nombre visible).
- Dos ciclos de vida distintos: `User.status` (`ACTIVE | LOCKED | DISABLED`, plataforma) y `Membership.status` (`ACTIVE | INACTIVE | SUSPENDED`, por organización).
- **Multi-organización desde el modelo, selector diferido**: la estructura soporta N membresías, pero la única vía de alta en este change es el registro (crea una). El login emite el token directo; el selector y las invitaciones pertenecen a `users`.
- **Onboarding autoservicio**; sin rol de plataforma ni panel interno.

## Capabilities

### New Capabilities

- `multi-tenancy`: aislamiento de datos entre organizaciones, entidades `Organization`/`OrganizationSettings`/`Membership`, resolución del contexto de organización por petición, y alta de organización con su primer administrador. Capacidad **técnica/plataforma** (`docs/vision.md §5`).

### Modified Capabilities

- `authentication`: cambian cuatro requirements —
  - *Registro de usuario*: crea `User` + `Organization` (+`Settings`) + `Membership` (rol `ADMIN`); el request lleva el nombre de la organización.
  - *Inicio de sesión con credenciales*: se autentica por identidad global; el token incluye la organización activa y el rol de la membresía.
  - *Renovación de token*: el token renovado conserva la organización activa y refleja el rol vigente de la membresía.
  - *Protección de rutas por autenticación y rol*: el rol se interpreta **dentro de** la organización activa.

## Non-goals

Explícitamente **fuera** de este change:

- **Selector de organización activa e invitaciones**: el modelo lo soporta; la UI y los endpoints pertenecen a `users`. El contrato de login multi-organización se **documenta** (design.md), no se implementa.
- **Planes, suscripciones y facturación SaaS**. Solo mención futura en `docs/vision.md §7`. `billing` sigue siendo **cobros org→paciente**. `Organization` **no** lleva `plan`.
- **Rol de plataforma / superadministrador** y panel interno cross-tenant.
- **Subdominio por organización** (`acme.odontorisas.com`) y resolución por host. El `slug` se persiste desde ya para no bloquear esa migración.
- **Gestión de staff y flujos de bloqueo/suspensión**: los `status` existen en el modelo, pero sus flujos se ejercitan en `users`.
- **Catálogo abierto de tipos de organización**: `OrganizationType` es un enum acotado (`CLINIC`, y valores futuros); no hay administración de tipos.

## Impact

**Base de datos** (`modules/backend/src/main/resources/db/migration/`, hoy en `V5`)
- `V6__organizations.sql`: tablas `organizations` (`name`, `slug` único, `type`, `status`), `organization_settings` (1:1), `memberships` (`user_id`, `organization_id`, `role`, `status`, único `(user_id, organization_id)`); backfill de organización por defecto (`type = CLINIC`) + settings + una membresía por usuario existente arrastrando su rol; `ALTER TABLE users DROP COLUMN role`; `ALTER TABLE users ADD COLUMN status`.
- `V7__rls.sql`: `ENABLE`/`FORCE ROW LEVEL SECURITY` + política de **identidad** sobre `memberships` (`user_id = current_setting('app.user_id')`). Las políticas por organización de las tablas de negocio llegan con cada capacidad de negocio (no existen aún).
- **Rol de base de datos**: `DB_USERNAME` sin `SUPERUSER` ni `BYPASSRLS` (condición para que RLS proteja). Documentado en `secrets.properties.example`.

**Backend** (`modules/backend`)
- Nuevo: entidades `Organization`, `OrganizationSettings`, `Membership` (+repositorios y enums de estado/tipo), `common/OrganizationContext.java` + `common/IdentityContext.java`, `CurrentTenantIdentifierResolver`, y la propagación de ambos contextos al pool de conexiones.
- Modificado: `User` (pierde `role`, gana `status`), `AccessClaims`, `TokenService`, `JwtAuthenticationFilter`, `SecurityConfig`, `AuthService.register`/`login`/`refresh`, `RegisterCommand`, `RegisterRequest`, `UserResponse`.

**Contrato y frontend** (`contracts/openapi.json`, `modules/frontend`)
- Regenerar contrato + cliente orval (`pnpm generate:api`).
- `RegisterForm`: campo *"Nombre de la organización"* (etiqueta de producto: "clínica"). Sesión: organización activa (nombre, `slug`) y rol.

**Documentación** (parte del change; gobernanza en `docs/vision.md §4`)
- `docs/vision.md`, `docs/domain.md`, `docs/architecture.md` (sección multi-tenancy), `docs/backend.md` (reglas de persistencia y acceso), `docs/testing.md` (test de aislamiento).

**Riesgo principal**: una fuga de datos entre organizaciones es el fallo que hunde un SaaS sanitario. De ahí la doble barrera (aplicación + motor) sobre las tablas de negocio, la RLS de identidad sobre la tabla puente, y el test de aislamiento como gate.
