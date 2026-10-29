# Design

## Context

- El enlace "¿Olvidaste tu contraseña?" de `LoginForm.tsx` es un `<button>` sin acción; no hay backend.
- `users` (identidad global) y `refresh_tokens` **no tienen RLS**; `memberships` sí (RLS de identidad), y se lee
  fijando la identidad con `tenantSession.bindIdentity` como hace `AuthService.activeMembership`.
- Los administradores de plataforma son filas de `users` marcadas en `platform_admins` (V26), con la **misma**
  contraseña. Su login (`PlatformAuthService`) no emite refresh token.
- El bloqueo por intentos (`update-authentication`) vive en `users.failed_login_attempts` + `locked_until`
  (5 intentos, 15 min). El bloqueo administrativo es otro mecanismo: `UserStatus` y el estado de la membresía.
- `update-authentication-my-profile` dejó el protocolo de bloqueo de fila (su D3): el login exige el hash verificado
  al resetear los intentos y la renovación toma `FOR SHARE` sobre `users`. Ese protocolo ya cubre cualquier cambio
  de `password_hash`, incluido el de este change.
- Correo: `SmtpInvitationMailer` sobre Brevo, con timeouts (`app.mail.timeout`). Sin correo configurado, el mailer
  deshabilitado lanza excepción.
- **`@Async` y `@Scheduled` están prohibidos** por ArchUnit (`MultiTenancyArchitectureTest`): el contexto de tenant
  vive en `ThreadLocal` de la petición y `TenantAwareTransactionManager` depende de un solo hilo.
- El token de invitación (`/auth/register?token=`) usa el mismo patrón de hash SHA-256 que este change reutiliza.

## Goals / Non-Goals

**Goals:** recuperar la contraseña sin revelar qué cuentas existen; enlace corto, de un uso y anulable; cerrar todas
las sesiones; no desbloquear; dejar fuera a la plataforma; avisar por correo de todo cambio de contraseña.

**Non-Goals:** los del proposal (2FA, límite por IP, desbloqueo, recuperación de plataforma desde la app, cambio de
correo, reglas de contraseña nuevas, correo con marca de la clínica).

## Decisions

### D1 — Tres endpoints públicos bajo `/auth`, el token siempre en el cuerpo

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `POST /auth/password-recovery` | `{ email }` | `202` sin cuerpo, **siempre** (salvo `400` de formato) |
| `POST /auth/password-reset/check` | `{ token }` | `204` si es vigente; `400 invalid-reset-link` si no |
| `POST /auth/password-reset` | `{ token, newPassword }` | `200 { outcome, lockedUntil? }`; `400 invalid-reset-link`; `400` validación |

- Los tres se suman a la lista explícita de `PublicPaths` (desde Mi perfil, una ruta nueva no nace pública).
- El token viaja en el **cuerpo**, nunca en la URL de la API: no queda en logs de acceso ni de proxies.
- `check` existe para no hacer escribir una contraseña a quien abrió un enlace muerto. No revela nada que el
  `reset` no revele, y responde lo mismo para inválido, vencido, usado o anulado.
- `outcome` ∈ `OK` · `LOCKED` (con `lockedUntil`) · `ACCESS_DISABLED`. Solo lo ve quien tiene un enlace vigente,
  es decir, quien demostró ser dueño del correo: no contradice la anti-enumeración del login.

**Alternativa descartada:** devolver el estado de la cuenta en el pedido (`/password-recovery`). Revelaría la
cuenta a cualquiera.

### D2 — Tabla `password_reset_tokens`

```
password_reset_tokens
  id          BIGSERIAL PK
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
  token_hash  CHAR(64) NOT NULL UNIQUE      -- SHA-256 hex del token
  created_at  TIMESTAMPTZ NOT NULL
  expires_at  TIMESTAMPTZ NOT NULL          -- created_at + 30 min
  used_at     TIMESTAMPTZ NULL
  revoked_at  TIMESTAMPTZ NULL              -- anulado por un enlace nuevo o por el restablecimiento
  INDEX (user_id, created_at)
```

- Sin RLS, como `users` y `refresh_tokens`: es de la identidad global, no de una clínica. Grants a
  `odontorisas_app` con el mismo bloque `DO $$` condicional de las migraciones anteriores.
- Token: 32 bytes aleatorios en base64url; se guarda solo el SHA-256 (mismo helper que los refresh tokens).
- **Vigente** = `used_at IS NULL AND revoked_at IS NULL AND expires_at > now`.
- **Límite** (D3) = filas de la cuenta con `created_at > now - 24 h`, sin importar si se usaron, se anularon o falló
  el correo: lo que se limita son los envíos.
- **Limpieza** sin tarea programada (prohibida): al emitir un enlace se borran las filas de esa cuenta con más de
  24 h. Las de cuentas que nunca vuelven a pedir quedan; son pocas y sin datos sensibles (solo el hash).

### D3 — Pedido: misma respuesta y mismo tiempo, el correo fuera de la petición

En una transacción:

1. normalizar el correo (`trim` + minúsculas) y buscar el usuario;
2. si no existe, no está `ACTIVE` o es administrador de plataforma → nada; responder `202`;
3. `SELECT ... FOR UPDATE` de la fila de `users`: serializa los pedidos simultáneos de la misma cuenta, para que
   cuatro a la vez no pasen los cuatro el conteo;
4. contar los enlaces de las últimas 24 h; si hay 3 → nada; `202`;
5. anular los vigentes (`revoked_at`), borrar los de más de 24 h, insertar el nuevo;
6. registrar un `afterCommit` que entrega el correo al executor de D4; `202`.

El tiempo de BCrypt no interviene (no se verifica contraseña). La diferencia entre "existe" y "no existe" queda en
unas pocas consultas a la base, del orden de milisegundos, frente a los segundos del SMTP, que es lo que delataría
la cuenta. Ver Riesgos.

El correo sale **después del commit**: si la transacción falla, no llega un enlace que no existe en la base.

### D4 — Envío de correo con un executor propio, no `@Async`

- Un bean `ExecutorService` dedicado (`mailExecutor`): 2 hilos y una cola acotada. Si la cola se llena, la tarea se
  descarta con un log; el usuario puede volver a pedir.
- La tarea recibe **solo datos ya calculados** (destinatario, enlace o fecha del cambio, vencimiento): no toca la
  base ni lee `IdentityContext`/`OrganizationContext`. Por eso no choca con la razón del tripwire de `@Async`.
- Nueva regla de ArchUnit: el paquete `infra.mail` **no depende** de `persistence` ni de los contextos de tenant.
  Hace explícito y vigilado lo que el tripwire de `@Async` protege.
- Un fallo de entrega se registra **sin** el token, el enlace ni datos de la contraseña.
- En el apagado, el executor termina con un plazo corto (`shutdown` + `awaitTermination`).
- Nueva interfaz `AccountMailer` (enlace de recuperación y aviso de cambio), separada de `InvitationMailer`: la
  invitación **debe** fallar la petición si no se envía (add-platform-admin D6); estos correos no. Sin correo
  configurado, la variante deshabilitada registra y descarta: en ese entorno la recuperación no funciona, pero la
  respuesta sigue siendo la misma.
- En los tests, un `AccountMailer` que registra lo enviado y un executor que corre en el mismo hilo, para afirmar
  sin esperas. Un test aparte comprueba con el executor real que la respuesta no espera a un SMTP lento.

### D5 — Restablecer: orden de bloqueo `users` → `password_reset_tokens` → `refresh_tokens`

1. Hash del token; leer su fila **sin bloqueo** para conocer el `user_id`. Si no existe → `400 invalid-reset-link`.
2. `SELECT ... FOR UPDATE` de `users` (lectura fresca, **sin** el filtro de "no bloqueado" que usa Mi perfil:
   restablecer con la cuenta bloqueada es válido).
3. `SELECT ... FOR UPDATE` de la fila del token y re-chequear **vigencia** y que el usuario **no** sea administrador
   de plataforma. Si falla → `400 invalid-reset-link`, sin cambiar nada.
4. Validar la contraseña nueva (`@Size(min = 8)`, como el registro). Un `400` aquí no consume el enlace.
5. Guardar el hash nuevo. Si `locked_until` es futuro, **se conserva** el bloqueo y su contador; si no, contador a
   cero y `locked_until` nulo.
6. Marcar el token como usado y anular los demás vigentes de la cuenta.
7. Borrar **todos** los refresh tokens del usuario. No se emite sesión nueva.
8. Calcular `outcome`: `LOCKED` si el bloqueo sigue; si no, `ACCESS_DISABLED` si `UserStatus` no es `ACTIVE` o no hay
   ninguna membresía activa en una organización activa (fijando la identidad antes de leer `memberships`); si no,
   `OK`.
9. `afterCommit` → aviso de cambio (D6).

El orden es el mismo en el pedido (`users` → `password_reset_tokens`), así que pedido y restablecimiento simultáneos
no forman ciclo. Leer el token sin bloqueo en el paso 1 no cuenta como adquisición.

**Carreras cubiertas:**
- **Dos restablecimientos con el mismo enlace:** el segundo espera en el paso 2 y en el 3 encuentra el token usado → `400`.
- **Login o renovación simultáneos:** el protocolo de Mi perfil ya los cubre. El login exige el hash verificado y
  la renovación toma `FOR SHARE` sobre `users`.
- **Pedido de un enlace nuevo mientras se restablece con el anterior:** serializados por `users`; el que llegue
  segundo ve el estado del primero.

Se prueba con hilos reales, un `ExecutorService` propio y el punto de espera inyectable de Mi perfil
(`AuthConcurrencyProbe`), que gana dos puntos nuevos: después de leer el token y después de bloquear `users`.

### D6 — Aviso de cambio de contraseña

- Se envía tras el commit de `POST /auth/password` (Mi perfil) y de `POST /auth/password-reset`, por el executor de D4.
- Contenido: que la contraseña de la cuenta cambió, fecha y hora en hora de Perú, y qué hacer si no fue el titular:
  pedir la recuperación y avisar al administrador de la clínica. Sin enlaces que cambien la contraseña, para no
  entrenar a abrir enlaces de correos de "tu cuenta cambió".
- Mi perfil no cambia de comportamiento visible ni de texto de requirement: el aviso se especifica como requirement
  propio, que nombra los dos caminos.

### D7 — Frontend: dos pantallas públicas en el módulo `auth`

- Rutas: `/auth/forgot-password` y `/auth/reset-password`, en `routes/paths.ts`, dentro del mismo layout del login.
- `LoginForm`: el `<button>` pasa a ser un `Link` a `/auth/forgot-password`; se le quita el `tabIndex={-1}` para
  que el teclado llegue.
- **Pedido** (`ForgotPasswordForm`): correo con el `emailSchema` del login. Tras enviar muestra siempre el mismo
  mensaje: "Si existe una cuenta con ese correo, te enviamos un enlace. Revisa tu bandeja y el spam. El enlace vale
  30 minutos".
- **Restablecer** (`ResetPasswordForm`):
  - lee `token` de la query **una vez**, lo guarda en estado y reemplaza la URL sin la query
    (`navigate(..., { replace: true })`), así no queda en el historial;
  - llama a `check`; si es inválido muestra "El enlace no es válido o ya venció" con un botón para pedir otro;
  - el formulario reutiliza `newPasswordSchema` con confirmación (el de Mi perfil, sin la contraseña actual);
  - al guardar, navega al login con un aviso en el estado de navegación según `outcome`:
    - `OK`: "Listo. Ya puedes iniciar sesión con tu contraseña nueva."
    - `LOCKED`: "Tu contraseña se cambió. Por los intentos fallidos, podrás iniciar sesión a las HH:MM."
    - `ACCESS_DISABLED`: "Tu contraseña se cambió, pero tu acceso está desactivado. Contacta al administrador de tu
      clínica."
- **Referencia:** `<meta name="referrer" content="no-referrer">` en `index.html`. La política por defecto del
  navegador ya enviaría solo el origen a otros sitios, pero lo hacemos explícito para la página que recibe el token.
- La sesión en memoria se limpia si existiera: restablecer cierra todas las sesiones, también la de este navegador.

### D8 — Administrador de plataforma: procedimiento operativo, sin código

`PlatformAdminBootstrapRunner` solo **crea** el administrador si no existe, así que no sirve para recuperar. Se
documenta en `docs/deployment.md` un procedimiento para el responsable:

1. generar localmente un hash BCrypt de la contraseña nueva;
2. con el rol dueño de Neon, `UPDATE users SET password_hash = ..., failed_login_attempts = 0, locked_until = NULL`
   para ese correo, y borrar sus refresh tokens.

No agrega superficie de ataque a la aplicación. Se revisa si algún día hay más de un administrador de plataforma
o 2FA.

## Risks / Trade-offs

- **Diferencia de tiempo residual** entre cuenta existente y no existente: unas consultas a la base, del orden de
  milisegundos. Medirla por red es posible solo con muchas muestras, y el límite por cuenta no frena a quien prueba
  correos distintos. Se acepta: es el mismo orden de lo que ya expone el registro por invitación. Se descarta
  rellenar con trabajo ficticio porque complica sin cerrar el hueco por completo.
- **Envíos perdidos**: si el proceso se reinicia con correos en cola, se pierden. El usuario vuelve a pedir; un
  envío perdido cuenta para el límite. Se acepta frente a una tabla de salida con reintentos (outbox), que pediría
  una tarea programada.
- **Seguimiento de clics de Brevo**: si está activo, Brevo reescribe el enlace del correo y el token pasa por sus
  servidores. Hay que verificar que el seguimiento de clics esté apagado para estos correos (tarea del
  responsable).
- **Filas viejas** de `password_reset_tokens` de cuentas que no vuelven a pedir: crecen poco y solo guardan hashes.
- **Correo comprometido**: quien controla la casilla controla la cuenta de clínica. Es inherente a la recuperación
  por correo. Lo mitigan el aviso de cambio (el titular se entera), el cierre de todas las sesiones y que la
  plataforma quede fuera.

## Migration Plan

1. Migración Flyway con `password_reset_tokens` (aditiva; los grants condicionales, como V26/V30).
2. Desplegar backend y frontend juntos: el enlace del login apunta a una ruta nueva que necesita los endpoints.
3. Antes de producción: confirmar `MAIL_ENABLED` y el SMTP en Render, y el seguimiento de clics de Brevo.
4. **Rollback:** la tabla es aditiva; revertir la app la deja sin uso. Flyway no se revierte.

## Open Questions

_Ninguna._ Las decisiones de producto las cerró el responsable en el explore del 2026-10-29.
