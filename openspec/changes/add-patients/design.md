## Context

`patients` es la ficha de la persona atendida: el **núcleo del dominio**, del que cuelgan `dental-chart`, `treatments`, `appointments`, `images` y `patient-billing`.

Es también la **primera capacidad de negocio** del producto, así que es la primera que se aísla por organización de verdad. Pero **el mecanismo de aislamiento no se construye aquí**: el enganche automático del contexto de tenant es un cimiento transversal que vive en su propio change (**`update-multi-tenancy`**), porque lo necesitan *todas* las capacidades de negocio.

> **Consecuencia práctica, y es la clave del diseño:** `patients` **no escribe ni una línea de código de aislamiento**. Declara su entidad siguiendo la convención y el aislamiento **ya está**. Si este diseño necesitara "acordarse" de fijar el contexto en algún sitio, sería la señal de que el cimiento está mal — **por defecto, no por disciplina**.

Restricción heredada (`docs/backend.md §8.1`, **no negociable**): columna `organization_id` + `@TenantId`, **índice** sobre ella, `ENABLE`/`FORCE ROW LEVEL SECURITY` con política `organization_isolation` **sin `missing_ok`** (fallo ruidoso), acceso **siempre transaccional**, y el tenant **solo desde el token firmado** — jamás de la petición.

## Goals / Non-Goals

**Goals:**

- `Patient` cumple la convención de aislamiento **completa**, verificada contra PostgreSQL real: una clínica **nunca** ve ni modifica pacientes de otra. Es la **primera prueba real** de la garantía que trae `update-multi-tenancy`.
- La unicidad del documento es **por clínica** (`ADR-0004`).
- El **menor de edad** exige responsable, y la minoría **se deriva** de la fecha de nacimiento.
- El **listado con búsqueda** sirve el caso de uso diario de recepción.

**Non-Goals:**

- **Construir el enganche del contexto de tenant**: es de `update-multi-tenancy`. Aquí se **consume**, no se implementa.
- Alertar de alergias/antecedentes (eso es de `treatments`; aquí se **registran**).
- Odontograma, tratamientos, citas: cuelgan de aquí, pero no se construyen.
- Roles específicos: los tres gestionan pacientes; basta estar autenticado en la organización.
- Portabilidad del historial entre clínicas (`ADR-0004`).

## Decisions

### D1 — La unicidad del documento es **por clínica**, y el documento es **opcional**

**Decisión:** restricción única sobre **`(organization_id, document)`**, **nunca** sobre `document` a secas — eso impediría que dos clínicas atendieran a la misma persona, rompiendo `ADR-0004`. El intento de duplicar dentro de la misma clínica responde **`409`**, con `type` que lo identifica.

**El documento es opcional** (`NULL` permitido): un bebé o un menor pueden no tener DNI todavía, y **la clínica no puede negarse a atenderlo por eso**. PostgreSQL permite múltiples `NULL` en un índice único, así que la restricción sigue siendo correcta.

### D2 — La minoría de edad **se deriva**, y arrastra al responsable

**Decisión:** `isMinor` **NO es un campo**: se calcula de `birthDate` (< 18 años). Un campo declarable se desincroniza el día del cumpleaños — un dato derivado, nunca.

**Si el paciente es menor, los datos del responsable son obligatorios** (nombre, parentesco, teléfono). Es una **validación condicional**: no se expresa con un `@NotNull` por campo, hace falta validación **a nivel de objeto** (en servidor) y su equivalente en el cliente.

> La paridad de validación cliente/servidor (`docs/coding-style.md §7`) es aquí más delicada que de costumbre, porque la regla es condicional. El servidor es la autoridad: si el cliente se despista, su `400` trae el **detalle por campo** y se pinta junto al input (ya lo tenemos resuelto).

### D3 — La anamnesis se guarda como **listas**, no como texto libre

`allergies`, `conditions` (antecedentes) y `currentMedication` se almacenan como **listas de valores**, no como un párrafo.

**La razón es clínica, no técnica:** `treatments` tendrá que **recorrerlas para alertar** antes de un procedimiento (*"este paciente es alérgico a la penicilina"*). **Un blob de texto libre no se puede alertar de forma fiable** — y aquí un falso negativo tiene consecuencias reales sobre una persona.

- *Alternativa — un catálogo codificado de alergias/patologías.* Es lo correcto a largo plazo, pero exige un vocabulario clínico que hoy no existe. **Fuera de alcance**; las listas de texto son el punto intermedio honesto: estructuradas lo justo para poder alertar.

### D4 — Dar de baja a un paciente **NO lo borra**

**Decisión:** la baja es **lógica** (`status`: activo / inactivo). El listado muestra los activos por defecto.

**Razón:** la historia clínica tiene **retención legal** y, además, el paciente cuelga de tratamientos, citas e facturas. Un borrado físico o es imposible (integridad referencial) o destruye información que **no nos pertenece destruir**. "Dar de baja" en una clínica significa **archivar**, no eliminar.

### D5 — El listado es `GET` + query params (`ADR-0002`)

Búsqueda por nombre o documento, con paginación. **No** se usa el método HTTP `QUERY` (es un borrador; el contrato no podría describirlo y orval no generaría cliente — rompería la cadena "el contrato es la fuente única de verdad").

Los errores transversales (`401`/`403`/`500`) **se inyectan solos** en el contrato (`docs/backend.md §10.2`): solo hay que declarar el status de éxito, el `400` de validación y el `409` del documento duplicado.

### D6 — El servicio NO habla DTOs de la API — y ahora **la build lo exige**

**Decisión:** `PatientService` recibe y devuelve **tipos propios de la capa de servicio** (un `Patient` de dominio / un command), **nunca** `PatientResponse` ni `CreatePatientRequest` de `presentation`. La traducción a/desde los DTOs vive en `PatientController`, que es quien conoce el contrato HTTP.

**Por qué se decide aquí explícitamente y no se da por hecho:** `organization-branding` cometió justo el error contrario —el servicio devolvía `BrandingResponse` y el DTO leía la entidad JPA— y **pasó desapercibido durante meses porque `LayeredArchitectureTest` estaba ciego** (ArchUnit no leía el bytecode de Java 25). Arreglado eso, la regla **rompe la build**. Si `patients` copia el patrón malo, no compila. Referencia del patrón correcto ya en el repo: `Branding` (record de servicio) + `BrandingController` (traduce).

> Es el mismo principio que el aislamiento automático: lo que protege no es la disciplina, es que **la build falla**. Escribir el servicio atado al transporte ya no es "código mejorable" — es código que no entra.

## Risks / Trade-offs

- **[Un olvido de aislamiento no falla: filtra historia clínica entre clínicas]** ⭐ → Por eso el mecanismo es **automático** (`TenantAwareTransactionManager`, de `update-multi-tenancy`) y hay un **test de aislamiento obligatorio** contra PostgreSQL real. **Si al implementar `patients` hiciera falta fijar el contexto a mano, el cimiento estaría mal** — se arregla allí, no aquí. De hecho no se puede: una regla de ArchUnit **rompe la build** si `PatientService` importa `TenantSession`.

  > 🚨 **Hay DOS tests de aislamiento y corren sobre bases distintas — no confundirlos:**
  >
  > 1. **A nivel de motor (RLS), el obligatorio** (`docs/testing.md`): entra por el **repositorio de `Patient`** con la app conectada como **rol no-superusuario** — extiende **`AbstractTenantAwareIntegrationTest`**, no `AbstractIntegrationTest`. Si conecta como el superusuario del contenedor, **RLS ni se aplica** y el test pasa en verde con el aislamiento roto. Sigue el patrón de **`AutomaticTenantBindingIT`**: puebla `OrganizationContext` como el filtro y **nunca llama a `bind()`**.
  > 2. **A nivel HTTP (el 404 por identificador)**: lo garantiza el filtro de aplicación (`@TenantId`), que funciona aunque el rol sea superusuario. Puede correr sobre la base normal. Prueba que la clínica A recibe **404** —no 403— al pedir un paciente de B por su id, sin revelar que existe.
  >
  > El primero prueba que **el motor** aísla; el segundo, que **la API** no filtra la existencia. Hacen falta los dos.
- **[La validación condicional del menor se desincroniza entre cliente y servidor]** (D2) → El servidor es la autoridad y su `400` trae detalle por campo. Los Scenarios cubren **ambos** lados.
- **[Las listas de anamnesis son texto sin vocabulario]** (D3) → Aceptado: `treatments` podrá alertar mostrándolas; codificarlas es una evolución, no un requisito de hoy.
- **[El índice por organización se olvida]** → `docs/backend.md §8.1` lo exige explícitamente ("el índice **es parte de la política de aislamiento**, no una optimización posterior"): sin él, RLS filtra con *seq scan* y el listado se degrada con cada clínica nueva.

## Migration Plan

0. **Prerrequisito: `update-multi-tenancy` en verde** (el contexto se fija solo).
1. Migración **V9**: tabla `patients` con `organization_id`, **índice**, `FORCE ROW LEVEL SECURITY` y política `organization_isolation`; unique `(organization_id, document)`.
2. Entidad (`@TenantId`), repositorio, servicio y controller — **sin código de aislamiento**.
3. Regenerar contrato + cliente (el guardián `ContractDriftIT` rompe la build si no).
4. Frontend: listado con búsqueda + ficha.

**Rollback:** revertir el commit; la V9 se deshace con una migración de reversa (no hay datos en producción).

## Open Questions

1. **¿Qué campos son obligatorios en el alta?** Propuesta: `fullName` y `birthDate` (la fecha es imprescindible: de ella se derivan la minoría de edad y la dentición del odontograma). El **documento es opcional** (D1). El resto, opcional. Confirmar al implementar el formulario.
