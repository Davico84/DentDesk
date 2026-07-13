## Context

`patients` es la **primera capacidad de negocio**, y eso la carga con una responsabilidad que va más allá de su CRUD.

`multi-tenancy` dejó el aislamiento **a medias, a propósito**. Los ladrillos están (`TenantSession.bind(userId, organizationId)` fija en una sola sentencia las variables que consume RLS), pero **nadie las fija automáticamente**. Su propio javadoc lo declara:

> *"La propagación **automática** por transacción para las tablas de negocio se finaliza cuando aterrice **la primera capacidad de negocio** y pueda validarse con sus tests de RLS; **su punto de enganche vivirá aquí**."*

Hasta hoy no hacía falta: la única tabla con RLS era `memberships` (aislada por *identidad*), y los flujos de auth fijaban el contexto **a mano**. **`patients` trae la primera tabla aislada por organización** — y sin el enganche, `app.organization_id` nunca se fija, RLS **falla cerrado** y *ninguna* consulta de pacientes funciona.

Restricción heredada (`docs/backend.md §8.1`, **no negociable**): columna + `@TenantId`, **índice** sobre `organization_id`, `ENABLE`/`FORCE ROW LEVEL SECURITY` con política `organization_isolation` **sin `missing_ok`** (fallo ruidoso), acceso **siempre transaccional**, y el tenant **solo desde el token firmado** — jamás de la petición.

Y el criterio que este proyecto ya se ganó a pulso (con el `OperationCustomizer` del contrato): **por defecto, no por disciplina.** Si aislar exige que cada dev recuerde llamar a algo, alguien lo olvidará — y el olvido no da error: **da una fuga de datos entre clínicas**.

## Goals / Non-Goals

**Goals:**

- El contexto de tenant se fija **solo** en toda transacción de una petición autenticada. Quien construya `treatments` o `appointments` **no tiene que acordarse de nada**.
- `Patient` cumple la convención de aislamiento **completa**, verificada contra PostgreSQL real: una clínica **nunca** ve ni modifica pacientes de otra.
- La unicidad del documento es **por clínica** (`ADR-0004`).
- El **menor de edad** exige responsable, y la minoría **se deriva** de la fecha de nacimiento.
- El **listado con búsqueda** sirve el caso de uso diario de recepción.

**Non-Goals:**

- Alertar de alergias/antecedentes (eso es de `treatments`; aquí se **registran**).
- Odontograma, tratamientos, citas: cuelgan de aquí, pero no se construyen.
- Roles específicos: los tres gestionan pacientes; basta estar autenticado en la organización.
- Portabilidad del historial entre clínicas (`ADR-0004`).

## Decisions

### D1 — El contexto de tenant se fija en el **inicio de la transacción**, no en los servicios ⭐

Es **la decisión de este change**. Todo lo demás depende de que esto funcione.

**Decisión:** engancharlo en el **punto por el que pasan TODAS las transacciones**, de modo que fijar el contexto sea **imposible de olvidar**. `TenantSession` ya tiene el método (`bind`) y el sitio reservado; falta el disparador.

- *Alternativa A — llamar a `TenantSession.bind(...)` al principio de cada método de servicio.* **Rechazada de plano.** Es "por disciplina": funciona hasta que alguien olvida una línea, y **el olvido no falla ruidosamente para el que lo comete** — simplemente RLS bloquea (o peor, si el contexto quedara sucio, expone). Es el mismo error que ya rechazamos al documentar los errores del contrato.
- *Alternativa B — aspecto AOP sobre `@Transactional`.* Frágil: depende del **orden de los advisors** (el aspecto tiene que quedar **dentro** de la transacción, y el advisor transaccional es el más interno por defecto). Un orden mal configurado no rompe la build: rompe el aislamiento **en silencio**. **Rechazada.**
- *Alternativa C — interceptor de Hibernate (`afterTransactionBegin`).* Elegante y es el hook conceptualmente correcto. **Candidata**, con la duda de si el API expone la sesión para ejecutar el `set_config` (ver Open Question 1).
- *Alternativa D — extender el gestor de transacciones (`doBegin`).* El único punto que **garantiza** que ninguna transacción se escapa, sin depender de ordenación de aspectos. **Candidata preferida.**

**Reglas del enganche** (independientes de dónde acabe viviendo):

| Contexto de la petición | Qué se fija |
|---|---|
| Identidad **y** organización (petición autenticada) | ambas variables |
| Solo identidad | solo la de identidad |
| Ninguna (p. ej. login, registro, actuator) | **nada** — y RLS **falla cerrado** si se toca una tabla de negocio. Es lo correcto. |

Los flujos de **auth conservan su fijación explícita**: en el **registro** aún no hay token del que leer el contexto (la organización se está creando en esa misma transacción), así que el enganche automático no tiene de dónde sacarlo.

> ⚠️ **Este mecanismo se implementa y se valida ANTES que el CRUD.** Si no funciona, todo lo demás es humo. La primera tarea es probarlo con el test de aislamiento contra PostgreSQL real. *(La lección del change anterior: lo que no se verifica contra la realidad, miente.)*

### D2 — La unicidad del documento es **por clínica**, y el documento es **opcional**

**Decisión:** restricción única sobre **`(organization_id, document)`**, **nunca** sobre `document` a secas — eso impediría que dos clínicas atendieran a la misma persona, rompiendo `ADR-0004`. El intento de duplicar dentro de la misma clínica responde **`409`**, con `type` que lo identifica.

**El documento es opcional** (`NULL` permitido): un bebé o un menor pueden no tener DNI todavía, y **la clínica no puede negarse a atenderlo por eso**. PostgreSQL permite múltiples `NULL` en un índice único, así que la restricción sigue siendo correcta.

### D3 — La minoría de edad **se deriva**, y arrastra al responsable

**Decisión:** `isMinor` **NO es un campo**: se calcula de `birthDate` (< 18 años). Un campo declarable se desincroniza el día del cumpleaños — un dato derivado, nunca.

**Si el paciente es menor, los datos del responsable son obligatorios** (nombre, parentesco, teléfono). Es una **validación condicional**: no se expresa con un `@NotNull` por campo, hace falta validación **a nivel de objeto** (en servidor) y su equivalente en el cliente.

> La paridad de validación cliente/servidor (`docs/coding-style.md §7`) es aquí más delicada que de costumbre, porque la regla es condicional. El servidor es la autoridad: si el cliente se despista, su `400` trae el **detalle por campo** y se pinta junto al input (ya lo tenemos resuelto).

### D4 — La anamnesis se guarda como **listas**, no como texto libre

`allergies`, `conditions` (antecedentes) y `currentMedication` se almacenan como **listas de valores**, no como un párrafo.

**La razón es clínica, no técnica:** `treatments` tendrá que **recorrerlas para alertar** antes de un procedimiento (*"este paciente es alérgico a la penicilina"*). **Un blob de texto libre no se puede alertar de forma fiable** — y aquí un falso negativo tiene consecuencias reales sobre una persona.

- *Alternativa — un catálogo codificado de alergias/patologías.* Es lo correcto a largo plazo, pero exige un vocabulario clínico que hoy no existe. **Fuera de alcance**; las listas de texto son el punto intermedio honesto: estructuradas lo justo para poder alertar.

### D5 — Dar de baja a un paciente **NO lo borra**

**Decisión:** la baja es **lógica** (`status`: activo / inactivo). El listado muestra los activos por defecto.

**Razón:** la historia clínica tiene **retención legal** y, además, el paciente cuelga de tratamientos, citas e facturas. Un borrado físico o es imposible (integridad referencial) o destruye información que **no nos pertenece destruir**. "Dar de baja" en una clínica significa **archivar**, no eliminar.

### D6 — El listado es `GET` + query params (`ADR-0002`)

Búsqueda por nombre o documento, con paginación. **No** se usa el método HTTP `QUERY` (es un borrador; el contrato no podría describirlo y orval no generaría cliente — rompería la cadena "el contrato es la fuente única de verdad").

Los errores transversales (`401`/`403`/`500`) **se inyectan solos** en el contrato (`docs/backend.md §10.2`): solo hay que declarar el status de éxito, el `400` de validación y el `409` del documento duplicado.

## Risks / Trade-offs

- **[El enganche de tenant no funciona a la primera]** ⭐ → Se implementa y **se valida con el test de aislamiento ANTES de escribir una línea de CRUD**. Si falla, se cambia de hook (D1 tiene candidatas) sin haber construido nada encima.
- **[Un olvido de aislamiento no falla: filtra datos entre clínicas]** → Por eso el mecanismo es **automático** (D1) y hay un **test de aislamiento obligatorio** contra PostgreSQL real. El `FORCE ROW LEVEL SECURITY` + el rol de aplicación no-owner hacen que el motor sea la última red.
- **[La validación condicional del menor se desincroniza entre cliente y servidor]** (D3) → El servidor es la autoridad y su `400` trae detalle por campo. Los Scenarios cubren **ambos** lados.
- **[Las listas de anamnesis son texto sin vocabulario]** (D4) → Aceptado: `treatments` podrá alertar mostrándolas; codificarlas es una evolución, no un requisito de hoy.
- **[El índice por organización se olvida]** → `docs/backend.md §8.1` lo exige explícitamente ("el índice **es parte de la política de aislamiento**, no una optimización posterior"): sin él, RLS filtra con *seq scan* y el listado se degrada con cada clínica nueva.

## Migration Plan

1. **El enganche de tenant** (D1) + su test de aislamiento. **Nada más hasta que esto pase en verde.**
2. Migración **V9**: tabla `patients` con `organization_id`, **índice**, `FORCE ROW LEVEL SECURITY` y política `organization_isolation`; unique `(organization_id, document)`.
3. Entidad (`@TenantId`), repositorio, servicio y controller.
4. Regenerar contrato + cliente (el guardián `ContractDriftIT` rompe la build si no).
5. Frontend: listado con búsqueda + ficha.

**Rollback:** revertir el commit; la V9 se deshace con una migración de reversa (no hay datos en producción).

## Open Questions

1. **¿Dónde vive exactamente el enganche: interceptor de Hibernate o gestor de transacciones?** (D1, alternativas C y D). Se decide **empíricamente en la primera tarea**: se implementa el candidato preferido y se valida con el test de aislamiento; si el API no coopera, se cambia al otro. **Lo que el spec exige es *que el contexto se fije solo*, no dónde** — la implementación es libre mientras la garantía se cumpla.
2. **¿Qué campos son obligatorios en el alta?** Propuesta: `fullName` y `birthDate` (la fecha es imprescindible: de ella se derivan la minoría de edad y la dentición del odontograma). El **documento es opcional** (D2). El resto, opcional. Confirmar al implementar el formulario.
