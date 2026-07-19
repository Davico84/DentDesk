## Why

`patients` es **el núcleo del dominio**: de la ficha del paciente cuelgan `dental-chart`, `treatments`, `appointments`, `images` y `patient-billing`. Sin ella no hay producto.

Y es la **primera capacidad de negocio con una entidad bajo RLS por organización** —la primera que se aísla de verdad; `organization-branding`, la otra capacidad de negocio ya construida, es **metadato del tenant y no lleva RLS** (`ADR-0003`)—, y la que estrena las convenciones que dejamos preparadas: el contrato que **inyecta los errores transversales solos** y los tipos de error **derivados** en el frontend.

> **Depende de `update-multi-tenancy`.** El enganche automático del contexto de tenant —lo que hace que una entidad de negocio quede aislada **sin escribir código de aislamiento**— es un cimiento **transversal** que necesitan *todas* las capacidades de negocio, no solo esta. Por eso vive en su propio change, sobre `multi-tenancy`, y no aquí: que le tocara a `patients` destaparlo es un **accidente de calendario** (le tocó ser la primera), no un diseño.
>
> Consecuencia práctica: `patients` **no escribe ni una línea de código de aislamiento**. Declara su entidad siguiendo la convención y el aislamiento **ya está**. Esta capacidad es la **primera prueba real** de esa garantía.

## What Changes

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

*(ninguna)* — el cierre del aislamiento (`multi-tenancy`) va en su **propio change**: `update-multi-tenancy`. Ver la nota de dependencia arriba.

## Impact

- **Backend — `persistence`**: entidad `Patient` (con `@TenantId`), su repositorio y la migración **V9** (tabla + índice por organización + `FORCE ROW LEVEL SECURITY` + política).
- **Backend — `service`/`presentation`**: servicio de pacientes + controller (CRUD y listado con filtros). Los errores transversales (`401`/`403`/`500`) **se inyectan solos**; solo se declara el status de éxito, el `400` y el `409` del documento duplicado.
- **Contrato**: `contracts/openapi.json` se regenera (el guardián `ContractDriftIT` **rompe la build** si no).
- **Frontend**: pantallas de listado y ficha; tipos y errores **derivados del contrato** (`docs/frontend.md §2.1`), con `errors[]` mapeado por campo.
- **Tests**: derivados de los Scenarios. Incluye el **test de aislamiento** contra PostgreSQL real (obligatorio, `docs/testing.md`) — aquí es donde la garantía de `update-multi-tenancy` se demuestra **sobre una entidad real**.
- **Roles**: **ninguno específico**. Los tres roles (`ADMIN`, `DENTIST`, `RECEPTIONIST`) gestionan pacientes, así que basta con estar autenticado en la organización. `patients` es la única capacidad clínica que **no depende** del desdoble de roles que traerá `staff`.

## Non-goals

- **No se alerta de alergias ni antecedentes.** Aquí se **registran**; mostrarlos como aviso antes de un tratamiento es de `treatments`, que es quien tiene el contexto para hacerlo.
- **No se construye el odontograma ni la ficha dental completa.** La *ficha dental* es una vista compuesta que se llena a medida que aterrizan sus capacidades (`docs/domain.md`); aquí solo nace su cabecera.
- **No se da cuenta al paciente.** Sigue sin ser usuario del sistema (`docs/domain.md`): no inicia sesión y no se vincula a ningún `User`.
- **No se comparte el paciente entre clínicas** ni se implementa portabilidad del historial (`ADR-0004`).
- **No se desdoblan los roles** (`ADMIN`/`DENTIST`/`RECEPTIONIST`): eso lo trae `staff`, y `patients` no lo necesita.
- **No se usa el método HTTP `QUERY`** para los filtros: `GET` + query params (`ADR-0002`).
- **No se construye el enganche del contexto de tenant**: es de `update-multi-tenancy`. `patients` lo **consume**, no lo implementa.
