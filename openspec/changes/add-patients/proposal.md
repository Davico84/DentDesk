## Why

`patients` es **el núcleo del dominio**: de la ficha del paciente cuelgan `dental-chart`, `treatments`, `appointments`, `images` y `patient-billing`. Sin ella no hay producto.

Pero además —y esto es lo que de verdad define este change— **es la primera capacidad de negocio**, y eso la convierte en **la que cierra el aislamiento multi-tenant**:

> `multi-tenancy` dejó los ladrillos puestos (`TenantSession.bind(userId, organizationId)` fija las variables que consume RLS) pero **difirió deliberadamente el enganche automático**: *"la propagación automática por transacción para las tablas de negocio se finaliza cuando aterrice la primera capacidad de negocio y pueda validarse con sus tests de RLS"*.
>
> Hasta hoy nadie lo necesitaba: la única tabla con RLS era `memberships`, y los flujos de auth fijaban la identidad a mano. **Con `patients` llega la primera tabla aislada por organización** — y sin ese enganche, `app.organization_id` nunca se fija y **RLS falla cerrado en toda consulta de pacientes**.

Así que este change hace dos cosas inseparables: **construye la ficha del paciente** y **completa el mecanismo que garantiza que una clínica jamás vea los datos de otra**. La segunda es la que hereda todo el dominio clínico que viene después.

También estrena, por primera vez en una capacidad de negocio, las convenciones que dejamos preparadas: el contrato que **inyecta los errores transversales solos** y los tipos de error **derivados** en el frontend.

## What Changes

- **El enganche automático del contexto de tenant** (lo que faltaba): al abrir cualquier transacción de una petición autenticada, las variables que consume RLS quedan fijadas **solas**, a partir del token firmado. **Por defecto, no por disciplina**: quien construya `treatments` o `appointments` **no tendrá que acordarse de nada**.
- **La ficha del paciente**: alta, consulta, edición y baja, más el **listado con búsqueda** —el caso de uso diario de la recepción—.
- **Aislamiento real, verificado**: `Patient` sigue la convención completa de `docs/backend.md §8.1` (columna + `@TenantId`, índice, `ENABLE`/`FORCE ROW LEVEL SECURITY` con política `organization_isolation`, acceso transaccional), con el **test de aislamiento obligatorio** contra PostgreSQL real.
- **La unicidad del documento es POR CLÍNICA** (`ADR-0004`): la restricción va sobre `(organizationId, document)`, **nunca** sobre `document` a secas — eso impediría que dos clínicas atendieran a la misma persona.
- **Menores de edad**: si el paciente es menor, los datos del **responsable/tutor** pasan a ser **obligatorios**. La minoría de edad **se deriva de la fecha de nacimiento**, no se declara a mano.
- **Anamnesis** (alergias, antecedentes, medicación): se **registra** aquí. El *alertar* antes de un tratamiento lo consumirá `treatments`; aquí solo se guarda bien.
- Frontend: **listado con búsqueda** y **ficha** (alta/edición), con tipos y errores **derivados del contrato**.

## Capabilities

### New Capabilities

- `patients`: la ficha clínico-administrativa de la persona atendida en una clínica — datos personales, contacto, responsable (menores), contacto de emergencia y anamnesis. Incluye el alta, la consulta, la edición, la baja y el **listado con búsqueda**.

### Modified Capabilities

- `multi-tenancy`: se **completa** el mecanismo de aislamiento que quedó a medias. Su spec ya exige que el aislamiento por organización proteja las tablas de negocio, pero **hasta ahora no había ninguna** y el contexto de organización **no se fijaba automáticamente** en las transacciones. Este change añade que el contexto SHALL establecerse **solo**, a partir del token, en toda transacción de una petición autenticada — de modo que una capacidad de negocio nueva quede aislada **sin escribir código de aislamiento**.

## Impact

- **Backend — `persistence`**: entidad `Patient` (con `@TenantId`), su repositorio y la migración **V9** (tabla + índice por organización + `FORCE ROW LEVEL SECURITY` + política).
- **Backend — `infra/tenancy`**: el **enganche automático** del contexto de tenant por transacción (el punto que `TenantSession` tenía reservado). Los flujos de auth conservan su fijación explícita, porque en el **registro** aún no hay token del que leer el contexto.
- **Backend — `service`/`presentation`**: servicio de pacientes + controller (CRUD y listado con filtros). Los errores transversales (`401`/`403`/`500`) **se inyectan solos**; solo se declara el status de éxito, el `400` y el `409` del documento duplicado.
- **Contrato**: `contracts/openapi.json` se regenera (el guardián `ContractDriftIT` **rompe la build** si no).
- **Frontend**: pantallas de listado y ficha; tipos y errores **derivados del contrato** (`docs/frontend.md §2.1`), con `errors[]` mapeado por campo.
- **Tests**: derivados de los Scenarios. Incluye el **test de aislamiento** contra PostgreSQL real (obligatorio, `docs/testing.md`) — el que demuestra que la promesa del SaaS se cumple.
- **Roles**: **ninguno específico**. Los tres roles (`ADMIN`, `DENTIST`, `RECEPTIONIST`) gestionan pacientes, así que basta con estar autenticado en la organización. `patients` es la única capacidad clínica que **no depende** del desdoble de roles que traerá `staff`.

## Non-goals

- **No se alerta de alergias ni antecedentes.** Aquí se **registran**; mostrarlos como aviso antes de un tratamiento es de `treatments`, que es quien tiene el contexto para hacerlo.
- **No se construye el odontograma ni la ficha dental completa.** La *ficha dental* es una vista compuesta que se llena a medida que aterrizan sus capacidades (`docs/domain.md`); aquí solo nace su cabecera.
- **No se da cuenta al paciente.** Sigue sin ser usuario del sistema (`docs/domain.md`): no inicia sesión y no se vincula a ningún `User`.
- **No se comparte el paciente entre clínicas** ni se implementa portabilidad del historial (`ADR-0004`).
- **No se desdoblan los roles** (`ADMIN`/`DENTIST`/`RECEPTIONIST`): eso lo trae `staff`, y `patients` no lo necesita.
- **No se usa el método HTTP `QUERY`** para los filtros: `GET` + query params (`ADR-0002`).
