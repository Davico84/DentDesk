# Tasks

> Rama `apply-staff-professional-credential` desde `dev`, PR a `dev`. Commits separados por scope
> (`docs/commits.md`): backend · contrato + cliente generado · frontend · docs. Antes de tocar el frontend, skill
> `frontend-guard`. Tests derivados de los Scenario de los deltas `staff`, `clinical-history` y `dental-chart`.
> Los IT de RLS y de concurrencia corren con Testcontainers (Docker levantado); concurrencia con pool propio,
> nunca `supplyAsync` sin executor.
> Revisión de Codex (2026-10-26) incorporada: `USING` en el `UPDATE` de la credencial, `FOR SHARE` contra retiro y
> renovación concurrentes, vigencia en `America/Lima`, inmutabilidad de las Historias viejas, 409 del COP sin causa
> SQL, IT de concurrencia y MODIFIED de los dos sellados de `clinical-history`. Agregado propio: trigger de guarda
> del retiro en `memberships` (la policy de V28 deja que un usuario actualice su propia membresía).

## 1. Base de datos

- [x] 1.1 `V30`: tabla `professional_credential` (design D1) con `cop_number` único, `licensed_until DATE`, `sworn_at`, índice único con nombre propio, RLS forzada con una policy por comando (`SELECT` propia o de un miembro si quien lee es `ADMIN` activo de la organización activa, vía una función con `search_path=public` (*apply: `SECURITY INVOKER`, ver design D1*); `INSERT` con `WITH CHECK (user_id = app.user_id)`; `UPDATE` con `USING` **y** `WITH CHECK (user_id = app.user_id)`; sin `DELETE`), permisos para `odontorisas_app` como en V6. Verificar: `mvn verify` aplica la migración desde cero
- [x] 1.2 En `V30`: `memberships.clinical_capacity_revoked_at` y `clinical_capacity_revoked_by` con un `CHECK` de coherencia y un trigger `BEFORE INSERT OR UPDATE` que corre último: `INSERT` sin retiro, y si el retiro cambia, valida el estado final (`ADMIN` activo de la organización, fila ajena y `NEW.role = 'MEMBER'`) (design D2); `historia_clinica.completed_by`, `dental_chart_block.sealed_by`, y en las tres tablas `signer_full_name`, `signer_cop_number`, `signer_licensed_until`, nulos; `CHECK ... NOT VALID` de la foto en las tres (design D5). Sin `UPDATE` de datos. Verificar: una base con filas selladas previas migra sin error y esas filas no cambian
- [x] 1.3 IT de RLS de la credencial: un usuario no inserta ni modifica la credencial de otro aun como `ADMIN`, incluido un `UPDATE` por SQL que intenta cambiar el `user_id` de una fila ajena al propio *(Intentar declarar la credencial de otro usuario)*; un miembro no levanta su propio retiro por SQL directo con su sesión *(Un miembro intenta levantar su propio retiro)*; un `INSERT` de membresía retirada falla *(Una membresía no nace retirada)*; un `UPDATE` que cambia rol y retiro a la vez sin ser `ADMIN` falla, y uno de `status` que no toca el retiro sigue funcionando; el `ADMIN` lee la de un miembro de su clínica y no la de alguien de otra *(Credencial de alguien de otra clínica)*; un COP duplicado choca aunque la otra fila sea invisible
- [x] 1.4 IT de los `CHECK NOT VALID`: sellar por SQL directo una Historia, un odontograma o una nota sin foto falla; una fila sellada previa a la restricción sigue legible; un `UPDATE` sobre una Historia `COMPLETED` previa falla por el `CHECK` *(Un sellado nuevo sin la foto es imposible · Lo sellado antes del cambio no se reescribe · Una Historia sellada antes del cambio no se modifica ni en la base)*

## 2. Backend: credencial y capacidad clínica

- [x] 2.1 Entidad y repositorio de `ProfessionalCredential`; `ClinicalCapacityService` (design D3) con `INACTIVE_MEMBERSHIP`/`REVOKED`/`NO_CREDENTIAL`/`EXPIRED`/`ENABLED` en ese orden, vigencia con el `Clock` inyectado en `America/Lima`, último día incluido (design D3), y `require()` que lee membresía y credencial con `FOR SHARE` (design D4) y devuelve la foto o lanza `ClinicalCapacityRequiredException` (403 RFC 9457 con `reason`). Verificar con tests unitarios: vence al día siguiente, el último día es válido, mismo instante UTC cerca de medianoche con clínicas en dos zonas horarias da el mismo resultado, retiro en una clínica no afecta a otra
- [x] 2.2 `GET` y `PUT /me/professional-credential` (design D6): el usuario sale de la sesión; Bean Validation (COP solo dígitos 1–10, fecha no anterior a hoy en `America/Lima`, `sworn` verdadero); primera declaración con `INSERT` y renovación con `UPDATE` por el `user_id` de la sesión, sin upsert (design D1); COP duplicado: el servicio captura la restricción por su nombre y lanza `credential-number-taken` (409, texto fijo, sin la causa SQL); sin clínica activa, credencial sí y `clinicalCapacity: null`; sesión de plataforma → 401
- [x] 2.3 IT de la credencial *(spec `staff` → Declarar la credencial profesional propia · Capacidad clínica efectiva en la clínica activa)*: primera declaración, renovar tras vencer, sin declaración jurada, COP con letras, fecha pasada, COP de otro usuario al declarar y al renovar cambiando el número (409 sin revelar quién; con captura de logs, ni la respuesta ni los logs contienen el número), sesión de plataforma, sin clínica activa; consulta de capacidad con cada resultado, incluido el miembro desactivado con su sesión vigente; la misma credencial al cambiar de A a B; retirada en A pero vigente en B; dos clínicas con zonas horarias distintas en el mismo instante

## 3. Backend: retiro y listado del personal

- [x] 3.1 `PATCH /staff/members/{id}/clinical-capacity` con `{ revoked }` (design D2): solo `ADMIN`; sobre un `MEMBER` de su clínica; propio u otro `ADMIN` → 403; no miembro → 404; responde `204`. Restituir solo levanta el retiro
- [x] 3.2 `MemberResponse.clinicalCapacity` (`state`, `copNumber`, `licensedUntil`) con el mismo cálculo de 2.1, sin exponer credenciales de no miembros
- [x] 3.3 IT *(spec `staff` → Retirar y restituir · Capacidad clínica en el listado del personal)*: retirar, restituir, restituir a quien venció o no declaró (sigue sin capacidad), otro `ADMIN` o sí mismo 403, `MEMBER` 403, no miembro 404; el listado con cada estado y la fecha de vencimiento

## 4. Backend: firmar con credencial

- [x] 4.1 `CloseAdmissionService`: `require()` como primera operación de la transacción, antes de `complete`/`sealInitial` (orden membresía → credencial → Historia → odontograma, design D4); la misma foto se guarda en `historia_clinica` (`completed_by` + `signer_*`) y en `dental_chart_block` (`sealed_by` + `signer_*`) (design D4)
- [x] 4.2 `ConsultationNoteService.seal`: `require()` como primera operación de la transacción, antes de bloquear la nota (orden membresía → credencial → nota, design D4); foto en `consultation_note` junto a `sealed_by`
- [x] 4.3 Respuestas de la Historia, el odontograma y la nota con `signer` y `signedBeforeCredentials` derivado (sellado y sin foto) (design D6)
- [x] 4.4 IT *(spec `clinical-history` → Firmar exige capacidad clínica · Credencial del firmante fotografiada al sellar · Firmas anteriores al registro de credenciales; spec `dental-chart` → Credencial del firmante en el odontograma sellado)*: cerrar la admisión con capacidad (Historia y odontograma con la misma foto); sin credencial, vencida y retirada → 403 con su `reason` y nada sellado; borradores sin capacidad se siguen guardando; renovar o corregir el nombre no cambia lo firmado; vencer o retirar después no invalida lo firmado; Historia, odontograma y nota sellados antes del cambio, cada uno por separado → `signedBeforeCredentials` verdadero
- [x] 4.5 IT de concurrencia con hilos reales, pool propio y orden forzado (`awaitLockWaitOrFinished`), primero contra el código sin `FOR SHARE` para ver que fallan: retiro contra sellado de nota y contra cierre de admisión, y desactivación contra sellado (nunca sellado después de un retiro o una desactivación confirmados); renovación contra sellado (la foto lleva la fecha que estaba confirmada); dos sellados del mismo profesional para dos pacientes no se bloquean *(Retiro confirmado mientras se sella)*
- [x] 4.6 Los IT existentes de cierre de admisión y de sellado de nota declaran la credencial en su preparación; `mvn verify` en verde

## 5. Contrato

- [x] 5.1 OpenAPI de los endpoints nuevos y de los campos nuevos (200, 204, 400, 401, 403 con `reason`, 404, 409), `operationId` únicos; regenerar `contracts/openapi.json` **antes** de `mvn verify` (`ContractDriftIT`) y el cliente generado del front

## 6. Frontend: credencial propia

- [x] 6.1 Módulo y hook de React Query para `GET`/`PUT /me/professional-credential` (`clinicalCapacity` nulo sin clínica activa); schema Zod con paridad (COP solo dígitos 1–10, fecha no anterior a hoy, declaración jurada obligatoria) (`docs/coding-style.md` §7)
- [x] 6.2 Pantalla "Mi credencial profesional" accesible a toda membresía (design D7): estado en la clínica activa con su motivo, formulario con la declaración jurada, aviso de que la habilitación es autodeclarada; 409 y 400 del servidor mostrados en el campo. Verificar con Vitest/MSW: declarar, renovar, errores de COP y de fecha, sin declaración jurada, conflicto por COP

## 7. Frontend: personal

- [x] 7.1 Columna de capacidad clínica en Personal (sin credencial, habilitado hasta, vencida, retirada, con COP) y acción retirar/restituir con confirmación, no ofrecida sobre la propia fila ni sobre otro `ADMIN`. Verificar con Vitest/MSW: cada estado, retirar, cancelar, restituir

## 8. Frontend: firmar

- [x] 8.1 Botones de cerrar la admisión y de sellar la nota deshabilitados sin capacidad clínica, con el motivo y el enlace a la pantalla de credencial; el 403 del servidor muestra el mismo texto *(La interfaz explica por qué no se puede firmar)*
- [x] 8.2 Historia `COMPLETED`, odontograma sellado y nota sellada muestran "Firmado por <nombre> · COP <número>" o "Firmado antes del registro de credenciales". Verificar con Vitest/MSW, incluidos los tests existentes de `SealedNoteCard` y del cierre de la admisión
- [x] 8.3 `pnpm validate` en verde

## 9. Integración

- [x] 9.1 Prueba manual con backend y frontend locales: sin credencial no se firma y el botón explica por qué; declarar, cerrar una admisión y sellar una nota, y ver el firmante; como `ADMIN`, retirar la capacidad de un `MEMBER` y comprobar que no puede sellar; una nota sellada antes del cambio se ve como "firmada antes del registro de credenciales"
- [x] 9.2 `openspec validate update-staff-professional-credential --strict` y medir a mano los requirements (≤ 500 caracteres) — *apply 2026-10-26: válido; los 10 miden de 315 a 499 (el MODIFIED de COMPLETED conserva su texto de 499)*

## Workflow follow-up

- Al archivar: `docs/domain.md` (credencial profesional en el ER y en la tabla de actores; quitar el aviso de
  "la credencial clínica no está construida"), `docs/vision.md` (fila de `staff` y su "Pendiente"), la fila
  *Autoría por profesional habilitado* de `docs/compliance/regulatory-matrix.md`, nota en `ADR-0016` D7
  (asimetría otorgar/retirar y dos niveles credencial/membresía) y el Purpose de `openspec/specs/staff/spec.md`
  (suma la credencial que declara el propio profesional).
- Antes de archivar: confirmar con una constancia real si la habilitación del COP trae una fecha de vigencia
  (Open Question del design) y ajustar el texto de ayuda.
- Al desplegar: cada profesional declara su credencial antes de volver a firmar (notas del release).
