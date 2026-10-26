# Tasks

> Rama `apply-staff-professional-credential` desde `dev`, PR a `dev`. Commits separados por scope
> (`docs/commits.md`): backend · contrato + cliente generado · frontend · docs. Antes de tocar el frontend, skill
> `frontend-guard`. Tests derivados de los Scenario de los deltas `staff`, `clinical-history` y `dental-chart`.
> Los IT de RLS y de concurrencia corren con Testcontainers (Docker levantado); concurrencia con pool propio,
> nunca `supplyAsync` sin executor.

## 1. Base de datos

- [ ] 1.1 `V30`: tabla `professional_credential` (design D1) con `cop_number` único, `licensed_until DATE`, `sworn_at`, RLS forzada (`SELECT` propia o de un miembro si quien lee es `ADMIN` activo de la organización activa, vía una función `SECURITY DEFINER` con `search_path=public`; `INSERT`/`UPDATE` con `WITH CHECK (user_id = app.user_id)`; sin `DELETE`), permisos para `odontorisas_app` como en V6. Verificar: `mvn verify` aplica la migración desde cero
- [ ] 1.2 En `V30`: `memberships.clinical_capacity_revoked_at` y `clinical_capacity_revoked_by` (design D2); `historia_clinica.completed_by`, `dental_chart_block.sealed_by`, y en las tres tablas `signer_full_name`, `signer_cop_number`, `signer_licensed_until`, nulos; `CHECK ... NOT VALID` de la foto en las tres (design D5). Sin `UPDATE` de datos. Verificar: una base con filas selladas previas migra sin error y esas filas no cambian
- [ ] 1.3 IT de RLS de la credencial: un usuario no inserta ni modifica la credencial de otro aun como `ADMIN` *(Intentar declarar la credencial de otro usuario)*; el `ADMIN` lee la de un miembro de su clínica y no la de alguien de otra *(Credencial de alguien de otra clínica)*; un COP duplicado choca aunque la otra fila sea invisible
- [ ] 1.4 IT de los `CHECK NOT VALID`: sellar por SQL directo una Historia, un odontograma o una nota sin foto falla; una fila sellada previa a la restricción sigue legible *(Un sellado nuevo sin la foto es imposible · Lo sellado antes del cambio no se reescribe)*

## 2. Backend: credencial y capacidad clínica

- [ ] 2.1 Entidad y repositorio de `ProfessionalCredential`; `ClinicalCapacityService` (design D3) con `ENABLED`/`NO_CREDENTIAL`/`EXPIRED`/`REVOKED`, vigencia con el `Clock` inyectado y la zona horaria de la clínica, último día incluido, y `require()` que devuelve la foto o lanza `ClinicalCapacityRequiredException` (403 RFC 9457 con `reason`). Verificar con tests unitarios: vence al día siguiente, el último día es válido, retiro en una clínica no afecta a otra
- [ ] 2.2 `GET` y `PUT /me/professional-credential` (design D6): el usuario sale de la sesión; Bean Validation (COP solo dígitos 1–10, fecha no anterior a hoy en la zona de la clínica, `sworn` verdadero); COP duplicado → 409 genérico sin datos de la otra fila; sesión de plataforma → 401
- [ ] 2.3 IT de la credencial *(spec `staff` → Declarar la credencial profesional propia · Capacidad clínica efectiva en la clínica activa)*: primera declaración, renovar tras vencer, sin declaración jurada, COP con letras, fecha pasada, COP de otro usuario (409 sin revelar quién), sesión de plataforma; consulta de capacidad con cada resultado y retirada en A pero vigente en B

## 3. Backend: retiro y listado del personal

- [ ] 3.1 `PATCH /staff/members/{id}/clinical-capacity` con `{ revoked }` (design D2): solo `ADMIN`; sobre un `MEMBER` de su clínica; propio u otro `ADMIN` → 403; no miembro → 404; responde `204`. Restituir solo levanta el retiro
- [ ] 3.2 `MemberResponse.clinicalCapacity` (`state`, `copNumber`, `licensedUntil`) con el mismo cálculo de 2.1, sin exponer credenciales de no miembros
- [ ] 3.3 IT *(spec `staff` → Retirar y restituir · Capacidad clínica en el listado del personal)*: retirar, restituir, restituir a quien venció o no declaró (sigue sin capacidad), otro `ADMIN` o sí mismo 403, `MEMBER` 403, no miembro 404; el listado con cada estado y la fecha de vencimiento

## 4. Backend: firmar con credencial

- [ ] 4.1 `CloseAdmissionService`: `require()` dentro de la transacción y antes de `complete`/`sealInitial`; la misma foto se guarda en `historia_clinica` (`completed_by` + `signer_*`) y en `dental_chart_block` (`sealed_by` + `signer_*`) (design D4)
- [ ] 4.2 `ConsultationNoteService.seal`: `require()` después de bloquear la nota y antes de validar su contenido; foto en `consultation_note` junto a `sealed_by`
- [ ] 4.3 Respuestas de la Historia, el odontograma y la nota con `signer` y `signedBeforeCredentials` derivado (sellado y sin foto) (design D6)
- [ ] 4.4 IT *(spec `clinical-history` → Firmar exige capacidad clínica · Credencial del firmante fotografiada al sellar · Firmas anteriores al registro de credenciales; spec `dental-chart` → Credencial del firmante en el odontograma sellado)*: cerrar la admisión con capacidad (Historia y odontograma con la misma foto); sin credencial, vencida y retirada → 403 con su `reason` y nada sellado; borradores sin capacidad se siguen guardando; renovar o corregir el nombre no cambia lo firmado; vencer o retirar después no invalida lo firmado; fila sellada previa → `signedBeforeCredentials` verdadero
- [ ] 4.5 Los IT existentes de cierre de admisión y de sellado de nota declaran la credencial en su preparación; `mvn verify` en verde

## 5. Contrato

- [ ] 5.1 OpenAPI de los endpoints nuevos y de los campos nuevos (200, 204, 400, 401, 403 con `reason`, 404, 409), `operationId` únicos; regenerar `contracts/openapi.json` **antes** de `mvn verify` (`ContractDriftIT`) y el cliente generado del front

## 6. Frontend: credencial propia

- [ ] 6.1 Módulo y hook de React Query para `GET`/`PUT /me/professional-credential`; schema Zod con paridad (COP solo dígitos 1–10, fecha no anterior a hoy, declaración jurada obligatoria) (`docs/coding-style.md` §7)
- [ ] 6.2 Pantalla "Mi credencial profesional" accesible a toda membresía (design D7): estado en la clínica activa con su motivo, formulario con la declaración jurada, aviso de que la habilitación es autodeclarada; 409 y 400 del servidor mostrados en el campo. Verificar con Vitest/MSW: declarar, renovar, errores de COP y de fecha, sin declaración jurada, conflicto por COP

## 7. Frontend: personal

- [ ] 7.1 Columna de capacidad clínica en Personal (sin credencial, habilitado hasta, vencida, retirada, con COP) y acción retirar/restituir con confirmación, no ofrecida sobre la propia fila ni sobre otro `ADMIN`. Verificar con Vitest/MSW: cada estado, retirar, cancelar, restituir

## 8. Frontend: firmar

- [ ] 8.1 Botones de cerrar la admisión y de sellar la nota deshabilitados sin capacidad clínica, con el motivo y el enlace a la pantalla de credencial; el 403 del servidor muestra el mismo texto *(La interfaz explica por qué no se puede firmar)*
- [ ] 8.2 Historia `COMPLETED`, odontograma sellado y nota sellada muestran "Firmado por <nombre> · COP <número>" o "Firmado antes del registro de credenciales". Verificar con Vitest/MSW, incluidos los tests existentes de `SealedNoteCard` y del cierre de la admisión
- [ ] 8.3 `pnpm validate` en verde

## 9. Integración

- [ ] 9.1 Prueba manual con backend y frontend locales: sin credencial no se firma y el botón explica por qué; declarar, cerrar una admisión y sellar una nota, y ver el firmante; como `ADMIN`, retirar la capacidad de un `MEMBER` y comprobar que no puede sellar; una nota sellada antes del cambio se ve como "firmada antes del registro de credenciales"
- [ ] 9.2 `openspec validate update-staff-professional-credential --strict` y medir a mano los requirements (≤ 500 caracteres)

## Workflow follow-up

- Al archivar: `docs/domain.md` (credencial profesional en el ER y en la tabla de actores; quitar el aviso de
  "la credencial clínica no está construida"), `docs/vision.md` (fila de `staff` y su "Pendiente"), la fila
  *Autoría por profesional habilitado* de `docs/compliance/regulatory-matrix.md`, nota en `ADR-0016` D7
  (asimetría otorgar/retirar y dos niveles credencial/membresía) y el Purpose de `openspec/specs/staff/spec.md`
  (suma la credencial que declara el propio profesional).
- Antes de archivar: confirmar con una constancia real si la habilitación del COP trae una fecha de vigencia
  (Open Question del design) y ajustar el texto de ayuda.
- Al desplegar: cada profesional declara su credencial antes de volver a firmar (notas del release).
