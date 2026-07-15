## Why

`multi-tenancy` dejó el aislamiento **a medias, a propósito**, y este change lo cierra.

Los ladrillos están puestos: `TenantSession.bind(userId, organizationId)` fija en una sola sentencia las variables de sesión que consume Row-Level Security. Pero **nadie las fija automáticamente**. Su propio javadoc lo declara:

> *"La propagación **automática** por transacción para las tablas de negocio se finaliza cuando aterrice **la primera capacidad de negocio** y pueda validarse con sus tests de RLS; **su punto de enganche vivirá aquí**."*

Hasta hoy no hacía falta: la única tabla con RLS era `memberships` (aislada por *identidad*), y los flujos de autenticación fijaban el contexto **a mano**. **Ninguna tabla estaba aislada por organización, porque no había ni una tabla de negocio.**

Y ahí está lo grave. El spec de `multi-tenancy` **ya promete**:

> *"THEN el sistema establece la organización A como contexto de la transacción — **AND todas las consultas de negocio de esa petición quedan restringidas a la organización A**"*

**Esa promesa nunca fue verificable.** No había consultas de negocio que restringir. Es un Scenario **escrito, aprobado e incumplido en silencio** — el mismo patrón que ya nos mordió con la detección de drift en `api-type-contracts`, donde el spec exigía algo que CI no hacía.

> ⚠️ **Este change no *añade* esa promesa: la *paga*.** Los Scenarios de aislamiento de negocio de `multi-tenancy` (incluido el de *"operación sin organización en el contexto → la operación falla"*) **ya están escritos y aprobados**; lo que faltaba es que hubiera algo que aislar y un mecanismo que lo hiciera. Por eso el delta **no los repite**: los deja intactos y añade **lo único que es genuinamente nuevo** — que el aislamiento sea **automático** y que el código de negocio **no pueda** degradarlo.

**Ahora es el momento de cerrarlo**, justo antes de que aterrice la primera capacidad de negocio (`patients`) y todas las que cuelgan de ella. Si no, la primera consulta de pacientes **falla cerrada** — o, peor, alguien "lo arregla" fijando el contexto a mano en cada servicio, y entonces el aislamiento pasa a depender de que nadie se olvide.

## What Changes

- **El contexto de organización se establece SOLO**, en toda transacción de una petición autenticada, a partir del token firmado. El código de negocio **no tiene que pedirlo**.
- **Una capacidad de negocio nueva queda aislada sin escribir código de aislamiento.** Quien construya `patients`, `treatments` o `appointments` **no tiene que acordarse de nada**: declara su entidad siguiendo la convención (`docs/backend.md §8.1`) y el aislamiento **ya está**.
- **Y no puede hacerlo aunque quiera**: un *tripwire* de ArchUnit **rompe la build** si una clase de `service` o `presentation` intenta fijar el contexto por su cuenta. Sin él, la garantía sería un párrafo en un documento — es decir, disciplina otra vez.
- **Los flujos previos a la sesión siguen funcionando**: en el alta de una organización todavía no hay token del que leer el contexto (la organización se está creando en esa misma transacción), así que conservan su fijación explícita.
- **Verificado contra PostgreSQL real** con una tabla de negocio de prueba y **como el rol de aplicación no-owner** — el único contexto en el que RLS aplica de verdad. El test **no fija el contexto**: si lo fijara, no probaría el enganche.

**Por qué es un change propio y no parte de `add-patients`:** esto pertenece a **`multi-tenancy`**, no a la ficha del paciente. Lo necesitan **todas** las capacidades de negocio; que le tocara a `patients` es un accidente de calendario (le tocó ser la primera), no un diseño. Además tiene otro **perfil de riesgo** —es el cimiento— y **se verifica solo**, sin necesitar una capacidad de negocio real.

## Capabilities

### New Capabilities

*(ninguna)*

### Modified Capabilities

- `multi-tenancy`: sus requirements ya exigen que las consultas de negocio queden restringidas a la organización de la petición (y que una operación sin contexto **falle**), pero **no dicen nada de cómo se garantiza** — y de hecho **no se garantizaba**: el contexto no se establecía en las transacciones. Este change **no reescribe esos requirements: los cumple**. Añade uno nuevo: que el aislamiento SHALL ser **automático** —una capacidad de negocio nueva queda aislada **sin escribir código de aislamiento**— y que el código de negocio **NO SHALL poder** fijar el contexto por su cuenta.

## Impact

- **Backend — `infra/tenancy`**: el **enganche automático** del contexto por transacción (el punto que `TenantSession` tenía reservado). Lee `IdentityContext` y `OrganizationContext`, los `ThreadLocal` que `JwtAuthenticationFilter` **ya puebla** desde el token. Es el único cambio de comportamiento.
- **Backend — `service/auth`**: sin cambios funcionales; conserva su fijación explícita porque el registro crea la organización **dentro de la misma transacción** (no hay token del que leer el contexto).
- **Tests**: test de aislamiento **con una tabla de negocio de prueba** (contra PostgreSQL real, como rol no-owner) que demuestra que una entidad **sin código de aislamiento** queda igualmente aislada — **sin que el test fije el contexto**; y **regla de ArchUnit** que rompe la build si `service`/`presentation` intenta fijarlo.
- **`docs/backend.md §8.1`**: documentar que el contexto **se fija solo** y **qué NO hay que escribir** en una capacidad de negocio nueva.
- **Sin migraciones, sin cambios de API, sin frontend.**

## Non-goals

- **No se construye ninguna entidad de negocio.** `Patient` y el resto llegan con sus propias capacidades; aquí solo se prueba el mecanismo con una **tabla de prueba**.
- **No se cambia la convención de aislamiento** (`docs/backend.md §8.1`): la entidad sigue llevando su columna, su `@TenantId`, su índice y su política. Lo que cambia es que **el contexto ya no hay que pedirlo**.
- **No se resuelve la propagación a hilos `@Async`/`@Scheduled`**: sigue sin haber ninguno, y el *tripwire* de ArchUnit sigue rompiendo la build si aparece uno sin cablear.
- **No se toca el selector de organización activa** (cambiar de clínica): es otro pendiente de `multi-tenancy`, con su propio change.
