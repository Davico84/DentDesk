## Decisiones

### D1 — Tabla global separada

`platform_admins(user_id PRIMARY KEY REFERENCES users)` será la fuente de autorización. `PLATFORM_ADMIN` no se modela como `Membership` ni como `Role`, porque no pertenece a una clínica.

### D2 — Bootstrap sin endpoint público

El primer administrador se crea mediante un CLI o seed operativo ejecutado una sola vez por entorno. No existirá un endpoint público de bootstrap.

### D3 — Aprovisionamiento atómico

`POST /platform/organizations` crea organización, configuración e invitación `ADMIN` en una sola transacción. El token se entrega una sola vez y se envía al correo nominativo.

### D4 — JWT y contexto de plataforma

El JWT de plataforma llevará `authorities: ["ROLE_PLATFORM_ADMIN"]` y no llevará `oid`. El filtro de tenant omitirá `/platform/**`, no establecerá `OrganizationContext` para esas rutas y ningún `PLATFORM_ADMIN` podrá acceder a datos clínicos por el mero hecho de ser global.

### D5 — Dependencia con invitaciones

Este change depende de `update-registration-organization-linking`: reutiliza su entidad, servicio, hash, expiración, correo y consumo atómico de invitaciones. Debe aplicarse después.

### D6 — El correo se envía dentro de la transacción, después de un flush

El envío SMTP es un efecto externo: **no** participa de la transacción y no puede hacerse atómico
con ella. Se eligió enviarlo *antes* del commit, tras un `saveAndFlush` de la invitación (que vuelca
también organización y configuración):

| Falla | Resultado |
| --- | --- |
| Cualquier `INSERT` (restricción, slug, correo tomado) | Falla antes de enviar; rollback; no sale correo. |
| El SMTP (`InvitationDeliveryException`, 503) | Rollback completo; no queda organización ni invitación. Se puede reintentar. |
| El propio `COMMIT`, después de enviar | Llega un correo cuyo enlace no resuelve (el hash nunca se guardó). Es inofensivo: el registro lo rechaza como invitación inválida. |

La alternativa —enviar *después* del commit— invierte el riesgo: un fallo SMTP dejaría una clínica
con una invitación pendiente que nadie recibió y, como la API no devuelve el token y no hay reenvío,
sin forma de entrar. Se prefiere un correo huérfano raro a una clínica huérfana. El coste es retener
la conexión a BD mientras dura el envío SMTP, aceptable para una operación administrativa de bajo
volumen. Si aparece reenvío de invitaciones, conviene reevaluar con un outbox.

### D7 — Sin correo configurado, la invitación se rechaza

Con `MAIL_ENABLED=false` el `InvitationMailer` lanza `InvitationDeliveryException` (503) en vez de
descartar el correo en silencio. Un no-op dejaría invitaciones pendientes cuyo token nadie conoce;
en la inicial, una clínica inaccesible. Aplica también a las invitaciones `MEMBER` (mismo puerto).
Los tests sustituyen el bean por un mock y leen de él el token y el código.

### D8 — Ámbito de cada token

El filtro deja **sin autenticar** un token de plataforma fuera de `/platform/**` (no lleva `oid`, no
hay tenant que fijar: 401). Un token de clínica en `/platform/**` sí se autentica y lo rechaza el rol
(403), por `@PreAuthorize` y por una regla de URL `hasRole('PLATFORM_ADMIN')` sobre `/platform/**`
que cubre un endpoint futuro que olvide la anotación. El login de plataforma aplica el mismo bloqueo
por intentos fallidos que el de clínica.

## Open questions

- Cambiar la contraseña de un `PLATFORM_ADMIN` ya creado: el bootstrap es idempotente y **no** la
  pisa, y no existe endpoint de cambio de contraseña (gestión de usuarios globales está fuera de
  alcance). Hoy solo se puede por operación directa en BD.

## Bootstrap operativo

El bootstrap se ejecuta al arrancar el backend únicamente cuando el entorno define
`PLATFORM_ADMIN_BOOTSTRAP_ENABLED=true`, `PLATFORM_ADMIN_EMAIL`,
`PLATFORM_ADMIN_FULL_NAME` y `PLATFORM_ADMIN_PASSWORD`. Crea la identidad global y su fila
en `platform_admins`; si ya existe esa autorización, la operación es idempotente.

La contraseña debe inyectarse como secreto del entorno y retirarse después del primer arranque.

Procedimiento por entorno:

1. Definir como secretos `PLATFORM_ADMIN_BOOTSTRAP_ENABLED=true`, `PLATFORM_ADMIN_EMAIL`,
   `PLATFORM_ADMIN_FULL_NAME` y `PLATFORM_ADMIN_PASSWORD`, y arrancar (o redesplegar) el backend.
2. Comprobar el login en `POST /platform/auth/login`.
3. Poner `PLATFORM_ADMIN_BOOTSTRAP_ENABLED=false` y **borrar** `PLATFORM_ADMIN_PASSWORD` del entorno.

Reglas del runner: si el correo ya es `PLATFORM_ADMIN`, no hace nada (tampoco cambia la contraseña);
si pertenece a una identidad de clínica, **el arranque falla** — una identidad existente no se
promueve a global por configuración. El login posterior se realiza en `POST /platform/auth/login`; la credencial inicial no
se crea mediante ningún endpoint de bootstrap público.

