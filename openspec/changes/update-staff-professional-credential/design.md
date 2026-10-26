# Design

## Context

Motivación y alcance: ver `proposal.md`. Lo que condiciona el cómo:

- **Dos operaciones de firma, no tres.** `POST /patients/{id}/clinical-history/complete` entra por
  `CloseAdmissionService`, que llama a `ClinicalHistoryService.complete` y a `DentalChartService.sealInitial` en
  una sola transacción; `sealInitial` no tiene endpoint propio. La nota se sella en
  `POST /patients/{id}/consultation-notes/{noteId}/seal` (`ConsultationNoteService.seal`).
- **Quién firmó, hoy:** `consultation_note.sealed_by` (user id). `historia_clinica` y `dental_chart_block` no lo
  guardan (solo `created_by`/`updated_by`, que son de edición, no de firma).
- **RLS:** `users` no tiene RLS. `memberships` tiene `membership_identity_isolation` (V24/V28): el usuario ve las
  suyas y un `ADMIN` activo ve las de su organización activa, vía `current_user_is_org_admin` (`SECURITY
  DEFINER`). La app corre como `odontorisas_app`, sin `BYPASSRLS`.
- **Guardas de sellado:** V20 y V22 ponen triggers que rechazan modificar odontogramas y notas sellados. Valen
  también para el dueño de las tablas, así que una migración que reescriba filas selladas chocaría con ellos.
- **Zona horaria:** cada clínica tiene la suya en `OrganizationSettings` (por defecto `America/Lima`); la nota de
  consulta ya la usa para validar la fecha de atención.

## Goals / Non-Goals

**Goals:**
- Que "nadie declara la credencial de otro" lo garantice **la base**, no solo el servicio.
- Que lo ya sellado quede intacto **sin reescribir filas** y que un sellado nuevo sin foto sea imposible
  también en la base.
- Una sola fuente para la capacidad clínica efectiva, usada por las dos firmas y por la consulta del usuario.

**Non-Goals:**
- No se cambia el flujo ni el checklist de cierre de la admisión ni del sellado de la nota: solo se agrega la
  comprobación y la foto.
- No se agrega la capacidad clínica al token: se evalúa en cada firma (vence por fecha y se retira en caliente).

## Decisions

### D1 — La credencial en una tabla propia, con RLS que solo deja escribir la propia

`professional_credential` (una fila por usuario): `user_id` (PK, FK a `users`), `cop_number` (único),
`licensed_until` (`DATE`), `sworn_at` (cuándo aceptó la declaración jurada), `created_at`, `updated_at`.

- `SELECT`: la propia (`user_id = app.user_id`) **o** la de un miembro de la organización activa si quien lee es
  su `ADMIN` activo: el mismo patrón que `membership_identity_isolation`, con una función `SECURITY DEFINER`
  nueva (`current_user_is_org_admin_of_user`), para no recursar sobre la RLS de `memberships`.
- `INSERT`/`UPDATE`: `WITH CHECK (user_id = app.user_id)`. Sin `DELETE` para el rol de la app.
- **Unicidad del COP:** índice único. La RLS no oculta una violación de unicidad: el `INSERT` choca aunque la otra
  fila sea invisible, y el servicio lo traduce a un 409 genérico, sin datos de la otra fila.

**Alternativa descartada: columnas en `users`.** `users` no tiene RLS y la leen los flujos de autenticación y de
personal; la garantía de "solo la propia" quedaría solo en el servicio. La tabla aparte la pone en la base, que
es donde la capacidad clínica tiene que estar asegurada.

### D2 — El retiro en la membresía

`memberships` suma `clinical_capacity_revoked_at` (`TIMESTAMPTZ`, nulo si no hay retiro) y
`clinical_capacity_revoked_by`. Restituir los vuelve a nulo. La escritura usa la misma RLS que ya permite al
`ADMIN` desactivar miembros. Las reglas sobre a quién (solo `MEMBER`, ni a sí mismo ni a otro `ADMIN`) van en el
servicio, igual que `PATCH /staff/members/{id}/status`.

### D3 — Capacidad clínica efectiva: un solo servicio

`ClinicalCapacityService` resuelve, para el usuario de la sesión y la organización activa, uno de cuatro
resultados: `ENABLED` (con la credencial), `NO_CREDENTIAL`, `EXPIRED` (con la fecha) o `REVOKED`. La vigencia
se compara con la **fecha de hoy en la zona horaria de la clínica**, e incluye el último día. `require()` lanza
`ClinicalCapacityRequiredException` (403 RFC 9457 con la propiedad `reason`, `docs/backend.md`) o devuelve la
foto (`SignerSnapshot`: id, nombre, COP, `licensed_until`). La consulta del usuario y la columna de Personal usan
el mismo cálculo.

### D4 — Dónde se comprueba

- **Cierre de la admisión:** en `CloseAdmissionService`, **dentro de la misma transacción y antes** de
  `complete`/`sealInitial`. La misma foto se pasa a los dos, así que la Historia y el odontograma guardan
  exactamente los mismos datos.
- **Sellado de la nota:** en `ConsultationNoteService.seal`, dentro de su transacción, después de bloquear la
  nota y antes de validar su contenido.
- Ningún otro endpoint clínico cambia (borradores: non-goal).

### D5 — La foto en el registro firmado, y lo viejo protegido con `NOT VALID`

Columnas nuevas, nulas:

| Tabla | Quién firmó | Foto |
|---|---|---|
| `historia_clinica` | `completed_by` (nueva) | `signer_full_name`, `signer_cop_number`, `signer_licensed_until` |
| `dental_chart_block` | `sealed_by` (nueva) | ídem |
| `consultation_note` | `sealed_by` (ya existe) | ídem |

Regla en la base: `CHECK (status <> '<sellado>' OR (<quién firmó> IS NOT NULL AND signer_* IS NOT NULL))`,
agregada como **`NOT VALID`**. PostgreSQL la aplica a todo `INSERT`/`UPDATE` nuevo y no la verifica sobre las
filas existentes. Resultado:

- Lo sellado antes del cambio **no se toca**: ni se reescribe ni hay que desactivar las guardas de V20/V22.
- Un sellado nuevo sin foto es **imposible**, aun escribiendo directo en la base.
- "Firmado antes del registro de credenciales" se **deriva**: sellado y sin foto. No hace falta guardar una
  marca.

**Alternativa descartada: columna-marca** (`signed_before_credentials`) puesta por la migración. Obliga a
reescribir filas selladas, desactivando a mano las guardas de V20/V22, y deja una columna que habría que
proteger para que la app no la pueda poner en `true`. `NOT VALID` da la misma garantía sin reescribir nada.

⚠️ `NOT VALID` sobrevive a `pg_dump`/`pg_restore` tal cual. Al restaurar no se valida, igual que en el origen; no
afecta a la verificación de copias de `add-database-backups`.

### D6 — API

- `GET /me/professional-credential`: la credencial propia (o vacía) y la capacidad clínica en la organización
  activa (`state`, `reason`, `licensedUntil`, `expiredOn`).
- `PUT /me/professional-credential`: `{ copNumber, licensedUntil, sworn }`. `sworn` debe ser `true`. El usuario
  sale de la sesión, nunca del cuerpo ni de la ruta, así que no hay forma de nombrar a otro.
- `MemberResponse` suma `clinicalCapacity` (`state`, `copNumber`, `licensedUntil`).
  `PATCH /staff/members/{id}/clinical-capacity` con `{ revoked: boolean }`, como el `PATCH .../status` de hoy.
- Las respuestas de la Historia, el odontograma y la nota suman `signer` (`fullName`, `copNumber`,
  `licensedUntil`) y `signedBeforeCredentials` (derivado, D5).
- Paridad Zod ↔ Bean Validation (`docs/coding-style.md` §7): COP solo dígitos (1 a 10, como cota de
  almacenamiento, no como formato del COP) y fecha no anterior a hoy. En el front, "hoy" es la fecha local; el
  servidor decide con la de la clínica.

### D7 — Frontend

- **Pantalla "Mi credencial profesional"**, accesible a toda membresía (no solo `ADMIN`), con el estado de su
  capacidad en la clínica activa y el formulario con la casilla de declaración jurada.
- **Personal:** columna de capacidad clínica y acción retirar/restituir con confirmación, con el patrón de
  desactivar/reactivar. No se ofrece sobre la propia fila ni sobre otro `ADMIN`.
- **Botones de firma** (cerrar admisión, sellar nota): un hook de React Query sobre
  `GET /me/professional-credential` los deshabilita con el motivo y un enlace a la pantalla. El 403 del servidor
  se muestra con el mismo texto, por si el estado cambió entre la consulta y el clic.
- **Lo firmado** muestra "Firmado por <nombre> · COP <número>" o "Firmado antes del registro de credenciales".

## Risks / Trade-offs

- [La habilitación es autodeclarada] → Se dice en la pantalla y en la declaración jurada. El modelo admite
  verificar contra el registro del COP después, sin cambiar de forma (`ADR-0018` D6).
- [Al desplegar, nadie puede firmar hasta declarar su credencial] → Hoy es una sola clínica con datos de prueba.
  El botón deshabilitado lleva a la pantalla de declaración. Va en las notas del release.
- [Un retiro concurrente con un sellado] → La comprobación ocurre dentro de la transacción del sellado. Si el
  retiro confirma antes, el sellado se rechaza; si confirma después, el sellado ya quedó con una capacidad que
  era válida en ese momento. Los dos órdenes son correctos, no se bloquea `memberships`.
- [El 409 por COP duplicado revela que ese número ya está en Dentdesk] → Se acepta: el padrón del COP es público
  y la respuesta no dice quién ni en qué clínica. A cambio impide usar el número de otro profesional.
- [`NOT VALID` deja filas que no cumplen el `CHECK`] → Es intencional, y solo son las selladas antes del cambio,
  que no se pueden modificar: las guardas de V20/V22 en la base para el odontograma y la nota, y el servicio
  para la Historia (`historia_clinica` no tiene trigger de sellado). Si alguna vez se intentara un `UPDATE` sobre
  una de esas filas, el `CHECK` lo rechazaría: una protección más, no una regresión.

## Migration Plan

1. `V30`: tabla `professional_credential` con su RLS, función `SECURITY DEFINER`, columnas de retiro en
   `memberships`, columnas de la foto y los `CHECK ... NOT VALID`. Permisos para `odontorisas_app` como en V6.
   ⚠️ En Neon la migración corre como `odontorisas_admin`, con RLS forzada: no lee filas de otras clínicas, y no
   lo necesita (no hay `UPDATE` de datos).
2. Desplegar backend y frontend juntos (el front nuevo lee `signer` y `clinicalCapacity`).
3. Cada profesional declara su credencial antes de volver a firmar.
4. **Rollback:** las columnas y la tabla son aditivas y nulas; revertir la app deja la base compatible. Flyway no
   se revierte: se avanza con una migración nueva si hiciera falta.

## Open Questions

- **¿La constancia de habilitación del COP trae una fecha de vigencia impresa?** Si la trae, la ayuda del campo
  dice "la fecha de tu constancia". Si no, dice "el último día del período de cuotas pagado". Cambia el texto de
  ayuda, no el modelo ni los requirements. Se confirma con una constancia real antes de archivar.
- **Formato del número COP.** Se acepta solo dígitos, de 1 a 10, sin suponer una longitud fija; si el COP usa una
  fija, se ajusta la validación sin cambiar el modelo.
