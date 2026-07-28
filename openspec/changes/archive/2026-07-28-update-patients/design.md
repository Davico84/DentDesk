## Context

`patients` está construida y testeada. Las reglas de contacto y de documento hoy:

- **Teléfono opcional** (`optionalPhone` en `patientSchema`); se puede repetir el mismo número entre pacientes **sin aviso**.
- **Contacto de emergencia** (`emergencyContactName/Phone/Relationship`) existe pero es **opcional**, sin ninguna regla que lo ligue a la ausencia de teléfono propio.
- **Documento duplicado** en la clínica → el backend responde `409` (`patient-document-taken`); el front muestra el `detail` en un toast, pero **no** ofrece abrir la ficha que colisiona.

Los ADRs `0009`/`0010` piden cambiar estas tres cosas. (El endurecimiento de datos mínimos de `0005` queda como Open Question — ver proposal.)

## Goals / Non-Goals

**Goals:**
- Permitir teléfono compartido con **aviso no bloqueante**; exigir contacto de emergencia solo cuando falta el teléfono propio.
- Ante un documento duplicado, que el cliente pueda **llevar a la ficha existente**, no solo informar del error.

**Non-Goals:**
- No endurece los datos mínimos del alta (ADR-0005) — pendiente de decisión.
- No toca el gate clínico por etapa (ADR-0006, es de `treatment-plans`) ni consentimientos (ADR-0007, es de `treatments`).
- No cambia la unicidad del documento (sigue por clínica, `ADR-0004`/`0009` núcleo, ya construida).

## Decisions

### D1 — El aviso de teléfono compartido es no bloqueante y del lado servidor
Compartir número es **legítimo** (familias), así que **no** es un error de validación: es un **aviso**. Para poder avisar, el backend expone si el teléfono ya está en otros pacientes **de la clínica activa** (dato propio, respeta el aislamiento — no cruza clínicas). El front lo muestra como advertencia que **deja continuar**, visualmente distinta de un error de campo.
- **Dónde vive el aviso (plomería):** `PatientView`/`PatientResponse` gana un campo nuevo, `phoneSharedWithOtherPatients: boolean` (o `phoneSharedCount: int` si conviene mostrar cuántos — ver Open Question). Es un **cambio de contrato real**: requiere regenerar `contracts/openapi.json` y el cliente del frontend; `ContractDriftIT` lo exige.
- **La consulta excluye al propio paciente en edición** (mismo patrón que `existsByDocumentAndIdNot`): `PatientRepository` gana `countByPhoneAndIdNot(String phone, Long id)` para `update`, y `countByPhone(String phone)` para `create`. Sin la exclusión, editar la ficha de alguien que YA comparte teléfono con un hermano podría contarse mal (contar la propia fila como "otro").
- *Alternativa descartada — bloquear el duplicado:* convierte una situación normal en un error; contradice el ADR.
- *Alternativa descartada — chequeo solo en cliente:* el cliente no conoce los demás pacientes; el dato es del servidor.

### D2 — Contacto de emergencia obligatorio **solo si** no hay teléfono propio (regla condicional)
Misma forma que la regla del responsable para menores (`superRefine` en el schema, en **paridad** con el backend, `docs/coding-style.md §7`): si `phone` está vacío, entonces `emergencyContactName` + `emergencyContactPhone` + relación pasan a obligatorios. Si hay teléfono propio, siguen opcionales. La autoridad es el backend; el cliente replica la regla para avisar antes.
- *Consecuencia sobre ADR-0005:* esto define la "excepción al celular obligatorio" que `0005` menciona. Nota: hoy el teléfono es opcional a secas; esta regla lo vuelve "obligatorio **o** contacto de emergencia", que es un endurecimiento suave y **coherente** aunque no se resuelva aún el resto de `0005`.

### D3 — El conflicto de documento identifica la ficha existente
El `409` de documento duplicado (`patient-document-taken`) SHALL incluir lo necesario para que el front **enlace** a la ficha existente (su identificador de paciente), sin filtrar más datos de los necesarios. El front, ante ese `409`, ofrece "Abrir registro existente" además de "Cancelar".
- *Aislamiento:* la ficha existente es **de la misma clínica** (la unicidad es por clínica), así que exponer su `id` a un usuario de esa clínica es correcto — no cruza tenants.
- *Anti-enumeración:* no aplica como en auth; aquí el usuario ya está autenticado y autorizado sobre su propia clínica.
- *Alternativa descartada — solo el mensaje de texto:* obliga a la recepcionista a buscar la ficha a mano; el atajo es justo lo que el ADR pide.

### D4 — Cómo el `id` de la ficha llega al 409 (plomería, verificada contra el código)
`BusinessException` (la base de `DocumentAlreadyExistsException`) solo lleva `(message, status, errorType)` — **sin espacio para datos extra**, y `GlobalExceptionHandler.handleBusiness` la traduce con `ProblemDetails.of(ex.getStatus(), ex.getErrorType(), ex.getMessage())`, sin más. El único precedente de una extensión adicional en el proyecto (`errors[]` de validación) **no** pasa por `handleBusiness`: tiene su propio `@ExceptionHandler(MethodArgumentNotValidException)` que arma el `ProblemDetail` a mano y le hace `setProperty("errors", ...)`. D3 necesita el mismo patrón, no reinventar uno.

- **Decisión:** `DocumentAlreadyExistsException` gana un campo `existingPatientId` (constructor lo recibe); se agrega un `@ExceptionHandler(DocumentAlreadyExistsException.class)` **dedicado** en `GlobalExceptionHandler` (no pasa por `handleBusiness`) que construye el `ProblemDetail` vía `ProblemDetails.of(...)` y le añade `problem.setProperty("existingPatientId", ex.getExistingPatientId())`.
- *Por qué no generalizar `BusinessException`* con un mapa de extensiones genérico: mucho mayor radio de impacto (toca la base que usan `EmailAlreadyExistsException`, `InvalidCredentialsException`, etc., sin que ninguna otra lo necesite hoy). Un handler dedicado para este caso concreto es exactamente el patrón que el propio `handleMethodArgumentNotValid` ya demuestra que el proyecto usa cuando un error necesita algo más que `detail`.
- **Contrato:** `patient-document-taken` gana una extensión `existingPatientId` en su schema de error — documentar como opcional siguiendo el patrón de `traceId` (solo aparece en este `errorType` concreto).
- `PatientService.requireDocumentFree` necesita el `id` del paciente colisionado para pasarlo al constructor de la excepción — hoy solo hace `existsBy...` (boolean); pasa a `findBy...` (devuelve la entidad o su id) para poder incluirlo.

### D5 — Quién muestra el toast accionable, sin duplicar el aviso (verificado contra el código)
`useCreatePatient`/`useUpdatePatient` (fix `fix/patients-error-toast`) tienen `onError: (e) => toast.error(getUserFriendlyError(e))` **a nivel de hook** — corre **siempre**, sin acceso a `navigate` (los hooks de `usePatientsApi` no dependen del router). `PatientForm.onSubmit` tiene su propio `onError: (e) => applyServerFieldErrors(e, setError)` **a nivel de componente**, que sí tiene `navigate` en scope. Ambos `onError` corren **juntos** (mismo mecanismo que `useLogin`, confirmado esta sesión) — así que, sin más, el `409` de documento duplicado dispararía **dos** toasts: uno de solo texto (hook) y uno con botón "Abrir registro existente" (si se agregara en el componente).

- **Decisión:** el hook se **abstiene** de su toast genérico específicamente cuando el `errorType` es `patient-document-taken` — comprueba `getApiProblem(error)?.type === "/errors/patient-document-taken"` antes de llamar a `toast.error`. El toast accionable (con el botón que navega) lo muestra **el componente**, en su propio `onError`, leyendo `existingPatientId` del mismo error.
- **Nuevo util en `apiError.ts`**: `getExistingPatientId(error): number | undefined` → `getApiProblem(error)?.existingPatientId`, mismo patrón que `getFieldErrors`. Usa el tipo generado (una vez regenerado el contrato, D4) — nunca una forma escrita a mano (`docs/frontend.md §2.6`).
- *Alternativa descartada — el hook recibe `navigate` como parámetro:* rompe la simetría de `useDeactivatePatient`/`useReactivatePatient`, que no dependen del llamador para nada más que los datos de la mutación.
- *Alternativa descartada — mostrar ambos toasts:* dos avisos para el mismo error es peor UX que uno solo, y no hay ninguna razón de negocio para duplicarlo (a diferencia de field-errors + toast genérico, que sí son informaciones distintas — aquí ambos toasts dirían básicamente lo mismo).
- *Consecuencia:* el hook dejar de ser 100% agnóstico al tipo de error para ESTE `errorType` puntual — es una excepción documentada, no el patrón general. Cualquier otro error de `patients` (500, otros 409) sigue cayendo en el toast genérico del hook, sin cambios.

## Risks / Trade-offs

- **El aviso de teléfono compartido añade una consulta** en el alta/edición → aceptable (indexado por `(organizationId, phone)` si hiciera falta); es un `count`, no una carga.
- **Regla condicional de emergencia** puede confundir si la UI no explica *por qué* el campo se volvió obligatorio → mitigación: mensaje claro ("Obligatorio si el paciente no tiene teléfono propio"), como el de menores.
- **Exponer el `id` de la ficha en conflicto**: se limita a `id` (y quizá nombre para confirmar), nunca la ficha entera en el error. Ver D4 sobre cómo se transporta.
- **Cambio de contrato en dos DTOs** (`PatientResponse` gana el aviso de teléfono, D1; el error `patient-document-taken` gana `existingPatientId`, D4) → ambos exigen regenerar `contracts/openapi.json` + cliente del frontend; `ContractDriftIT` falla si se olvida. Riesgo bajo (mecánico), solo hay que acordarse de correrlo.
- **Contar sin excluir al propio paciente en edición** (D1) → mitigado: la consulta de teléfono usa `countByPhoneAndIdNot` en `update`, mismo patrón que `existsByDocumentAndIdNot` ya usa para el documento.
- **Doble toast para el documento duplicado** (D5) → mitigado: el hook se abstiene de su toast genérico para ese `errorType` específico; el componente muestra el único toast, con acción.

## Open Questions

- Las de ADR-0005 (campos obligatorios del alta, excepción del bebé, `fullName` vs nombres+apellidos) — ver proposal. Se resuelven antes de añadir su delta; no bloquean D1–D5.
- ¿El aviso de teléfono compartido lista **cuántos**/**quiénes** lo comparten, o solo "ya está en uso por otros pacientes"? Propuesta: solo el hecho + conteo, sin nombres, para no volcar datos innecesarios en un aviso. Determina si `PatientResponse` gana un `boolean` o un `int` (ver D1).
