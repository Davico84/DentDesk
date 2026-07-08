## Context

OdontoRisas pasa de gestionar **una** clínica a ser un **SaaS multi-clínica**. La motivación y el alcance están en `proposal.md`; aquí se decide el **cómo**.

**Estado actual** (lo único construido, capacidad `authentication`):

- `users` (`id`, `email` único global, `password_hash`, `role`, `full_name`, timestamps) y `refresh_tokens` (`user_id`, `token_hash`…). Migraciones hasta `V5`.
- Access token JWT stateless con claims `userId`, `email`, `role` (`AccessClaims`); refresh token persistido y rotado.
- **Cero entidades de negocio.** `Patient`, `Appointment`, `Treatment`, `Invoice`… son todavía conceptuales en `docs/domain.md`.

**Restricciones del stack** (cerrado en `docs/architecture.md`):

- Spring Boot **4.0.6** → Hibernate ORM **7.2.12.Final** (verificado con `mvn dependency:tree`).
- PostgreSQL + Flyway (`ddl-auto: none`). **Un solo datasource** (`application.yml`), sin perfiles; credenciales externalizadas.
- `spring.jpa.open-in-view: false` — ya vigente, y prerequisito para que el contexto de clínica no sobreviva a la transacción.
- Los test slices de backend usan **H2** (`docs/testing.md`). H2 **no** implementa Row-Level Security → probar RLS exige PostgreSQL real (Testcontainers).

**Restricción del dominio:** los datos son **clínicos**. Una fuga entre clínicas no es un bug, es un incidente de datos de salud. El diseño asume que el código de aplicación **se equivocará** en algún momento y busca una segunda barrera para las tablas de negocio.

**Historia de este diseño.** Una primera versión metía `tenant_id` en `users` con `@TenantId`, lo que chocaba con el login (hay que encontrar al usuario *antes* de conocer su clínica) y obligaba a una política RLS permisiva (*fail-open*) sobre `users`. La revisión —modelo Identity/Clinic/Membership— elimina ese problema de raíz: la identidad es global y no se aísla, y el aislamiento recae solo sobre las tablas de negocio.

**Dato relevante de la versión:** en Hibernate 7.2, `@TenantId` filtra en la capa de aplicación. La generación **automática** de políticas RLS a partir de `@TenantId` llega en **Hibernate 8.0**. Aquí las políticas se escriben a mano en Flyway (cuando existan tablas de negocio); cuando el stack suba a Hibernate 8 podrá revisarse.

## Goals / Non-Goals

**Goals**

1. Ninguna consulta de negocio puede devolver filas de otra clínica, **aunque el desarrollador olvide el filtro**.
2. Una persona = una identidad = un email, aunque trabaje en varias clínicas.
3. El dominio clínico, que aún no existe, nace multi-tenant sin retrabajo: una capacidad nueva anota la entidad y hereda el aislamiento.
4. El login y el registro no se complican con subdominios ni pasos extra en el caso MVP (una membresía por usuario).

**Non-Goals** (ver `proposal.md → Non-goals`)

Selector de clínica activa e invitaciones (capacidad `users`) · planes/suscripciones/facturación SaaS · rol de plataforma y panel cross-tenant · subdominio por clínica · gestión de staff · personalización y cuotas.

---

## Decisions

### D1. Modelo Identity / Clinic / Membership

**Decisión.** Separar tres conceptos que hoy conviven en `User`:

| Entidad | Es | Campos clave | Aislada por clínica |
|---|---|---|---|
| `User` | **Identidad** global de una persona | `email` (único global), `password_hash`, `full_name`, `status` | **No** (es global; no tiene rol ni clínica) |
| `Clinic` | El **tenant** (cliente del SaaS) | `name`, `slug` (único), `status` | Es el sujeto del aislamiento |
| `Membership` | La **pertenencia** usuario↔clínica | `user_id`, `clinic_id`, `role`, `status`, único `(user_id, clinic_id)` | Dominio de auth (ver D2) |

**Por qué.** El rol nunca fue un atributo de la persona, sino de su relación con una clínica: el mismo profesional puede ser `ADMIN` en la suya y rol clínico en otra. Poner identidad, tenant y pertenencia en tres tablas es el modelo estándar de SaaS multi-tenant y hace que el `email` único global sea natural en vez de forzado.

**Alternativa considerada — `User` con `tenant_id` (diseño previo).** Descartada: fundía identidad y pertenencia, impedía multi-clínica con un solo correo, ponía el rol en el sitio equivocado y forzaba el *fail-open* de RLS sobre `users` (ver "Historia" arriba).

### D2. RLS solo sobre tablas de negocio; el dominio de auth no se aísla por clínica

**Decisión.** Dos conjuntos de tablas, y la distinción es permanente:

| Conjunto | Tablas | `clinic_id` | `@TenantId` | RLS por clínica | Se consulta por |
|---|---|---|---|---|---|
| **Dominio de auth** | `users`, `memberships`, `refresh_tokens` | `users` no; `memberships`/`refresh_tokens` sí (como FK de negocio-auth, no discriminador) | **No** | **No** | `email` / `user_id` / `token_hash` (claves de acceso globales) |
| **Negocio** | todas las futuras (`patients`, `appointments`, `treatments`, `invoices`…) | Sí | **Sí** | Sí, *fail-closed* | filtradas por la clínica del contexto |

**Por qué desaparece el *fail-open*.** El flujo de autenticación consulta el dominio de auth **antes** de tener una clínica en contexto: login busca `users` por email y `memberships` por `user_id`; refresh busca `refresh_tokens` por `token_hash`. Ninguna de esas tablas contiene datos de negocio, y todas se consultan por claves que **son** la clave de control de acceso (el propio email/usuario autenticado). Por eso no llevan RLS por clínica y no hace falta relajar ninguna política: simplemente no están en el conjunto aislado. El control de acceso de `memberships` es "cada quien ve sus propias membresías", garantizado en la capa de repositorio filtrando por el `user_id` autenticado.

Política de las tablas de negocio — falla cerrado, porque `current_setting` sin segundo argumento lanza error si la variable no está fijada:

```sql
CREATE POLICY clinic_isolation ON patients
  USING (clinic_id = current_setting('app.clinic_id')::bigint);
```

### D3. `email` único **global**

`UNIQUE (email)` global. Es la consecuencia natural de D1 (una identidad por persona) y lo que mantiene el login como `POST /auth/login {email, password}` sin subdominios ni selector. `Clinic.slug` se persiste desde ya (único) para dejar abierta una futura resolución por subdominio sin tocar el modelo de negocio.

### D4. El rol vive en `Membership`, no en `User`

**Decisión.** `Membership.role` (`ADMIN` | `USER`) sustituye a `User.role`. El rol se interpreta siempre dentro de una clínica.

**Consecuencia (BREAKING sobre lo construido).** Hay que refactorizar la capacidad `authentication`: `User` pierde `role`; `AccessClaims` pasa a `(userId, clinicId, email, role)` con el rol **derivado de la membresía activa**; el claim `role` del JWT se calcula al emitir el token; el `RouteGuard` del frontend sigue leyendo el rol de la sesión (cambia de dónde sale, no cómo se usa). La migración `V6` mueve el rol actual de cada usuario a su membresía antes de eliminar la columna.

### D5. Multi-clínica en el modelo, login de un paso en el MVP

**Decisión.** El modelo soporta N membresías por usuario, pero en este change la **única** vía de alta de membresías es el registro (D8), que crea exactamente una. Por tanto, en la práctica, todo usuario tiene una sola membresía y el login emite el token directamente con esa clínica.

**Comportamiento definido para el futuro (no implementado aquí):** si un usuario llegara a tener varias membresías activas, el login devolvería la lista de clínicas y el token se emitiría con la clínica elegida. Ese selector, el cambio de clínica activa y las invitaciones pertenecen a la capacidad `users`. Se especifica el caso MVP (una membresía) como camino feliz; el caso multi-membresía se declara explícitamente diferido.

**Por qué.** Entrega el modelo correcto sin pagar la UI de selección ni endpoints de invitación ahora. No hay retrabajo: cuando llegue `users`, el login ya autentica por identidad y solo se añade la ramificación por número de membresías.

### D6. Propagación a PostgreSQL: `set_config(..., true)` por transacción

RLS lee una variable de sesión que **alguien** tiene que fijar. Con un pool de conexiones, fijarla por sesión contamina la conexión al devolverla al pool.

**Decisión.** Fijarla **transaction-local** al comenzar cada transacción, a partir de la clínica del contexto:

```sql
SELECT set_config('app.clinic_id', ?, true);   -- true = local a la transacción, revertida al COMMIT/ROLLBACK
```

El enganche previsto es un `Interceptor` de Hibernate (`afterTransactionBegin`), que se ejecuta dentro de la transacción y tiene acceso a la sesión; alternativa: `DataSource` delegado. La elección exacta se valida en `apply` haciendo pasar el test de aislamiento. Prerequisito ya satisfecho: `open-in-view: false`.

### D7. `FORCE ROW LEVEL SECURITY` y el usuario de base de datos

**El error clásico:** habilitar RLS y creer que protege. **No aplica al propietario de la tabla** — y con un solo datasource, Flyway crea las tablas de negocio con el mismo usuario con el que corre la aplicación, que sería el owner y saltaría toda política silenciosamente.

**Decisión.** Cada tabla de negocio se creará con `ALTER TABLE ... FORCE ROW LEVEL SECURITY`, que aplica las políticas **también al propietario**. Esto permite conservar **un único datasource** y no tocar `application.yml`.

**Requisito operativo, no negociable:** `DB_USERNAME` **no** puede ser `SUPERUSER` ni tener `BYPASSRLS`. Ningún `FORCE` protege de un superusuario. Se documenta en `docs/architecture.md` y `secrets.properties.example`.

**Alternativa considerada.** Dos roles (Flyway/owner + aplicación sin privilegios). Más estricto pero exige un segundo datasource y coordinar `GRANT`s. Se descarta para el MVP: `FORCE` da la misma garantía frente al escenario que importa (bug de aplicación).

### D8. Registro autoservicio: `User` + `Clinic` + `Membership` en una transacción

`AuthService.register` pasa a ser atómico:

1. Valida que el email no exista (unicidad global, D3).
2. Crea `Clinic` (`name` = `clinicName`, `slug` derivado y desambiguado si colisiona, `status = ACTIVE`).
3. Crea `User` (identidad global, sin rol).
4. Crea `Membership` (`user`, `clinic`, `role = ADMIN`, `status = ACTIVE`).
5. Emite los tokens con clínica y rol (D9).

Todo bajo la misma `@Transactional`: nunca queda una clínica sin administrador ni una identidad sin membresía. Es la **única** vía de alta de clínicas y de membresías en este change. Estas escrituras ocurren sin clínica en el contexto, lo cual es correcto: el dominio de auth no está bajo RLS por clínica (D2).

### D9. La clínica activa viaja en el access token (claim `cid`); el rol se deriva de la membresía

`AccessClaims` pasa a `(userId, clinicId, email, role)`. Al emitir el token, el `role` se lee de la `Membership(user, clinic)`. `JwtAuthenticationFilter`, tras validar el token, puebla un `ClinicContext` (`ThreadLocal`) que alimenta al `CurrentTenantIdentifierResolver` y al `set_config` de D6, y expone el rol a Spring Security. El filtro **limpia el contexto en un `finally`** — un `ThreadLocal` sucio en un pool de hilos es otra vía clásica de fuga.

La clínica **no se lee nunca de la petición** (ni header, ni path, ni body): solo del token firmado.

**BREAKING**: los access tokens previos no tienen `cid` y se rechazan. Aceptable: no hay usuarios en producción.

### D10. RLS se entrega como mecanismo + convención, probado con una entidad-fixture

**Problema.** El aislamiento por clínica recae sobre las tablas de negocio (D2), pero **en este change no existe ninguna**. No hay tabla real a la que aplicar una política.

**Decisión.** Este change entrega la **infraestructura** de aislamiento, no una política sobre datos vacíos:

1. El mecanismo de propagación del contexto (D6), el `CurrentTenantIdentifierResolver` y la configuración de `@TenantId` en Hibernate.
2. Una **convención** documentada en `docs/backend.md`: *toda* `@Entity` de negocio lleva `clinic_id` + `@TenantId`, y su migración incluye `ENABLE`/`FORCE ROW LEVEL SECURITY` + la política `clinic_isolation`. Cada capacidad de negocio aplica RLS a **su** tabla.
3. Una **prueba del mecanismo** con una entidad-fixture de test (`@TenantId`) y una migración de test que crea su tabla con RLS, ejecutada sobre **PostgreSQL real vía Testcontainers** (H2 no soporta RLS). Este test es el que garantiza que, cuando `patients` aterrice, el pipeline ya funciona de extremo a extremo: filtrado de aplicación **y** barrera de motor.

**Por qué.** Evita dos males: aplicar RLS a `users` solo para "tener algo" (incorrecto, D2), o diferir todo el mecanismo hasta `patients` y que quien la construya reinvente la propagación de contexto y descubra tarde el problema del owner/`BYPASSRLS`. El coste de `patients` se reduce a: columna + anotación + tres líneas de SQL heredando un pipeline probado.

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| **`DB_USERNAME` con `SUPERUSER`/`BYPASSRLS`** → RLS decorativa y nadie se entera | `FORCE ROW LEVEL SECURITY` (D7) + documentarlo en `secrets.properties.example` + el test de fixture (D10) **fallaría** si el rol hiciera bypass |
| **Refactor del rol fuera de `User`** (D4) toca la capacidad `authentication` archivada | Delta MODIFIED sobre `authentication`; `V6` migra el rol a la membresía antes de eliminar la columna; tests de auth actualizados |
| **El mecanismo RLS no se prueba sobre una tabla de negocio real** hasta `patients` | Test de fixture con Testcontainers (D10) que ejercita el pipeline completo ahora; convención obligatoria por capacidad en `docs/testing.md` |
| **Los slices con H2 no ven RLS** → falsa sensación de aislamiento en los tests rápidos | El aislamiento de motor se prueba **solo** en el test Testcontainers; se documenta que H2 valida el filtrado de aplicación, no la barrera de motor |
| **`ThreadLocal` no limpiado** → la petición N ve la clínica de la N-1 | Limpieza en `finally` del filtro + test concurrente |
| **`CurrentTenantIdentifierResolver` sin contexto** (arranque, Flyway, flujo auth, jobs) → Hibernate lanza si devuelve `null` | El resolver devuelve un centinela cuando no hay contexto; las entidades de negocio nunca se consultan fuera de una petición. El dominio de auth no tiene `@TenantId`, así que no invoca el resolver |
| **Membresía leída sin contexto en login/refresh** | Correcto por diseño: `memberships` es dominio de auth, sin RLS por clínica (D2); se filtra por el `user_id` autenticado en el repositorio |
| **Drift del contrato OpenAPI** (`api-type-contracts` exige sin drift) | `pnpm generate:api` dentro de este change; el gate de CI lo verifica |
| **Tokens existentes invalidados** (D9) | No hay producción. Declarado **BREAKING** en `proposal.md` |

**Trade-off asumido:** esta capacidad técnica no entrega valor visible al usuario final y retrasa `users`/`patients`. Se acepta porque su coste crece de forma superlineal con cada capacidad de negocio: hoy es una migración sobre tablas casi vacías; después de `treatments` y `billing` son N tablas, N repositorios y una migración de datos clínicos reales.

## Migration Plan

**Migraciones** (el repo va por `V5`):

- **`V6__clinics_and_memberships.sql`**
  1. `CREATE TABLE clinics (id identidad PK, name, slug UNIQUE, status, created_at, updated_at)`.
  2. `CREATE TABLE memberships (id PK, user_id FK→users, clinic_id FK→clinics, role, status, created_at, updated_at, UNIQUE(user_id, clinic_id))`.
  3. **Backfill idempotente**: si hay usuarios, crear una clínica por defecto y, por cada usuario, una membresía que arrastre su `users.role` actual. Si `users` está vacía, no crea nada.
  4. `ALTER TABLE users DROP COLUMN role`.
  5. `ALTER TABLE refresh_tokens ADD COLUMN clinic_id BIGINT` (FK→clinics; backfill desde la membresía del usuario; luego `NOT NULL`).

- **RLS**: **no** hay migración de políticas en este change (no existen tablas de negocio, D10). El mecanismo y la convención se entregan en código y docs; la prueba vive en el test Testcontainers con una tabla-fixture. Cada capacidad de negocio traerá su `ENABLE`/`FORCE`/`POLICY`.

**Orden de despliegue.** Flyway corre al arrancar; el backfill deja el sistema consistente antes de aceptar tráfico. El claim `cid` es nuevo → todas las sesiones vivas se invalidan: corte de sesión, no de datos.

**Rollback.** `V6` no se revierte hacia atrás en Flyway; el rollback real es desplegar la versión anterior de la aplicación. Pero **es un rollback con pérdida parcial**: si se eliminó `users.role` y se creó multi-membresía, la versión anterior (rol en `users`, sin clínicas) no puede representar el estado nuevo. Mitigación: no hay producción; en el futuro, un rollback exigiría una migración de compensación. El único punto sin retorno inmediato es el claim `cid`, que solo obliga a re-loguear.

**Verificación** (gates de `CLAUDE.md`): `openspec validate add-multi-tenancy --strict` · `mvn verify` (incluye el test de fixture con Testcontainers) · `pnpm generate:api` sin drift · `pnpm validate`.

## Open Questions

1. **Enganche exacto de `set_config` (D6).** `Interceptor.afterTransactionBegin` de Hibernate vs `DataSource` delegado. Se resuelve escribiendo primero el test de fixture (D10) y haciéndolo pasar. Confirmar el registro del interceptor como bean en Spring Boot 4 / Hibernate 7.2.
2. **Testcontainers en el stack de test.** `docs/testing.md` describe slices con H2. Hay que introducir un perfil/slice de integración con PostgreSQL (Testcontainers) para el test de RLS. Confirmar dependencia y que encaja con el `mvn verify` de CI (arranque de Docker en el runner).
3. **RLS de `refresh_tokens`.** Lleva `clinic_id` para conocer la clínica activa al renovar, pero pertenece al dominio de auth (D2) y se consulta por `token_hash`. Confirmar que **no** necesita RLS por clínica y que el filtrado por identidad basta.
4. **Centinela del resolver** sin contexto: `Long` reservado vs fallo ruidoso. Preferencia: fallar ruidosamente en consultas de negocio; el dominio de auth no invoca el resolver. Comprobar qué permite Hibernate 7.2 sin código artificial.
5. **`slug` en colisión.** `"Clínica Sonrisas"` dos veces → `clinica-sonrisas`, `clinica-sonrisas-2`. Propuesta: desambiguar automáticamente (el `slug` no es visible aún) en vez de rechazar el registro.
6. **`status` de `User`.** ¿La identidad global necesita `status` propio además del `status` de cada membresía? Propuesta: sí (permite deshabilitar una identidad en toda la plataforma), pero su uso efectivo puede diferirse a `users`.
