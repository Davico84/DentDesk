## 1. Decisión previa (bloquea el delta de ADR-0005, no el resto)

- [ ] 1.1 Revisión humana: resolver las Open Questions de ADR-0005 (qué campos pasan a obligatorios, si sobrevive la excepción del bebé sin documento, si `fullName` se parte en nombres+apellidos). Solo entonces se añade su delta de spec. **No bloquea los grupos 2–5.**

## 2. Contacto telefónico (backend) — ADR-0010, D1/D2

- [x] 2.1 `PatientRepository`: `countByPhone(String phone)` (alta) y `countByPhoneAndIdNot(String phone, Long id)` (edición, excluye al propio paciente — mismo patrón que `existsByDocumentAndIdNot`). Acotado por `organizationId` automáticamente (RLS + `@TenantId`).
- [x] 2.2 Validación condicional en el DTO/servicio: si no hay teléfono propio, el contacto de emergencia (nombre + teléfono + relación) es obligatorio; si lo hay, opcional. (D2)
- [x] 2.3 `PatientView`/`PatientResponse`: nuevo campo `phoneSharedWithOtherPatients` (boolean, resuelta la Open Question de design) con el resultado de 2.1. Regenerado `contracts/openapi.json` + cliente del frontend (`ContractDriftIT` en verde). (D1)

## 3. Contacto telefónico (frontend) — ADR-0010, D1/D2

- [x] 3.1 Regla condicional en `patientSchema` (Zod `superRefine`): contacto de emergencia obligatorio si `phone` vacío; mensaje claro del porqué. Paridad con el backend.
- [x] 3.2 Aviso **no bloqueante** de teléfono compartido: `toast.warning` al guardar (hook) + banner persistente en la ficha (`PatientDetail`), visualmente distinto de un error de campo (deja guardar).

## 4. Ficha existente ante documento duplicado — ADR-0009, D3/D4/D5

- [x] 4.1 `DocumentAlreadyExistsException`: añadido el campo `existingPatientId` (constructor). `PatientService.requireDocumentFree`: cambiado `existsBy...` por `findBy...` para obtener el id del paciente colisionado y pasarlo al construir la excepción.
- [x] 4.2 `GlobalExceptionHandler`: `@ExceptionHandler(DocumentAlreadyExistsException.class)` **dedicado** (no pasa por `handleBusiness`) que arma el `ProblemDetail` vía `ProblemDetails.of(...)` y le agrega `problem.setProperty("existingPatientId", ex.getExistingPatientId())` — mismo patrón que `handleMethodArgumentNotValid` con `errors`. (D4)
- [x] 4.3 Contrato: `patient-document-taken` documenta la extensión `existingPatientId` (opcional, solo en este `errorType`, en `ApiProblem`). Regenerado `contracts/openapi.json` + cliente.
- [x] 4.4 `apiError.ts`: nuevo util `getExistingPatientId(error)` → `getApiProblem(error)?.existingPatientId`, mismo patrón que `getFieldErrors`. (D5)
- [x] 4.5 `usePatientsApi.ts`: en `useCreatePatient`/`useUpdatePatient`, el `onError` del hook **se abstiene** de su `toast.error` genérico cuando `getApiProblem(error)?.type === "/errors/patient-document-taken"` — ese caso lo maneja el componente, para no duplicar el aviso. (D5)
- [x] 4.6 `PatientForm.onSubmit`: en su `onError`, si el error es `patient-document-taken`, muestra el **único** toast con acción "Abrir ficha existente" (`navigate(patientDetailPath(existingPatientId))`). Para el resto de errores, sigue solo `applyServerFieldErrors` (el hook cubre el genérico). (D5)

## 5. Tests (uno por Scenario del delta)

- [x] 5.1 Dos pacientes comparten teléfono → alta completa + aviso no bloqueante.
- [x] 5.2 Sin teléfono propio y sin contacto de emergencia → rechazo indicando que se requiere.
- [x] 5.3 Sin teléfono propio pero con contacto de emergencia → alta completa.
- [x] 5.4 Con teléfono propio y sin contacto de emergencia → alta completa.
- [x] 5.5 Teléfono repetido en **otra** clínica → sin aviso, sin fuga entre clínicas.
- [x] 5.6 Conflicto de documento identifica la ficha existente en la respuesta.
- [x] 5.7 Abrir la ficha existente desde el aviso de conflicto navega a ella.
- [x] 5.8 Documento en otra clínica no genera conflicto ni revela nada de la otra.
- [x] 5.9 **Edición**: editar un paciente que ya comparte teléfono con otro (sin cambiar el teléfono) no muestra un falso aviso por contarse a sí mismo — `countByPhoneAndIdNot` excluye al propio id.
- [x] 5.10 **Edición**: quitar el teléfono propio de un paciente sin contacto de emergencia existente exige agregarlo, igual que en el alta.
- [x] 5.11 El conflicto de documento muestra **un solo** toast (con la acción), no el genérico del hook además del accionable del componente. (D5)

## 6. Cierre (al archivar)

- [ ] 6.1 `docs/vision.md`: enlazar el change bajo `patients` en "Estado de capacidades".
- [ ] 6.2 `docs/domain.md`: registrar la regla de contacto de emergencia (obligatorio si no hay teléfono propio) y el teléfono compartido.
- [ ] 6.3 Marcar en los ADR `0009`/`0010` la parte pendiente como implementada (o enlazar el change archivado).
