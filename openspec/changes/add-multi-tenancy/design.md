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

**Aislamiento de escritura, no solo de lectura.** La garantía cubre `INSERT`/`UPDATE`, no solo `SELECT`, por dos mecanismos que se refuerzan:
1. **`@TenantId`** hace que Hibernate **auto-rellene** `organization_id` con la organización del contexto al persistir — el código de aplicación no puede escribir en otra organización aunque lo intente.
2. La policy escrita solo con `USING` **también gobierna la escritura**: en PostgreSQL, si no se declara `WITH CHECK`, la expresión de `USING` se aplica igualmente a las filas nuevas (`INSERT`) y modificadas (`UPDATE`). Así, un `INSERT` con un `organization_id` ajeno —vía consulta nativa, por ejemplo— es **rechazado** por el motor.

Es decir: la org A no puede leer, insertar ni modificar filas de la org B. La política de identidad de `memberships` se comporta igual (la escritura de una membresía exige `user_id = app.user_id`, lo que el registro satisface fijando el contexto antes del `INSERT`, D8).

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

### D6. Propagación a PostgreSQL: un `set_config` combinado, transaction-local

RLS lee variables de sesión que **alguien** debe fijar; con pool, hacerlo por sesión contamina la conexión.

**Decisión.** Fijar ambas variables **transaction-local** al comenzar cada transacción, en **una sola sentencia** (un único round-trip, no dos):

```sql
SELECT set_config('app.user_id', ?, true), set_config('app.organization_id', ?, true);
```

`true` = local a la transacción, revertida al `COMMIT`/`ROLLBACK`. Enganche previsto: un `Interceptor` de Hibernate (`afterTransactionBegin`) que lee de `IdentityContext`/`OrganizationContext`; alternativa: `DataSource` delegado. La elección exacta se valida en `apply` haciendo pasar los tests de aislamiento. Prerequisito satisfecho: `open-in-view: false`.

**Compatibilidad con H2 (slices de test).** H2 no tiene `set_config`, así que el interceptor rompería *toda* transacción en los slices con H2. Solución: registrar en H2 un **alias** de función (`CREATE ALIAS set_config FOR ...`) que actúe de no-op y devuelva el valor, mediante script de inicialización solo de test. El código de producción queda **agnóstico** (nada de ramas "¿es H2?"). En H2 no hay políticas RLS, así que el alias solo evita el fallo de la sentencia; el `@TenantId` de aplicación sí se ejercita. La barrera de motor se prueba únicamente en Testcontainers (D10).

**Valores nulos.** Si no hay identidad u organización en contexto (flujos de sistema), el parámetro va `NULL`; `current_setting` en las políticas lo evalúa como no-coincidencia → cero filas (fail-closed), salvo el flujo de auth que fija `app.user_id` explícitamente (D2, D8).

**Supuestos de pool de conexiones.** El mecanismo es transaction-local *precisamente por el pool*: una variable de sesión (`SET`) contaminaría la conexión al devolverla al pool y la heredaría la petición siguiente (de otra organización) → `set_config(..., true)` se revierte en cada `COMMIT`/`ROLLBACK`. Supuestos: **un** pool de runtime (HikariCP, default de Spring Boot) con el rol `odontorisas_app`; Flyway abre su propia conexión transitoria con `odontorisas_admin` al arrancar (no sale del pool de la app). El **tamaño** del pool (`maximum-pool-size`, timeouts) es tuning de **despliegue**, no de este change. Además, al ser transaction-local, el mecanismo es compatible con un pooler externo (p. ej. PgBouncer en *transaction mode*) si a futuro se escala — un `SET` de sesión no lo sería.

### D7. Dos roles de base de datos (menor privilegio) + `FORCE` como defensa en profundidad

RLS **no aplica al propietario de la tabla**. El error clásico es que la app corra como owner (que Flyway usa para crear las tablas) y salte todas las políticas.

**Decisión (revisada tras revisión humana).** Separar **dos roles**, principio de menor privilegio:

| Rol | Uso | Privilegios |
|---|---|---|
| `odontorisas_admin` | **Solo Flyway** (arranque). Propietario de tablas | DDL completo (`CREATE`/`ALTER`/`ENABLE RLS`) |
| `odontorisas_app` | **Runtime** de la aplicación | Solo DML (`SELECT`/`INSERT`/`UPDATE`/`DELETE`); **no** owner |

Como `odontorisas_app` **no es propietario**, PostgreSQL le aplica RLS de forma **ineludible** — sin depender críticamente de `FORCE`. Y al tener solo DML, una SQL injection **no puede** ejecutar `ALTER TABLE ... DISABLE ROW LEVEL SECURITY` (requiere ser owner). Se cierra exactamente el hueco que motiva la doble barrera. `FORCE ROW LEVEL SECURITY` **se mantiene** como defensa en profundidad (por si alguna tabla la crea el admin y algo consulta con ese rol).

**No requiere un segundo datasource Java** — corrige mi objeción anterior: Spring Boot da credenciales propias a Flyway sobre el mismo datasource de la app:

```yaml
spring:
  datasource:
    username: odontorisas_app        # runtime, DML, no-owner
    password: ${DB_APP_PASSWORD}
  flyway:
    user: odontorisas_admin          # migraciones, owner, DDL
    password: ${DB_ADMIN_PASSWORD}
```

**Coste real (a gestionar en las migraciones):** las tablas las crea `odontorisas_admin`, así que hay que conceder acceso al rol de app. Se resuelve una vez con `ALTER DEFAULT PRIVILEGES FOR ROLE odontorisas_admin GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO odontorisas_app` (+ `USAGE` en secuencias) en la primera migración, de modo que las tablas futuras hereden los GRANT.

**Requisito operativo, no negociable:** ningún rol de la app puede ser `SUPERUSER` ni tener `BYPASSRLS` (los saltan RLS en silencio). Documentado en `docs/architecture.md` y `secrets.properties.example` (dos juegos de credenciales). Los tests de aislamiento con Testcontainers corren como `odontorisas_app` para validar el camino real (no como owner).

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

**Observabilidad.** El filtro añade `organizationId` y `userId` al **MDC** (junto al `traceId` ya existente, RFC 9457 + MDC) y los retira en el mismo `finally`. En un sistema multi-tenant, "¿por qué esta consulta devolvió 0 filas?" es difícil de depurar sin saber en qué organización corría; tenerlo en cada línea de log lo hace trivial. Nunca se loguean datos sensibles, solo los identificadores.

**BREAKING**: los access tokens previos no tienen `oid` y se rechazan. Aceptable: no hay producción.

### D10. RLS: una política real ahora (memberships) + mecanismo probado para el negocio

**Decisión.** Este change entrega:

1. **Una política RLS real y testeable ya**: la de identidad sobre `memberships` (`V7`). El aislamiento de `GET /me/memberships` se prueba de verdad contra PostgreSQL.
2. El **mecanismo** de propagación (D6), el `CurrentTenantIdentifierResolver` y la configuración de `@TenantId`, listos para las tablas de negocio.
3. Una **convención** en `docs/backend.md`: toda `@Entity` de negocio lleva `organization_id` + `@TenantId`, **un índice sobre `organization_id`** (sin él, RLS filtra con un *seq scan* enmascarado), y su migración incluye `ENABLE`/`FORCE ROW LEVEL SECURITY` + la política `organization_isolation`. Cada capacidad de negocio aplica RLS a **su** tabla. *(Índices como parte de la política de aislamiento, no como optimización posterior: la RLS convierte el filtro de tenant en implícito y omnipresente, así que debe estar indexado desde el día uno.)*
4. Una **prueba del aislamiento por organización** con una entidad-fixture de test (`@TenantId`) y su migración de test con RLS, sobre **PostgreSQL real vía Testcontainers** (H2 no soporta RLS), garantizando que el pipeline funciona de extremo a extremo cuando aterrice `patients`.

**Por qué.** No hay tablas de negocio todavía, pero sí una tabla puente real (`memberships`) que se protege ahora. Se evita tanto aplicar RLS a algo vacío como diferir el mecanismo y que `patients` lo reinvente. El coste de una tabla de negocio nueva se reduce a: columna + anotación + tres líneas SQL.

### D11. `User.status` y `Membership.status` son ciclos de vida distintos

**Decisión.** Dos enums, porque modelan cosas distintas:

- `User.status` = `ACTIVE | LOCKED | DISABLED` — la **identidad** en toda la plataforma (p. ej. cuenta comprometida o dada de baja globalmente).
- `Membership.status` = `ACTIVE | INACTIVE | SUSPENDED` — la **pertenencia** a *una* organización (p. ej. dejó de trabajar en esa clínica pero sigue activo en otra).

Una identidad `ACTIVE` puede tener una membresía `INACTIVE` y otra `ACTIVE`. El modelo los incluye desde ya; los **flujos** que los cambian (bloqueo, suspensión, invitación) se ejercitan en `users`. En este change solo se crean en `ACTIVE`.

### D12. Todo acceso a entidades multi-tenant ocurre en una transacción (enforced)

`set_config(..., true)` es **transaction-local**: una consulta en auto-commit (sin `@Transactional`, o con propagación que suspenda la transacción) fija la variable y la descarta en la misma sentencia, y la siguiente consulta de esa conexión del pool corre **sin contexto** → RLS la bloquea de forma inesperada.

**Decisión.** Regla dura (a `docs/backend.md`): toda operación que acceda a una entidad bajo RLS (`memberships` y toda tabla de negocio) SHALL ejecutarse dentro de una transacción activa (`@Transactional`, o `@Transactional(readOnly = true)` para lectura). Se **enforcea con ArchUnit** (el proyecto ya usa ArchUnit para las capas): los métodos de servicio/repositorio que tocan entidades multi-tenant deben ser transaccionales. `open-in-view: false` refuerza esto.

**Relación con el fallo ruidoso (D2).** Esto es la contraparte correcta de mantener `current_setting` **sin** `missing_ok`: se rechazó (en revisión) usar `missing_ok = true` en las políticas de negocio, porque convertiría el fallo ruidoso —el detector de "consultaste fuera de una transacción con contexto"— en un silencioso "cero filas" que enmascara justo el bug que esta decisión previene. Fallar ruidoso + enforce transaccional se refuerzan; `missing_ok` los anularía.

### D13. Propagación del contexto a hilos asíncronos (convención documentada, implementación diferida)

Los contextos viven en `ThreadLocal`, así que **no** se heredan en `@Async`/`@Scheduled`/paralelismo. Hoy no existe ningún uso async que toque datos de tenant, por lo que **no** se construye infraestructura sin consumidores.

**Decisión.** Documentar en `docs/backend.md` dos patrones, y aplicarlos cuando aparezca el primer async:

- **Async derivado de una petición** (p. ej. paralelizar dentro de un request): heredar el contexto con un `TaskDecorator` en el `ThreadPoolTaskExecutor` (capturar en el hilo padre, fijar en el hijo, **limpiar en `finally`**).
- **Jobs de fondo** (`@Scheduled`, colas): **no** heredan un tenant de ninguna petición; deben fijar el contexto **explícitamente** por cada tenant que procesen (normalmente iterando organizaciones). Heredar aquí sería un error.

Se deja como Open Question / tarea diferida; no bloquea este change.

### D14. Autenticar exige identidad, membresía y organización **activas**

`User.status` y `Membership.status` (D11) más `Organization.status` existen desde este change, pero un `status` que nadie comprueba no protege. **Decisión.** Login, refresh y (a futuro) select-organization SHALL exigir, para conceder o conservar sesión sobre una organización:

- `user.status = ACTIVE` (identidad no bloqueada/deshabilitada en la plataforma),
- `membership.status = ACTIVE` (pertenencia vigente a *esa* organización),
- `organization.status = ACTIVE` (la organización no suspendida).

Si cualquiera falla: login → credenciales rechazadas (sin filtrar cuál); refresh → sesión cerrada (como un refresco inválido). El **refresh reevalúa** estos estados en cada renovación, de modo que suspender una membresía **corta la sesión** en la siguiente renovación del access token, sin esperar a su expiración natural sería insuficiente — la ventana es el TTL del access (15 min).

**Alcance ahora.** En este change todo se crea `ACTIVE`, así que el gate nunca se dispara en producción todavía; pero la comprobación se implementa y se **testea inyectando estados no-activos directamente** (un test puede insertar una membresía `SUSPENDED` aunque ningún endpoint la produzca aún). Cuando `users` añada suspensión/bloqueo, la seguridad ya está puesta, no hay que recordar añadirla.

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| **Rol de app con `SUPERUSER`/`BYPASSRLS` o siendo owner** → RLS decorativa | Rol de app **no-owner solo DML** (D7) + `FORCE` como defensa en profundidad + doc en `secrets.properties.example`; el test de RLS (que corre como `odontorisas_app`) **fallaría** si el rol hiciera bypass |
| **SQL injection desactiva RLS** (`ALTER TABLE ... DISABLE RLS`) | El rol de app no es owner ni tiene DDL → no puede alterar políticas (D7) |
| **`set_config` transaction-local + consulta en auto-commit** → contexto perdido, RLS bloquea | `@Transactional` obligatorio en acceso a entidades RLS, enforced por ArchUnit (D12) |
| **H2 no tiene `set_config`** → slices de test rotos al abrir transacción | Alias de función en H2 solo de test; producción agnóstica (D6) |
| **Contexto no heredado en hilos `@Async`/`@Scheduled`** | Sin uso async hoy; convención documentada (D13); `TaskDecorator` para async-de-request, contexto explícito para jobs de fondo |
| **Refactor del rol fuera de `User`** (D4) toca `authentication` archivada | Delta MODIFIED; `V6` migra el rol antes de eliminar la columna; tests de auth actualizados |
| **Dos variables de sesión** (D6) aumentan la superficie de propagación | Un único enganche (`afterTransactionBegin`) fija ambas desde los contextos; test concurrente + test de `/me/memberships` |
| **`memberships` leída sin `app.user_id` fijado** (login/register) → RLS la bloquearía | El flujo de auth fija `app.user_id` explícitamente tras resolver el `User`, antes de leer/insertar membresías (D2, D8) |
| **El aislamiento por organización no se prueba sobre tabla de negocio real** hasta `patients` | Test de fixture con Testcontainers (D10.4); convención obligatoria por capacidad en `docs/testing.md` |
| **Los slices con H2 no ven RLS** | El aislamiento de motor se prueba solo en Testcontainers; H2 valida el filtrado de aplicación, no la barrera de motor (documentado) |
| **`ThreadLocal` no limpiado** → petición N ve el contexto de la N-1 | Limpieza de ambos contextos en `finally` + test concurrente |
| **`CurrentTenantIdentifierResolver` sin contexto** (arranque, Flyway, jobs) | Centinela cuando no hay contexto; las entidades de negocio nunca se consultan fuera de una petición |
| **Drift del contrato OpenAPI** | `pnpm generate:api` en este change; gate de CI |
| **Tokens existentes invalidados** (D9) | No hay producción. **BREAKING** en `proposal.md` |
| **Escritura cruzada de tenant** (insertar/actualizar en otra organización) | `@TenantId` auto-rellena `organization_id`; la policy `USING` gobierna también `INSERT`/`UPDATE` (fallback `WITH CHECK`) → el motor rechaza la escritura ajena (D2) |
| **`status` que nadie comprueba** → membresía suspendida o usuario deshabilitado sigue con sesión | Gate en login/refresh sobre `user`/`membership`/`organization` `ACTIVE` (D14); refresh reevalúa en cada renovación (ventana = TTL del access) |
| **RLS sin índice** → cada consulta de negocio es un *seq scan* filtrado por la policy | Convención: índice sobre `organization_id` en toda tabla de negocio, como parte de la política de aislamiento (D10) |
| **Depurar "0 filas" en multi-tenant** sin saber la organización | `organizationId`/`userId` en el MDC junto al `traceId` (D9) |

**Trade-off asumido:** capacidad técnica sin valor visible que retrasa `users`/`patients`. Se acepta porque su coste crece de forma superlineal con cada capacidad de negocio: hoy es una migración sobre tablas casi vacías; después de `treatments` y `billing` son N tablas y una migración de datos clínicos reales.

## Migration Plan

**Migraciones** (el repo va por `V5`):

- **`V6__db_roles_grants.sql`** (o incluido al inicio de `V6`): asegurar que existe el rol de app `odontorisas_app` y fijar herencia de privilegios — `ALTER DEFAULT PRIVILEGES FOR ROLE odontorisas_admin GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO odontorisas_app` + `... GRANT USAGE, SELECT ON SEQUENCES TO odontorisas_app`, y GRANT sobre las tablas ya existentes (`users`, `refresh_tokens`). La creación de los roles en sí es de provisión del entorno (no de Flyway); se documenta en `secrets.properties.example` (ver Open Question 6).
- **`V6__organizations.sql`**
  1. `CREATE TABLE organizations (id identidad PK, name, slug UNIQUE, type, status, created_at, updated_at)`.
  2. `CREATE TABLE organization_settings (organization_id BIGINT PK REFERENCES organizations(id), timezone, locale, currency)`.
  3. `CREATE TABLE memberships (id PK, user_id FK→users, organization_id FK→organizations, role, status, created_at, updated_at, UNIQUE(user_id, organization_id))` + índice sobre `organization_id` (el único `(user_id, organization_id)` ya sirve las consultas que lideran por `user_id`, p. ej. la policy RLS de identidad; falta el que lidera por organización, para "miembros de la organización X").
  4. **Backfill idempotente**: si hay usuarios, crear una organización por defecto (`type = CLINIC`) + settings y, por cada usuario, una membresía que arrastre su `users.role`. Si `users` está vacía, no crea nada.
  5. `ALTER TABLE users ADD COLUMN status ... DEFAULT 'ACTIVE'`; `ALTER TABLE users DROP COLUMN role`.
  6. `refresh_tokens` **no** cambia (el org activo se deriva; ver revisión #3 del historial).
  7. GRANT explícito de las tablas nuevas al rol `odontorisas_app` (o confiar en el `ALTER DEFAULT PRIVILEGES` anterior si se aplicó antes de crearlas).

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
6. **Provisión de los roles de BD (D7).** La creación de `odontorisas_admin`/`odontorisas_app` y la asignación de contraseñas es de entorno, no de Flyway. Definir cómo se documenta/automatiza en local (hoy el entorno usa un único `postgres`/`root`) y en despliegue: script de bootstrap idempotente vs instrucciones en `secrets.properties.example`. Confirmar además que los tests Testcontainers crean y usan el rol de app no-owner.
7. **Enganche del alias `set_config` en H2 (D6).** Script de inicialización de test (`CREATE ALIAS`) vs bean de configuración de test; confirmar que aplica a todos los slices que abran transacción sin romper los que usan `@DataJpaTest`.
