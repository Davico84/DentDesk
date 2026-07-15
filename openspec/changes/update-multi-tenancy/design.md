## Context

El aislamiento multi-tenant tiene **dos capas** (`docs/backend.md §8.1`): filtrado de aplicación (Hibernate `@TenantId`) y **Row-Level Security** en PostgreSQL. La segunda —la que de verdad protege, porque actúa aunque la aplicación se equivoque— depende de una variable de sesión: `app.organization_id`, fijada con `set_config(..., true)` (**transaction-local**).

**Hoy nadie la fija.** `TenantSession.bind(userId, organizationId)` existe y funciona, pero solo lo invocan los flujos de autenticación, y únicamente para la identidad. Mientras la única tabla con RLS fue `memberships`, bastó.

Con la primera tabla aislada **por organización**, deja de bastar: sin `app.organization_id`, la política `organization_isolation` compara contra un `current_setting` inexistente y —al estar declarada **sin `missing_ok`**— **lanza un error**. RLS falla cerrado. Ninguna consulta de negocio funciona.

**El criterio que gobierna esta decisión** ya se lo ganó este proyecto con el `OperationCustomizer` del contrato:

> **Por defecto, no por disciplina.**

Y aquí pesa más que en ninguna parte. Si aislar exigiera que cada dev recordara una llamada, algún día alguien la olvidaría — y **ese olvido no da un error**: da una **fuga de historia clínica entre clínicas**.

## Goals / Non-Goals

**Goals:**

- El contexto de organización se fija **solo** en toda transacción de una petición autenticada.
- Una entidad de negocio nueva queda aislada **sin una línea de código de aislamiento**.
- La ausencia de contexto **falla ruidosamente**; nunca devuelve vacío.
- Los flujos de auth (registro, login) **siguen funcionando** aunque no haya organización activa todavía.
- Verificado **contra PostgreSQL real**, como el **rol de aplicación no-owner** — el único contexto donde RLS aplica de verdad.

**Non-Goals:**

- Construir entidades de negocio (se prueba con una **tabla de prueba**).
- Cambiar la convención de `docs/backend.md §8.1` (columna, `@TenantId`, índice, política).
- Propagación a hilos `@Async`/`@Scheduled` (siguen sin existir; el *tripwire* de ArchUnit los sigue bloqueando).
- El selector de organización activa (otro pendiente de `multi-tenancy`).

## Decisions

### D1 — El enganche va en el **inicio de la transacción**, no en los servicios ⭐

**Decisión:** engancharlo en el punto por el que pasan **TODAS** las transacciones, de modo que fijar el contexto sea **imposible de olvidar**.

- *Alternativa A — llamar a `TenantSession.bind(...)` al principio de cada servicio.* **Rechazada de plano.** Es "por disciplina": funciona hasta que alguien olvida una línea. Y el olvido **no falla ruidosamente para quien lo comete**: simplemente RLS bloquea su consulta… o, si el contexto quedara sucio de una petición anterior, **expone datos de otra clínica**. Es exactamente el error que ya rechazamos al documentar los errores del contrato — pero aquí el precio es la privacidad clínica.
- *Alternativa B — aspecto AOP sobre `@Transactional`.* Frágil: depende del **orden de los advisors**. El aspecto debe quedar **dentro** de la transacción, y el advisor transaccional es el más interno por defecto. Un orden mal configurado **no rompe la build: rompe el aislamiento en silencio**. **Rechazada** — un mecanismo de seguridad no puede depender de una precedencia sutil.
- *Alternativa C — interceptor de Hibernate (`afterTransactionBegin`).* Es el hook conceptualmente correcto y el que la `multi-tenancy` original tenía en mente. **Candidata**, con la duda de si el API expone la sesión para poder ejecutar el `set_config` (Open Question 1).
- *Alternativa D — extender el gestor de transacciones (`doBegin`).* El único punto que **garantiza** que ninguna transacción se escapa, sin depender de ordenación de aspectos ni de que el API de un interceptor coopere. **Candidata preferida.**

**De dónde sale el contexto:** ya está servido. `JwtAuthenticationFilter` puebla, desde el token verificado, dos `ThreadLocal` — **`IdentityContext`** (`app.user_id`) y **`OrganizationContext`** (`app.organization_id`)— y los limpia en su `finally`. **El enganche lee de ahí**, no del `SecurityContext`. Y funciona **porque todo ocurre en el mismo hilo**: por eso el *tripwire* de ArchUnit contra `@Async`/`@Scheduled` no es un detalle suelto, **es lo que sostiene el mecanismo**.

> 🚨 **Trampa: `TenantSession` NO se puede reutilizar tal cual dentro de `doBegin`.**
>
> `TenantSession` inyecta un `@PersistenceContext EntityManager` (proxy compartido). `JpaTransactionManager.doBegin(...)` **enlaza el `EntityManagerHolder` al hilo al FINAL**; si se invoca el proxy antes, no encuentra el recurso enlazado y **abre un `EntityManager` nuevo sobre otra conexión**. Como `set_config(..., true)` es **transaction-local**, la variable **se fija en la conexión equivocada y se descarta**: la transacción real nunca la recibe.
>
> No es una fuga (RLS falla cerrado), pero **el código "se ve bien"** y se depura a ciegas. El `set_config` debe ejecutarse **sobre la sesión creada dentro de `doBegin`**, es decir: `super.doBegin(...)` primero y **después** obtener el `EntityManagerHolder` ya enlazado (o desenvolver la `Session`/`Connection`) y fijar allí las variables.

**Reglas del enganche** (valen sea cual sea el hook elegido):

| Contexto de la petición | Qué se fija |
|---|---|
| Identidad **y** organización (petición autenticada normal) | **ambas** variables |
| Solo identidad | solo la de identidad |
| **Ninguna** (login, registro, actuator, tareas internas) | **nada** — y si se toca una tabla de negocio, **RLS falla cerrado**. Es lo correcto. |

### D2 — Los flujos de auth conservan su fijación explícita (y no es una excepción incómoda: es la única posible)

En el **registro**, la organización **se está creando en esa misma transacción**. No hay token, no hay contexto, y no puede haberlo: el `oid` que el enganche leería **todavía no existe**.

**Decisión:** el enganche automático **no toca** esos flujos (no hay contexto que propagar), y `AuthService` conserva su `bindIdentity` explícito, que es lo que permite insertar la membresía bajo la RLS de identidad.

> No es una grieta en el "por defecto, no por disciplina": es que **en el bootstrap no hay nada que propagar**. La regla automática cubre todo lo que viene *después* de que exista una sesión — que es el 100% del código de negocio.

### D3 — La verificación NO espera a `patients`: se hace con una **tabla de prueba**

**Decisión:** el mecanismo se valida **en este mismo change**, con una tabla de negocio **de prueba** que lleva RLS por organización y **ni una línea de código de aislamiento**.

Ya existe el patrón: `OrganizationRlsIT` monta una tabla-fixture (`test_business`) con su política y verifica el aislamiento **como el rol de aplicación no-owner**. Aquí se extiende para probar lo que faltaba: que el contexto **se fija solo**, sin que nadie lo pida.

> 🚨 **El test NO debe fijar el contexto.** Si llama a `TenantSession.bind(...)`, está probando `bind()` —que ya funciona— y **no el enganche**: sería un test decorativo que pasa en verde mientras el mecanismo está roto. Debe **poblar `OrganizationContext` como lo haría el filtro** (esa es la entrada real del sistema) y entrar por una operación `@Transactional` normal. La ausencia de `bind` en el test **es** el aserto.

> Esto es lo que permite que el change **se sostenga solo**, sin acoplarse a `patients`. Y es lo que hace honesto el requirement: *"una entidad de negocio nueva queda aislada sin código propio"* se demuestra **con una entidad que no tiene código propio**.

### D4 — El fallo ruidoso ya está especificado: aquí solo se **cumple**

`multi-tenancy` **ya tiene** el Scenario *"Operación de negocio sin organización en el contexto → la operación falla → no devuelve datos de ninguna organización"*. No hacía falta escribirlo: hacía falta que **existiera una tabla de negocio** con la que fuera comprobable. Por eso el delta **no lo repite** — repetirlo daría dos scenarios para una regla y dos tests que se desincronizan.

La política se declara **sin `missing_ok`** (ya es así), y **se fija por test en este change**. La alternativa —`missing_ok` → `NULL` → `WHERE organization_id = NULL` → **cero filas**— es **mucho peor**: un listado vacío se confunde con *"esta clínica no tiene pacientes"*. El fallo quedaría **oculto** hasta que alguien se pregunte por qué su clínica aparece vacía.

> Un error ruidoso se arregla en cinco minutos. Un vacío silencioso puede tardar meses en detectarse — y mientras tanto, nadie sabe si el aislamiento funciona o simplemente no hay datos.

### D5 — La regla se defiende **con la build**, no con un documento ⭐

**Decisión:** una regla de ArchUnit **prohíbe** que las capas de negocio (`service`, `presentation`) llamen a `TenantSession`. Solo pueden hacerlo `infra/tenancy` y los flujos de auth (D2).

**Por qué es imprescindible y no un extra:** el riesgo real de este mecanismo no es que falle — es que **alguien lo desactive sin querer**. El día que un fallo cerrado bloquee una consulta, la reacción natural será *"pues fijo el contexto aquí y ya"*. Con eso, el aislamiento vuelve a depender de la disciplina, **y nadie se entera**.

Mitigarlo con un párrafo en `docs/backend.md` sería exactamente el error que este change combate: **una promesa que nadie verifica**. Ya tenemos el patrón montado —`MultiTenancyArchitectureTest` es un *tripwire* de ArchUnit que ya bloquea `@Async`/`@Scheduled` sin cablear—; esto se añade ahí.

> El documento explica **por qué**. La regla impide **que pase**. Hacen falta los dos, pero solo uno de ellos rompe la build.

## Risks / Trade-offs

- **[⚠️ El `set_config` acaba en la conexión equivocada]** ⭐ → **Es el fallo más probable de este change**, y no da error: la variable se fija sobre un `EntityManager` distinto del de la transacción y se descarta con él. Ver la trampa detallada en D1. Mitigación: el test de aislamiento (D3) **no puede pasar** si esto ocurre — es su primera línea de defensa.
- **[El hook elegido no coopera con el API]** (Open Question 1) → Por eso hay **dos candidatas** (C y D). La tarea 1 es implementar la preferida y **validarla con el test de aislamiento**; si el API no coopera, se cambia a la otra **sin haber construido nada encima**.
- **[Forzar el `set_config` al abrir la transacción obliga a adquirir la conexión antes de tiempo]** → Se pierde la adquisición perezosa de conexión. Aceptado: **cualquier transacción que abrimos va a usar la base de datos de todos modos**, y el coste de una sentencia (que además fija *las dos* variables de una vez) es despreciable frente a la garantía que compra.
- **[El enganche se aplica también a transacciones sin contexto]** (actuator, tareas internas) → Por diseño **no fija nada** en ese caso, así que no rompe nada: solo las tablas de negocio quedarían inaccesibles, que es exactamente lo que debe pasar.
- **[Alguien "arregla" un fallo cerrado fijando el contexto a mano en su servicio]** → Es el riesgo real a medio plazo, y **no se mitiga con documentación**: la regla de ArchUnit de **D5** rompe la build. El documento explica el porqué; la regla impide que pase.
- **[El enganche depende del `ThreadLocal`, y un `@Async` lo perdería]** → El *tripwire* de ArchUnit que ya bloquea `@Async`/`@Scheduled` sin cablear **es parte del mecanismo**, no una restricción aparte. Si algún día hace falta uno, hay que propagar el contexto explícitamente **antes** de que la build lo permita.

## Migration Plan

Sin migraciones de datos ni cambios de API. **Un solo cambio de comportamiento**: las transacciones autenticadas fijan el contexto.

1. El enganche (D1) — **cuidado con la conexión**: el `set_config` va sobre la sesión de `doBegin`, no sobre el proxy de `TenantSession`.
2. El test de aislamiento con tabla de prueba (D3) —**sin fijar el contexto**— y el test fail-closed (D4).
3. La **regla de ArchUnit** que impide fijar el contexto desde `service`/`presentation` (D5).
4. Comprobar que los flujos de auth siguen verdes (D2).
5. Documentar en `docs/backend.md §8.1` **qué no hay que escribir**.

**Rollback:** revertir el commit. No hay estado persistido.

## Open Questions

1. **¿El enganche vive en el interceptor de Hibernate o en el gestor de transacciones?** (D1, alternativas C y D). Se decide **empíricamente en la primera tarea**: se implementa la preferida (el gestor de transacciones, porque garantiza que ninguna transacción se escapa) y se valida con el test de aislamiento. Si no funciona, se cambia a la otra. **Lo que el spec exige es que el contexto se fije solo, no dónde** — la implementación es libre mientras la garantía se cumpla.
