## Why

OdontoRisas se concibió como el sistema de gestión de **una** clínica dental. El producto pasa a ser un **SaaS multi-clínica**: varias clínicas independientes comparten la instalación, y sus datos —clínicos, y por tanto sensibles— nunca pueden cruzarse. Además, un mismo profesional (habitual en odontología) debe poder trabajar en varias clínicas **con una sola identidad**, sin duplicar correos ni cuentas.

**Por qué ahora y no después.** Hoy lo único construido es `authentication`: una tabla `users`, una tabla `refresh_tokens` y un JWT con tres claims. El dominio clínico (`Patient`, `Appointment`, `Treatment`, `Invoice`…) todavía es **conceptual** en `docs/domain.md`. Introducir el modelo multi-tenant ahora significa que el 90% del dominio nazca ya aislado. Hacerlo después de `patients` o `billing` obliga a migrar cada tabla, cada consulta, cada test y datos reales de producción.

Esto convierte la capacidad en un **bloqueo duro**: ninguna capacidad de negocio (`users`, `patients`, …) debe proponerse antes de archivar esta.

## What Changes

Se adopta el modelo estándar de SaaS multi-tenant, separando tres conceptos que hoy están fundidos en `User`:

- **Identidad (`User`)**: existe **una sola vez** en toda la plataforma. Global, sin clínica y **sin rol**. Solo email (único global), contraseña, nombre y estado.
- **Clínica (`Clinic`)**: el *tenant*, el cliente del SaaS. Es lo que se aísla.
- **Membresía (`Membership`)**: la relación usuario↔clínica, y **dónde vive el rol**. Un usuario puede pertenecer a varias clínicas, con un rol en cada una.

Cambios concretos:

- **Nueva capacidad técnica `multi-tenancy`**: entidades `Clinic` y `Membership`, aislamiento de datos de negocio entre clínicas, y contexto de clínica resuelto en cada petición autenticada.
- **Aislamiento en dos capas** sobre las tablas **de negocio**: discriminador `clinic_id` filtrado automáticamente por Hibernate (`@TenantId`), **más** Row-Level Security de PostgreSQL como red de seguridad a nivel de motor. `users`, `memberships` y `refresh_tokens` son tablas del **dominio de autenticación**, no de negocio: no participan del aislamiento por clínica (ver `design.md`).
- **BREAKING — `User` pierde el rol**: el rol se traslada a `Membership`. Afecta a la capacidad `authentication` ya construida (`AccessClaims`, claim del JWT, `User.role`, `RouteGuard`).
- **BREAKING — Registro de usuario**: pasa a crear, en una sola transacción, la identidad (`User`), la clínica (`Clinic`) y la membresía de administrador (`Membership` con rol `ADMIN`). El request incorpora `clinicName`.
- **BREAKING — JWT**: el access token emite el claim de clínica (`cid`); el `role` pasa a derivarse de la membresía activa. Los tokens emitidos antes de este change dejan de ser válidos.
- **BREAKING — Contrato OpenAPI**: cambian `RegisterRequest` y `UserResponse`. `api-type-contracts` exige regenerar `contracts/openapi.json` y los artefactos de orval en este mismo change (requirement "sin drift").
- **Regla de dominio nueva y permanente**: toda entidad de negocio pertenece a una clínica. Se consolida en `docs/domain.md` y `docs/backend.md`.
- **Test de aislamiento obligatorio**: se añade a la estrategia de `docs/testing.md` como gate por capacidad — *la clínica A no puede ver datos de la clínica B*.

**Decisiones de alcance ya cerradas** (detalle y alternativas en `design.md`):

- El `email` es **único global** (sostiene una sola identidad por persona). El login sigue siendo `POST /auth/login {email, password}`.
- **Multi-clínica desde el modelo, selector diferido**: la estructura soporta que un usuario pertenezca a varias clínicas, pero en este change la **única** vía de alta de membresías es el registro, que crea exactamente una. Por tanto todo usuario tiene una sola membresía y el login emite el token directamente. El selector de clínica y las invitaciones a una segunda clínica pertenecen a la capacidad `users`.
- **Onboarding autoservicio**: quien se registra crea la clínica y queda como su `ADMIN`. No hay rol de plataforma ni panel interno.
- La entidad tenant se llama **`Clinic`** (lenguaje ubicuo del dominio).

## Capabilities

### New Capabilities

- `multi-tenancy`: aislamiento de datos de negocio entre clínicas, entidades `Clinic` y `Membership`, resolución del contexto de clínica por petición, y alta de clínica con su primer administrador. Capacidad **técnica/plataforma** (ver criterio en `docs/vision.md §5`).

### Modified Capabilities

- `authentication`: cambian cuatro requirements —
  - *Registro de usuario*: crea `User` + `Clinic` + `Membership` (rol `ADMIN`); el request lleva `clinicName`.
  - *Inicio de sesión con credenciales*: se autentica por identidad global; el access token emitido incluye la clínica y el rol de la membresía.
  - *Renovación de token*: el token renovado conserva la clínica activa y refleja el rol vigente de la membresía.
  - *Protección de rutas por autenticación y rol*: el rol se interpreta **dentro de** la clínica activa del usuario.

## Non-goals

Explícitamente **fuera** de este change:

- **Selector de clínica activa e invitaciones** a una segunda clínica: el modelo lo soporta, pero la UI y los endpoints pertenecen a la capacidad `users`.
- **Planes, suscripciones y facturación SaaS** (la clínica pagando a la plataforma). Solo se menciona como candidata futura en `docs/vision.md §7`. `billing` en el roadmap significa **cobros clínica→paciente** y no cambia de significado. `Clinic` **no** lleva campo `plan` en este change.
- **Rol de plataforma / superadministrador** y panel interno cross-tenant.
- **Subdominio por clínica** (`acme.odontorisas.com`) y resolución de tenant por host. El `slug` de `Clinic` se persiste desde ya para no bloquear esa migración futura.
- **Gestión de staff**: pertenece a la capacidad `users`.
- Personalización por clínica (logo, temas), límites de uso o cuotas.

## Impact

**Base de datos** (`modules/backend/src/main/resources/db/migration/`, hoy en `V5`)
- `V6__clinics_and_memberships.sql`: tablas `clinics` y `memberships` (`user_id`, `clinic_id`, `role`, `status`, único `(user_id, clinic_id)`); backfill de una clínica por defecto y una membresía por cada usuario existente, arrastrando su rol; **`ALTER TABLE users DROP COLUMN role`**.
- **RLS**: como todavía no existe ninguna tabla de negocio, este change **no** aplica políticas RLS a datos reales. Entrega el **mecanismo** (propagación del contexto de clínica al motor) y la **convención** que cada capacidad de negocio deberá seguir, probados con un test de integración. Ver `design.md`.
- **Rol de base de datos**: `DB_USERNAME` no puede tener `BYPASSRLS` ni ser `SUPERUSER` (condición para que RLS proteja cuando lleguen las tablas de negocio). Se documenta en `secrets.properties.example`.

**Backend** (`modules/backend`)
- Nuevo: `persistence/entity/Clinic.java`, `persistence/entity/Membership.java`, sus repositorios, `common/ClinicContext.java`, `CurrentTenantIdentifierResolver`, y la propagación del contexto al pool de conexiones.
- Modificado: `User` (pierde `role`, opcional `status`), `AccessClaims`, `TokenService`, `JwtAuthenticationFilter`, `SecurityConfig`, `AuthService.register`/`login`/`refresh`, `RegisterCommand`, `RegisterRequest`, `UserResponse`, `RefreshToken` (+`clinic_id`).

**Contrato y frontend** (`contracts/openapi.json`, `modules/frontend`)
- Regenerar contrato + cliente orval (`pnpm generate:api`).
- `RegisterForm`: campo *"Nombre de la clínica"*. Sesión: la clínica activa (nombre y `slug`) y el rol disponibles para la UI.

**Documentación** (se actualiza como parte del change; gobernanza en `docs/vision.md §4`)
- `docs/vision.md`, `docs/domain.md` (entidades `Clinic`/`Membership`, `User` como identidad, `clinicId` en las entidades de negocio, ER), `docs/architecture.md` (sección multi-tenancy), `docs/backend.md` (regla de persistencia), `docs/testing.md` (test de aislamiento).

**Riesgo principal**: una fuga de datos entre clínicas es el fallo que hunde un SaaS sanitario. De ahí la doble capa (aplicación + motor) sobre las tablas de negocio y el test de aislamiento como gate.
