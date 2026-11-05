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
  su `ADMIN` activo, con una función nueva (`current_user_is_admin_of_member`). *Apply:* quedó **`SECURITY
  INVOKER`**, no `DEFINER`: la rama de administrador de `membership_identity_isolation` (V28) ya le deja ver las
  membresías de su organización activa, y `memberships` no consulta esta tabla, así que no hay recursión que
  evitar ni RLS que saltar.
- `INSERT`: `WITH CHECK (user_id = app.user_id)`.
- `UPDATE`: `USING (user_id = app.user_id) WITH CHECK (user_id = app.user_id)`. El `USING` es imprescindible: con
  solo `WITH CHECK`, un `UPDATE` sobre una fila ajena podría reescribir su `user_id` al propio y pasar el
  control. La policy de `SELECT` no reemplaza al `USING` del `UPDATE`.
- Sin policy de `DELETE`: el rol de la app no borra credenciales.
- Una policy por comando (no `FOR ALL`), para que ninguna rama de lectura del `ADMIN` se cuele en la escritura.
- **Sin upsert.** Primera declaración = `INSERT`; renovación = `UPDATE ... WHERE user_id = <sesión>`. Ni
  `INSERT ... ON CONFLICT DO UPDATE` ni `merge` genérico de JPA: un upsert contra el COP de otro usuario podría
  terminar en un rechazo de RLS en vez de en la violación de unicidad, y el 409 dejaría de ser el único camino. Con
  `INSERT` o `UPDATE` explícitos, un COP ajeno siempre llega como violación del índice único, en cualquiera de los
  dos casos.
- **Unicidad del COP:** índice único con nombre propio. La RLS no oculta una violación de unicidad: el `INSERT`
  choca aunque la otra fila sea invisible. El servicio captura **esa** restricción por su nombre y lanza una
  excepción tipada (`credential-number-taken`, 409) con un texto fijo y **sin la causa SQL**. El manejador
  genérico de `DataIntegrityViolationException` lee el mensaje de PostgreSQL, que trae el valor (`Key
  (cop_number)=(…)`), y no debe ver este caso. *Apply:* en los logs de Hibernate el valor tampoco aparece,
  porque PostgreSQL omite el `Detail` de una violación de unicidad cuando la tabla tiene RLS (para no revelar
  filas invisibles). Lo verifica `ProfessionalCredentialIT` con captura de la salida. Queda un oráculo de existencia (se puede saber si un número ya está
  en Dentdesk); se acepta en Risks.

**Alternativa descartada: columnas en `users`.** `users` no tiene RLS y la leen los flujos de autenticación y de
personal; la garantía de "solo la propia" quedaría solo en el servicio. La tabla aparte la pone en la base, que
es donde la capacidad clínica tiene que estar asegurada.

### D2 — El retiro en la membresía

`memberships` suma `clinical_capacity_revoked_at` (`TIMESTAMPTZ`, nulo si no hay retiro) y
`clinical_capacity_revoked_by`. Restituir los vuelve a nulo. Las reglas sobre a quién (solo `MEMBER`, ni a sí
mismo ni a otro `ADMIN`) van en el servicio, igual que `PATCH /staff/members/{id}/status`.

**Y además en la base.** `membership_identity_isolation` (V28) es una policy para todos los comandos con solo
`USING`, que vale también como `WITH CHECK`: un usuario puede hacer `UPDATE` sobre **su propia** membresía. Con el
retiro en esa fila, un miembro podría levantarse su propio retiro con una escritura directa. Por eso un trigger
`BEFORE INSERT OR UPDATE` (sin `OF <columnas>`, para no depender de qué nombra el `SET`):

- **`INSERT`:** la membresía nace **sin** retiro (`clinical_capacity_revoked_at` y `_by` nulos). Nadie da de alta
  una membresía ya retirada.
- **`UPDATE`** en el que el retiro cambia (`OLD` distinto de `NEW` en cualquiera de las dos columnas): valida el
  **estado final**. Quien escribe debe ser `ADMIN` activo de esa organización (`current_user_is_org_admin`), la
  fila no debe ser la suya, y `NEW.role` debe ser `MEMBER`. Así un `UPDATE` que cambie el rol y el retiro a la vez
  no se cuela validando el rol viejo.
- Es el **único** trigger de `memberships` y se nombra para correr último (`zz_…`), de modo que valida lo que
  dejen otros triggers `BEFORE` que pudieran agregarse después. Un `CHECK` de coherencia (`_at` y `_by` nulos o
  no nulos a la vez) completa la regla.

Así la asimetría "otorgar nunca, retirar sí" vive también en la base.

⚠️ La policy de V28 deja abierto de la misma forma el resto de las columnas de la propia membresía (`role`,
`status`). Es previo a este change y no se toca acá; queda anotado para el change de la puerta de lectura, que
revisa los permisos de `memberships`.

### D3 — Capacidad clínica efectiva: un solo servicio

`ClinicalCapacityService` resuelve, para el usuario de la sesión y la organización activa, uno de cinco
resultados, evaluados en este orden: `INACTIVE_MEMBERSHIP` (la membresía no está `ACTIVE`), `REVOKED`,
`NO_CREDENTIAL`, `EXPIRED` (con la fecha) o `ENABLED` (con la credencial). La vigencia se compara con la **fecha
de hoy en `America/Lima`** e incluye el último día.

**Membresía activa.** Un miembro desactivado con un token de acceso todavía vigente no debe poder firmar: el
estado `ACTIVE` de la membresía es parte de la capacidad, leído con el mismo `FOR SHARE` de D4. Así la
desactivación concurrente con un sellado se comporta igual que el retiro.

**Por qué Lima y no la zona de la clínica.** La fecha "habilitado hasta" la fija el COP, que es peruano: es una
fecha de Perú, sea cual sea la zona de la clínica. Evaluarla en la zona de cada clínica haría que la misma
credencial estuviera vigente en una clínica y vencida en otra cerca de medianoche, por un dato que no cambió. Con
una sola zona, la credencial, que es global, tiene un solo vencimiento. La validación "no anterior a hoy" del
`PUT` usa la misma zona, así que no depende de tener clínica activa.

**Sin clínica activa.** La credencial es del usuario: `GET` y `PUT /me/professional-credential` funcionan sin
organización activa. La **capacidad** depende de la membresía: sin clínica activa la respuesta trae
`clinicalCapacity: null` (campo nulo y documentado así en OpenAPI, el DTO y el schema Zod), y `require()` no se
puede llamar, porque las dos firmas exigen clínica activa por su propia ruta.

`require()` lanza
`ClinicalCapacityRequiredException` (403 RFC 9457 con la propiedad `reason`, `docs/backend.md`) o devuelve la
foto (`SignerSnapshot`: id, nombre, COP, `licensed_until`). La consulta del usuario y la columna de Personal usan
el mismo cálculo.

### D4 — Dónde se comprueba

- **Cierre de la admisión:** en `CloseAdmissionService`, **dentro de la misma transacción y antes** de
  `complete`/`sealInitial`. La misma foto se pasa a los dos, así que la Historia y el odontograma guardan
  exactamente los mismos datos.
- **Sellado de la nota:** en `ConsultationNoteService.seal`, dentro de su transacción, **antes** de bloquear la
  nota y de validar su contenido.
- Ningún otro endpoint clínico cambia (borradores: non-goal).

**Bloqueo contra el retiro y la renovación concurrentes.** Comprobar dentro de la transacción no basta: el sellado
podría leer la membresía sin retiro, el `ADMIN` retirar y confirmar, y el sellado confirmar después. Por eso
`require()` lee la membresía y la credencial con **`SELECT ... FOR SHARE`**:

- **Retiro o restitución** (`UPDATE` de `memberships`) y **renovación** (`UPDATE` de `professional_credential`)
  esperan a que el sellado confirme. Entonces el sellado queda con la capacidad que era válida en su momento, y el
  cambio se aplica después.
- Si el retiro o la renovación confirmaron **antes**, el `FOR SHARE` espera su commit y, en `READ COMMITTED`,
  vuelve a leer la versión nueva: un retiro rechaza el sellado, y una renovación lo deja pasar con la fecha nueva
  en la foto.
- `FOR SHARE` y no `FOR UPDATE`: dos sellados del mismo profesional (dos pacientes a la vez) no se bloquean entre
  sí.
- **Orden de bloqueo, único para las dos firmas:** **membresía → credencial → filas clínicas** en su orden actual
  (Historia → odontograma en la admisión; la nota en su sellado). `require()` es lo primero de la transacción. El
  retiro, la restitución y la desactivación solo toman la membresía, y la renovación solo la credencial, así que no
  hay ciclo posible.
- `FOR SHARE` exige el privilegio `UPDATE` (lo tiene `odontorisas_app`, V6) y pasar el `USING` de la policy: la
  membresía y la credencial propias lo pasan.

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

**Consecuencia buscada: lo viejo queda inmutable también en la base.** PostgreSQL vuelve a evaluar el `CHECK` en
cada `UPDATE` de una fila, aunque se haya agregado `NOT VALID`. Una Historia `COMPLETED` sin foto ya no admite
ningún `UPDATE`, ni de metadatos ni de una migración futura. El odontograma y la nota ya eran así por sus triggers
(V20/V22); la Historia, que solo tenía la guarda del servicio, gana una en la base. Una migración futura que
necesite tocar esas filas tendrá que decidirlo explícitamente, y esa es la fricción correcta para un libro de
actas.

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
  servidor decide con la de `America/Lima` (D3).

### D7 — Frontend

- **Pantalla "Mi credencial profesional"**, accesible a toda membresía (no solo `ADMIN`), con el estado de su
  capacidad en la clínica activa y el formulario con la casilla de declaración jurada. La ayuda del campo de fecha
  dice que la vigencia se cuenta en la hora de Perú; un rechazo del servidor por fecha pasada se muestra en el campo
  aunque el navegador, en otra zona, la viera válida.
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
- [Un retiro, una desactivación o una renovación concurrentes con un sellado] → `FOR SHARE` sobre la membresía y
  la credencial (D4). Nunca queda sellado un registro después de un retiro ya confirmado. Costo: un retiro espera a que
  terminen los sellados en curso de ese profesional, que duran milisegundos.
- [El 409 por COP duplicado es un oráculo de existencia: dice si un número ya está en Dentdesk] → Se acepta. El
  padrón del COP es público, y la respuesta no dice quién ni en qué clínica. A cambio, impide usar el número de
  otro profesional. El texto es fijo y la causa SQL, que trae el valor, no llega ni a la respuesta ni a los logs
  (D1).
- [`NOT VALID` deja filas que no cumplen el `CHECK`] → Es intencional, y solo son las selladas antes del cambio,
  que no se pueden modificar: las guardas de V20/V22 en la base para el odontograma y la nota, y el servicio
  para la Historia (`historia_clinica` no tiene trigger de sellado). Si alguna vez se intentara un `UPDATE` sobre
  una de esas filas, el `CHECK` lo rechazaría: una protección más, no una regresión.

## Migration Plan

1. `V30`: tabla `professional_credential` con su RLS por comando, función de lectura del `ADMIN`, columnas de retiro en
   `memberships` con su trigger de guarda, columnas de la foto y los `CHECK ... NOT VALID`. Permisos para `odontorisas_app` como en V6.
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
  **Cerrada (2026-10-27):** el responsable confirmó que la constancia trae la fecha impresa; la ayuda dice "La fecha
  de vigencia que figura en tu constancia de habilitación del COP" (`update-authentication-my-profile`).
- **Formato del número COP.** Se acepta solo dígitos, de 1 a 10, sin suponer una longitud fija; si el COP usa una
  fija, se ajusta la validación sin cambiar el modelo.
