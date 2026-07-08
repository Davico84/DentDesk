## Context

OdontoRisas pasa de gestionar **una** clínica a ser un **SaaS multi-tenant**, y su capa de plataforma (identidad, organización, membresía, aislamiento) se diseña **genérica y reutilizable** (por eso el tenant es `Organization`, no `Clinic`: una clínica es *una* organización). La motivación y el alcance están en `proposal.md`; aquí se decide el **cómo**.

**Estado actual** (lo único construido, capacidad `authentication`):

- `users` (`id`, `email` único global, `password_hash`, `role`, `full_name`, timestamps) y `refresh_tokens` (`user_id`, `token_hash`…). Migraciones hasta `V5`.
- Access token JWT stateless con claims `userId`, `email`, `role` (`AccessClaims`); refresh token persistido y rotado.
- **Cero entidades de negocio.** `Patient`, `Appointment`, `Treatment`, `Invoice`… son todavía conceptuales en `docs/domain.md`.

**Restricciones del stack** (cerrado en `docs/architecture.md`):

- Spring Boot **4.0.6** → Hibernate ORM **7.2.12.Final** (verificado con `mvn dependency:tree`).
- PostgreSQL + Flyway (`ddl-auto: none`). **Un solo datasource** (`application.yml`), sin perfiles; credenciales externalizadas.
- `spring.jpa.open-in-view: false` — ya vigente, y prerequisito para que el contexto no sobreviva a la transacción.
- Los test slices de backend usan **H2** (`docs/testing.md`). H2 **no** implementa Row-Level Security → probar RLS exige PostgreSQL real (Testcontainers).

**Restricción del dominio:** los datos son **clínicos**. Una fuga entre organizaciones no es un bug, es un incidente de datos de salud. El diseño asume que el código de aplicación **se equivocará** en algún momento y busca una segunda barrera en el motor.

**Historia de este diseño (dos revisiones).**
1. La primera versión metía `tenant_id` en `users` con `@TenantId`; chocaba con el login (hay que hallar al usuario *antes* de conocer su organización) y forzaba una RLS permisiva (*fail-open*) sobre `users`.
2. La segunda adoptó Identity/Organization/Membership pero dejaba `memberships` **sin** RLS (filtrada por código). La revisión humana señaló la asimetría: ¿por qué el motor protege el negocio pero no la tabla puente? Esta versión la cierra con **RLS de identidad** sobre `memberships`.

**Dato relevante de la versión:** en Hibernate 7.2, `@TenantId` filtra en la capa de aplicación. La generación **automática** de RLS a partir de `@TenantId` llega en **Hibernate 8.0**. Aquí las políticas se escriben a mano en Flyway.

## Goals / Non-Goals

**Goals**

1. Ninguna consulta puede devolver filas de otra organización, **aunque el desarrollador olvide el filtro** — garantizado por el motor, no por el código.
2. Una persona = una identidad = un email, aunque trabaje en varias organizaciones.
3. El dominio clínico, que aún no existe, nace multi-tenant sin retrabajo: una capacidad nueva anota la entidad y hereda el aislamiento.
4. La capa de plataforma es reutilizable (nombres genéricos, sin acoplar a lo dental).
5. El login y el registro no se complican con subdominios ni pasos extra en el caso MVP.

**Non-Goals** — ver `proposal.md → Non-goals`.

---

## Decisions

### D1. Modelo Identity / Organization / Membership (+ Settings, + Type)

**Decisión.** Cuatro entidades de plataforma:

| Entidad | Es | Campos clave | Aislamiento |
|---|---|---|---|
| `User` | **Identidad** global de una persona | `email` (único global), `password_hash`, `full_name`, `status` | Ninguno (se lee por credencial) |
| `Organization` | El **tenant** | `name`, `slug` (único, inmutable), `type`, `status` | Es el sujeto del aislamiento de negocio |
| `OrganizationSettings` | Configuración 1:1 de la organización | `organization_id` (PK/FK), `timezone`, `locale`, `currency` | Hereda de su organización |
| `Membership` | **Puente** identidad↔organización, con el **rol** | `user_id`, `organization_id`, `role`, `status`, único `(user_id, organization_id)` | RLS de **identidad** (D2) |

- **`OrganizationType`**: enum acotado (`CLINIC`, y valores futuros), almacenado como `varchar` para que añadir un tipo no requiera migración. En este producto, el registro crea siempre `CLINIC`.
- **`OrganizationSettings`** se crea junto con la organización (valores por defecto) como punto de extensión: todo SaaS acaba necesitando zona horaria/locale/moneda por tenant, y tenerlo desde el inicio evita una migración posterior.

**Por qué.** El rol nunca fue atributo de la persona, sino de su relación con una organización. Separar identidad, tenant y pertenencia es el modelo estándar de SaaS multi-tenant y hace natural el email único global. `Membership` no es "tabla de auth": es la entidad que carga autorización, pertenencia, rol y contexto — el corazón del SaaS.

**Alternativa descartada — `User` con `tenant_id`.** Fundía identidad y pertenencia, impedía multi-organización con un solo correo y forzaba el *fail-open* (ver Historia).

### D2. RLS en todas partes, con **dos claves**: identidad y organización

**Decisión.** No hay tablas "protegidas por código". Cada tabla se aísla en el motor por la clave que le corresponde:

| Tabla | Se lee por | RLS | Clave de la política |
|---|---|---|---|
| `users` | `email` (credencial) | No | La credencial **es** el control de acceso; en login aún no hay identidad establecida |
| `refresh_tokens` | `token_hash` (credencial) | No | Ídem; el hash es secreto e identifica la sesión |
| `memberships` | `user_id` (tras establecer identidad) | **Sí, de identidad** | `user_id = current_setting('app.user_id')::bigint` |
| negocio (futuras) | filtradas por organización | **Sí, de organización** | `organization_id = current_setting('app.organization_id')::bigint` |

Dos variables de sesión, dos contextos:

- **`app.user_id` (identidad)** — se fija en cuanto se conoce al usuario: en el `JwtAuthenticationFilter` (desde el claim `sub`) para peticiones autenticadas, y en `AuthService` (login/register/refresh) justo tras resolver el `User`, antes de tocar `memberships`.
- **`app.organization_id` (organización)** — se fija desde el claim `oid` del token, solo en peticiones autenticadas.

Ambas políticas fallan cerrado (`current_setting` sin segundo argumento lanza si la variable no está fijada), salvo el flujo de auth, que fija `app.user_id` explícitamente antes de leer `memberships`.

**Por qué esto responde a la revisión.** `GET /me/memberships` deja de depender de que el repositorio recuerde filtrar por `user_id`: aunque lo olvide, el motor solo devuelve las membresías cuyo `user_id` coincide con la identidad autenticada. `users` y `refresh_tokens` quedan fuera de RLS de forma **principiada**, no por comodidad: se acceden por una credencial (email/hash) que es en sí misma la autorización, y se leen *antes* de que exista identidad establecida (por eso no podrían llevar RLS de identidad sin un huevo-y-gallina).

### D3. `email` único **global** y `slug` **inmutable**

`UNIQUE (email)` global: una identidad por persona; login `POST /auth/login {email, password}` sin subdominios. `Organization.slug` es único y **se congela al crear**: aunque la organización cambie de nombre (`Clínica Sonrisa` → `Dental Perú`), el `slug` permanece, porque puede haberse usado en URLs, referencias o (a futuro) subdominios. El nombre visible es mutable; el `slug`, no.

### D4. El rol vive en `Membership`, no en `User`

`Membership.role` (`ADMIN` | `USER`) sustituye a `User.role`, interpretado siempre dentro de una organización.

**Consecuencia (BREAKING).** Refactor de `authentication`: `User` pierde `role`; `AccessClaims` pasa a `(userId, organizationId, email, role)` con el rol **derivado de la membresía**; el claim `role` del JWT se calcula al emitir; el `RouteGuard` del frontend sigue leyendo el rol de la sesión (cambia el origen, no el uso). `V6` mueve el rol de cada usuario a su membresía antes de eliminar la columna.

### D5. Multi-organización en el modelo, login de un paso en el MVP + contrato diferido documentado

**Decisión.** El modelo soporta N membresías, pero la única vía de alta en este change es el registro (D8), que crea una. Por tanto el login emite el token directo con esa organización.

**Contrato de login (para eliminar ambigüedad), parte diferida a `users`:**

```
POST /auth/login { email, password }
  → 200 { accessToken }                                    // exactamente 1 membresía activa  (ÚNICO caso en este change)
  → 200 { requiresOrganizationSelection: true,             // ≥ 2 membresías activas  (DIFERIDO a `users`)
          organizations: [{ id, name, slug, role }],
          selectionToken }
POST /auth/select-organization { selectionToken, organizationId }
  → 200 { accessToken }
```

Se descarta `409` para el caso multi-organización: no es un conflicto, es una elección; `200` + flag es lo correcto. En este change solo existe la primera rama; las otras dos quedan especificadas pero no implementadas.

### D6. Propagación a PostgreSQL: dos `set_config(..., true)` por transacción

RLS lee variables de sesión que **alguien** debe fijar; con pool, hacerlo por sesión contamina la conexión.

**Decisión.** Fijarlas **transaction-local** al comenzar cada transacción:

```sql
SELECT set_config('app.user_id', ?, true);          -- identidad (si hay)
SELECT set_config('app.organization_id', ?, true);  -- organización (si hay)
```

`true` = local a la transacción, revertida al `COMMIT`/`ROLLBACK`. Enganche previsto: un `Interceptor` de Hibernate (`afterTransactionBegin`) que lee de `IdentityContext`/`OrganizationContext`; alternativa: `DataSource` delegado. La elección exacta se valida en `apply` haciendo pasar los tests de aislamiento. Prerequisito satisfecho: `open-in-view: false`.

### D7. `FORCE ROW LEVEL SECURITY` y el usuario de base de datos

RLS **no aplica al propietario de la tabla**, y con un solo datasource Flyway crea las tablas con el mismo usuario que corre la app (sería owner y saltaría las políticas en silencio).

**Decisión.** `ALTER TABLE ... FORCE ROW LEVEL SECURITY` en `memberships` y en cada tabla de negocio: aplica las políticas **también al owner**, permitiendo conservar **un único datasource**.

**Requisito operativo, no negociable:** `DB_USERNAME` **no** puede ser `SUPERUSER` ni tener `BYPASSRLS`. Documentado en `docs/architecture.md` y `secrets.properties.example`. Descartada la alternativa de dos roles (Flyway/owner + app sin privilegios): exigiría un segundo datasource; `FORCE` da la misma garantía frente al bug de aplicación.

### D8. Registro autoservicio: `Organization` + `Settings` + `User` + `Membership` en una transacción

`AuthService.register`, atómico:

1. Valida que el email no exista (unicidad global).
2. Crea `Organization` (`name`, `slug` derivado/desambiguado, `type = CLINIC`, `status = ACTIVE`) y su `OrganizationSettings` con valores por defecto.
3. Crea `User` (identidad, `status = ACTIVE`, sin rol).
4. Fija `app.user_id` con el usuario recién creado y crea `Membership` (`user`, `organization`, `role = ADMIN`, `status = ACTIVE`) — el `INSERT` satisface la política de identidad de `memberships`.
5. Emite los tokens (D9).

Bajo una sola `@Transactional`: nunca queda una organización sin administrador ni una identidad sin membresía.

### D9. Claims del token y contexto solo desde el token

`AccessClaims` pasa a `(userId, organizationId, email, role)`; el JWT emite `sub`, `oid` y `role` (leído de la membresía al emitir). `JwtAuthenticationFilter`, tras validar el token, puebla `IdentityContext` (desde `sub`) y `OrganizationContext` (desde `oid`), que alimentan al resolver y a los `set_config` (D6), y expone el rol a Spring Security. El filtro **limpia ambos contextos en un `finally`** (un `ThreadLocal` sucio en un pool de hilos es una vía clásica de fuga).

**Regla dura (a `docs/backend.md`):** el `organizationId` (y el `userId`) se obtienen **exclusivamente** de `OrganizationContext`/`IdentityContext`, poblados desde el token firmado. **Prohibido** derivarlos de `@RequestHeader`, `@PathVariable` o `@RequestBody`. Un cliente no puede cambiar de organización manipulando la petición.

**BREAKING**: los access tokens previos no tienen `oid` y se rechazan. Aceptable: no hay producción.

### D10. RLS: una política real ahora (memberships) + mecanismo probado para el negocio

**Decisión.** Este change entrega:

1. **Una política RLS real y testeable ya**: la de identidad sobre `memberships` (`V7`). El aislamiento de `GET /me/memberships` se prueba de verdad contra PostgreSQL.
2. El **mecanismo** de propagación (D6), el `CurrentTenantIdentifierResolver` y la configuración de `@TenantId`, listos para las tablas de negocio.
3. Una **convención** en `docs/backend.md`: toda `@Entity` de negocio lleva `organization_id` + `@TenantId`, y su migración incluye `ENABLE`/`FORCE ROW LEVEL SECURITY` + la política `organization_isolation`. Cada capacidad de negocio aplica RLS a **su** tabla.
4. Una **prueba del aislamiento por organización** con una entidad-fixture de test (`@TenantId`) y su migración de test con RLS, sobre **PostgreSQL real vía Testcontainers** (H2 no soporta RLS), garantizando que el pipeline funciona de extremo a extremo cuando aterrice `patients`.

**Por qué.** No hay tablas de negocio todavía, pero sí una tabla puente real (`memberships`) que se protege ahora. Se evita tanto aplicar RLS a algo vacío como diferir el mecanismo y que `patients` lo reinvente. El coste de una tabla de negocio nueva se reduce a: columna + anotación + tres líneas SQL.

### D11. `User.status` y `Membership.status` son ciclos de vida distintos

**Decisión.** Dos enums, porque modelan cosas distintas:

- `User.status` = `ACTIVE | LOCKED | DISABLED` — la **identidad** en toda la plataforma (p. ej. cuenta comprometida o dada de baja globalmente).
- `Membership.status` = `ACTIVE | INACTIVE | SUSPENDED` — la **pertenencia** a *una* organización (p. ej. dejó de trabajar en esa clínica pero sigue activo en otra).

Una identidad `ACTIVE` puede tener una membresía `INACTIVE` y otra `ACTIVE`. El modelo los incluye desde ya; los **flujos** que los cambian (bloqueo, suspensión, invitación) se ejercitan en `users`. En este change solo se crean en `ACTIVE`.

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| **`DB_USERNAME` con `SUPERUSER`/`BYPASSRLS`** → RLS decorativa | `FORCE` (D7) + doc en `secrets.properties.example` + el test de RLS **fallaría** si el rol hiciera bypass |
| **Refactor del rol fuera de `User`** (D4) toca `authentication` archivada | Delta MODIFIED; `V6` migra el rol antes de eliminar la columna; tests de auth actualizados |
| **Dos variables de sesión** (D6) aumentan la superficie de propagación | Un único enganche (`afterTransactionBegin`) fija ambas desde los contextos; test concurrente + test de `/me/memberships` |
| **`memberships` leída sin `app.user_id` fijado** (login/register) → RLS la bloquearía | El flujo de auth fija `app.user_id` explícitamente tras resolver el `User`, antes de leer/insertar membresías (D2, D8) |
| **El aislamiento por organización no se prueba sobre tabla de negocio real** hasta `patients` | Test de fixture con Testcontainers (D10.4); convención obligatoria por capacidad en `docs/testing.md` |
| **Los slices con H2 no ven RLS** | El aislamiento de motor se prueba solo en Testcontainers; H2 valida el filtrado de aplicación, no la barrera de motor (documentado) |
| **`ThreadLocal` no limpiado** → petición N ve el contexto de la N-1 | Limpieza de ambos contextos en `finally` + test concurrente |
| **`CurrentTenantIdentifierResolver` sin contexto** (arranque, Flyway, jobs) | Centinela cuando no hay contexto; las entidades de negocio nunca se consultan fuera de una petición |
| **Drift del contrato OpenAPI** | `pnpm generate:api` en este change; gate de CI |
| **Tokens existentes invalidados** (D9) | No hay producción. **BREAKING** en `proposal.md` |
| **Andamiaje especulativo** (`OrganizationSettings`/`OrganizationType` sin uso aún) | Coste mínimo (una tabla 1:1 con defaults y un enum en varchar); decisión consciente por la meta de template reutilizable |

**Trade-off asumido:** capacidad técnica sin valor visible que retrasa `users`/`patients`. Se acepta porque su coste crece de forma superlineal con cada capacidad de negocio: hoy es una migración sobre tablas casi vacías; después de `treatments` y `billing` son N tablas y una migración de datos clínicos reales.

## Migration Plan

**Migraciones** (el repo va por `V5`):

- **`V6__organizations.sql`**
  1. `CREATE TABLE organizations (id identidad PK, name, slug UNIQUE, type, status, created_at, updated_at)`.
  2. `CREATE TABLE organization_settings (organization_id BIGINT PK REFERENCES organizations(id), timezone, locale, currency)`.
  3. `CREATE TABLE memberships (id PK, user_id FK→users, organization_id FK→organizations, role, status, created_at, updated_at, UNIQUE(user_id, organization_id))`.
  4. **Backfill idempotente**: si hay usuarios, crear una organización por defecto (`type = CLINIC`) + settings y, por cada usuario, una membresía que arrastre su `users.role`. Si `users` está vacía, no crea nada.
  5. `ALTER TABLE users ADD COLUMN status ... DEFAULT 'ACTIVE'`; `ALTER TABLE users DROP COLUMN role`.
  6. `refresh_tokens` **no** cambia (el org activo se deriva; ver revisión #3 del historial).

- **`V7__rls.sql`**
  1. `ALTER TABLE memberships ENABLE ROW LEVEL SECURITY; ... FORCE ROW LEVEL SECURITY;`
  2. `CREATE POLICY membership_identity_isolation ON memberships USING (user_id = current_setting('app.user_id')::bigint);`
  - Las tablas de negocio no existen aún → sus políticas por organización llegan con cada capacidad de negocio.

**Orden de despliegue.** Flyway corre al arrancar; el backfill deja el sistema consistente antes de aceptar tráfico. El claim `oid` es nuevo → sesiones vivas invalidadas: corte de sesión, no de datos.

**Rollback.** `V6`/`V7` no se revierten hacia atrás en Flyway; el rollback real es desplegar la versión anterior. Es un **rollback con pérdida parcial** (eliminado `users.role`, creada multi-membresía): la versión anterior no puede representar el estado nuevo. Mitigación: no hay producción; a futuro exigiría una migración de compensación. Punto sin retorno inmediato: el claim `oid` (solo obliga a re-loguear).

**Verificación** (gates de `CLAUDE.md`): `openspec validate add-multi-tenancy --strict` · `mvn verify` (incluye RLS de `memberships` + fixture de organización, ambos Testcontainers) · `pnpm generate:api` sin drift · `pnpm validate`.

## Open Questions

1. **Enganche de los `set_config` (D6).** `Interceptor.afterTransactionBegin` vs `DataSource` delegado. Se resuelve escribiendo primero los tests de RLS y haciéndolos pasar. Confirmar el registro del interceptor como bean en Spring Boot 4 / Hibernate 7.2.
2. **Testcontainers en el stack de test.** `docs/testing.md` describe slices con H2. Introducir un slice de integración con PostgreSQL (Testcontainers) para los tests de RLS; confirmar que encaja con `mvn verify` en CI (Docker en el runner).
3. **Centinela del resolver** sin contexto: `Long` reservado vs fallo ruidoso. Preferencia: fallar ruidosamente en consultas de negocio. Comprobar qué permite Hibernate 7.2 sin código artificial.
4. **`slug` en colisión.** `"Clínica Sonrisas"` dos veces → `clinica-sonrisas`, `clinica-sonrisas-2`. Propuesta: desambiguar automáticamente (el `slug` no es visible aún).
5. **Valores por defecto de `OrganizationSettings`.** ¿`timezone`/`locale`/`currency` fijos (p. ej. `America/Lima` / `es-PE` / `PEN`) o derivados de algo en el registro? Propuesta: defaults fijos ahora; ajustables en `users`.
