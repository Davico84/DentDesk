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

**Non-Goals:** los del proposal (2FA, límite por IP entre varias instancias, cola persistida, desbloqueo, recuperación
de plataforma desde la app, cambio de correo, reglas de contraseña nuevas, correo con marca de la clínica).

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

### D3 — Pedido: la petición no mira la cuenta; todo se resuelve fuera de ella

Para que ni la respuesta ni su tiempo dependan de la cuenta, **la petición no la busca**:

1. validar el formato del correo (`400` si es inválido: depende solo de lo enviado, no de la cuenta);
2. normalizarlo (`trim` + minúsculas);
3. admitirlo (D10): si la IP superó su límite, o el correo ya tiene un pedido pendiente, no se encola;
4. si se admitió, entregarlo al executor de recuperación (D4);
5. responder `202`, se haya encolado o no.

Es el mismo trabajo para cualquier correo, exista o no: la admisión mira la IP y los pedidos en memoria, nunca la
cuenta. Si la cola está llena, la tarea se descarta con un log y la respuesta es la misma.

En el hilo del executor, en una transacción propia:

1. buscar el usuario; si no existe → fin;
2. `SELECT ... FOR UPDATE` de su fila de `users` y, **bajo el bloqueo**, comprobar que esté `ACTIVE` y que no sea
   administrador de plataforma. Bajo el bloqueo, una desactivación (que actualiza la fila) o una promoción a
   plataforma (cuyo `INSERT` en `platform_admins` toma `FOR KEY SHARE` sobre `users`) no pueden colarse entre la
   comprobación y la emisión. El bloqueo también serializa los pedidos simultáneos de la misma cuenta, para que
   cuatro a la vez no pasen los cuatro el conteo;
3. contar los enlaces de las últimas 24 h; si hay 3 → fin;
4. anular los vigentes (`revoked_at`), borrar los de más de 24 h, insertar el nuevo; commit;
5. **después del commit**, en el mismo hilo, enviar el correo: si la transacción falla, no llega un enlace que no
   existe en la base.

**Alternativa descartada:** resolver la cuenta en la petición y solo enviar el correo afuera. Deja una diferencia de
tiempo de varias consultas entre una cuenta activa dentro del límite y el resto. Igualarla con trabajo ficticio es
frágil, y el spec no admite diferencias apreciables.

### D4 — Dos executors propios sin contexto de tenant, no `@Async`

- Dos beans `ExecutorService` dedicados, cada uno con su cola acotada:
  - `recoveryExecutor` (2 hilos, cola de 100): los pedidos de recuperación (D3);
  - `accountNoticeExecutor` (1 hilo, cola de 50): los avisos de cambio de contraseña (D6).
- **Por qué separados:** cualquiera puede generar pedidos de recuperación, pero un aviso solo nace de un cambio que ya
  exigió la contraseña o un enlace vigente. Con una sola cola, una inundación de pedidos retrasaría o descartaría la
  alarma de seguridad; separadas, la inundación no llega a los avisos.
- **Por qué no choca con el tripwire de `@Async`:** el tripwire existe porque un hilo nuevo no hereda el contexto de
  tenant de la petición. Estas tareas **no lo necesitan**. Solo tocan `users`, `platform_admins` y
  `password_reset_tokens`, que no tienen RLS, y nunca fijan ni leen `IdentityContext`, `OrganizationContext` ni
  `tenantSession`. Sin contexto, `TenantAwareTransactionManager` no fija nada (su Javadoc lo prevé para los flujos de
  auth previos a la sesión), y si alguna tarea tocara una tabla con RLS, fallaría cerrado.
- Cada tarea **limpia los `ThreadLocal` de tenant al empezar**, como defensa por si un hilo del pool los heredara.
- Nuevas reglas de ArchUnit, que vigilan lo que el tripwire protege:
  - los workers de recuperación y de aviso y el paquete `infra.mail` no dependen de `IdentityContext`, `OrganizationContext` ni
    de la sesión de tenant;
  - `infra.mail` no depende de `persistence`.
- Un fallo de entrega se registra **sin** el token, el enlace ni datos de la contraseña.
- En el apagado, los executors terminan con un plazo corto (`shutdown` + `awaitTermination`). Las tareas en cola se
  pierden (ver Riesgos).
- Nueva interfaz `AccountMailer` (enlace de recuperación y aviso de cambio), separada de `InvitationMailer`: la
  invitación **debe** fallar la petición si no se envía (add-platform-admin D6); estos correos no. Sin correo
  configurado, la variante deshabilitada registra y descarta: en ese entorno la recuperación no funciona, pero la
  respuesta sigue siendo la misma.
- En los tests, un `AccountMailer` que registra lo enviado. Los ITs de comportamiento esperan a que los executors
  terminen su cola antes de afirmar. El test de anti-enumeración **bloquea el executor de recuperación con un latch**
  y comprueba que la petición responde igual para todos los casos sin que el worker haya empezado (D9).

### D5 — Restablecer: orden de bloqueo `users` → `password_reset_tokens` → `refresh_tokens`

1. Hash del token; leer su fila **sin bloqueo** para conocer el `user_id`. Si no existe → `400 invalid-reset-link`.
2. `SELECT ... FOR UPDATE` de `users` (lectura fresca, **sin** el filtro de "no bloqueado" que usa Mi perfil:
   restablecer con la cuenta bloqueada es válido).
3. `SELECT ... FOR UPDATE` de la fila del token y re-chequear **vigencia** y que el usuario **no** sea administrador
   de plataforma. Si falla → `400 invalid-reset-link`, sin cambiar nada.
4. Validar la contraseña nueva (`@Size(min = 8)`, como el registro). Un `400` aquí no consume el enlace.
5. Guardar el hash nuevo con un `UPDATE` propio (**no** el `changePassword` de Mi perfil, que siempre pone el
   bloqueo a cero). Si `locked_until` es futuro, **se conserva** el bloqueo y su contador; si no, contador a cero y
   `locked_until` nulo.
6. Marcar el token como usado y anular los demás vigentes de la cuenta.
7. Borrar **todos** los refresh tokens del usuario. No se emite sesión nueva.
8. Calcular `outcome`: `LOCKED` si el bloqueo sigue; si no, `ACCESS_DISABLED` si `UserStatus` no es `ACTIVE` o si
   no hay ninguna membresía `ACTIVE` **en una organización `ACTIVE`** (una membresía activa en una clínica
   suspendida no da acceso, igual que en el login); si no, `OK`. Para leer `memberships` se fija la identidad con
   `bindIdentity`, como el login: esto corre en el hilo de la petición, no en el executor.
9. `afterCommit` → entregar el aviso de cambio al `accountNoticeExecutor` (D6).

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

- Se envía tras el commit de `POST /auth/password` (Mi perfil) y de `POST /auth/password-reset`, por el
  `accountNoticeExecutor` de D4, nunca por la cola de los pedidos de recuperación.
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
  30 minutos. Si no te llega en unos minutos, vuelve a pedirlo." La última frase cubre los pedidos que no se
  atienden (límite, repetido, cola llena o reinicio) sin revelar cuál pasó.
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

### D9 — Cómo se prueba la anti-enumeración

Un IT con el executor de recuperación **detenido por un latch** pide la recuperación con seis correos: activo dentro
del límite, inexistente, identidad desactivada, administrador de plataforma, plataforma que también es miembro y
cuenta que ya superó el límite. Comprueba:

- **Respuestas idénticas:** status y cuerpo iguales, y los headers iguales **uno por uno**, salvo `Date`, que es el
  único que se excluye (por nombre, no por "comparables").
- **La petición no toca la base:** que la respuesta llegue antes que el worker no alcanza, porque una implementación
  que consultara la cuenta antes de encolar también lo cumpliría. Un contador global (las estadísticas de Hibernate)
  tampoco: Hikari, Actuator y otros hilos lo mueven. Se prueba **por hilo**: en el IT, un `BeanPostProcessor` de test
  envuelve el `DataSource` y registra qué hilo pide cada conexión; con MockMvc la petición corre en el hilo del test,
  y ese hilo no pide ninguna conexión durante los pedidos. Además, un test unitario del servicio de pedido con
  `verifyNoInteractions` sobre los repositorios.
- **Las variantes de no atender:** un pedido rechazado por el límite de IP, uno repetido con otro pendiente y uno
  con la cola llena (executor detenido y cola completa) responden exactamente igual que los seis anteriores.

Todo sin medir tiempos (que en CI serían inestables). Después se libera el latch y se comprueba que solo la cuenta
activa dentro del límite recibió el correo, una sola vez.

### D10 — Admisión del pedido: límite por IP y un solo pendiente por correo, en memoria

Corre en la petición, después de validar el formato, y **no consulta la base**:

- **Límite por IP, ventana fija:** 10 pedidos por IP del cliente en una ventana de 1 hora que empieza con el primer
  pedido y **no se corre** con los siguientes. El valor del `Cache` de Caffeine (ya está en el proyecto) es
  `(inicioDeVentana, cuenta)`:
  - el incremento es **atómico**, con `cache.asMap().compute(ip, ...)`, nunca con `get` + incremento + `put`, que
    deja pasar más de 10 pedidos simultáneos. Dentro del `compute`, si la ventana venció se reinicia en `(ahora, 1)`;
    si no, se suma uno. Se admite si la cuenta resultante es 10 o menos;
  - el vencimiento usa un `Expiry` propio: `expireAfterCreate` de 1 hora y `expireAfterUpdate` que **devuelve la
    duración restante**, para que una actualización no lo extienda (con `expireAfterWrite` la ventana se correría
    hasta el último pedido). Caffeine usa un `Ticker` y la ventana un `Clock`, inyectables para los tests;
  - tamaño máximo de 10 000 IPs. Si un ataque desde muchísimas IPs lo desborda, Caffeine descarta contadores y
    alguna IP recupera cupo. Se acepta, porque la memoria queda acotada y el peor caso es la cola llena de D4,
    pero queda **medido** (ver Métricas).
- **IP del cliente: una sola fuente, `CF-Connecting-IP`.** Render atiende detrás de Cloudflare, que escribe esa
  cabecera con la IP que se conectó y **pisa** la que mande el cliente. `X-Forwarded-For` se descarta como fuente:
  - el cliente controla sus valores a la izquierda;
  - con `forward-headers-strategy: framework`, `getRemoteAddr()` devuelve justamente el primero;
  - contar saltos desde la derecha exige conocer la cadena exacta de proxies de Render, que no está garantizada;
  - además, `ForwardedHeaderFilter` saca esas cabeceras y Boot lo registra con `HIGHEST_PRECEDENCE`, así que un
    filtro propio no puede asegurar correr antes.

  `CF-Connecting-IP` no es una cabecera `X-Forwarded-*`, así que ese filtro no la toca, y la estrategia
  `framework` (que sirve para `X-Forwarded-Proto` y las cookies `Secure`) queda como está. El nombre de la cabecera
  se configura (`app.password-recovery.client-ip-header`): en producción, `CF-Connecting-IP`; vacío en local y en
  tests, donde se usa la dirección de la conexión. Si en producción la cabecera falta, **no** se cae a
  `getRemoteAddr()`, que sería falsificable: todos esos pedidos comparten un único cupo. Que Render la entregue, y que
  la que mande el cliente se pise, es **precondición del despliegue** (Migration Plan 4). Si no se cumple, se revisa
  esta decisión antes de desplegar.
- **Un solo pendiente por correo:** un conjunto concurrente de correos normalizados en cola o en proceso. Si el correo
  ya está, no se encola. Evita que un mismo correo ocupe la cola y que dos pedidos simultáneos generen dos enlaces.
  Su tamaño está acotado por la cola. Para que un correo **nunca quede retenido**:
  - el `recoveryExecutor` usa explícitamente `AbortPolicy`: el rechazo lanza `RejectedExecutionException`, nunca un
    descarte silencioso. Esto cubre la cola llena y el pedido que llega durante el apagado;
  - la entrega se hace en un `try`: si `execute` lanza cualquier cosa, el correo se quita del conjunto antes de
    responder;
  - el worker lo quita en un `finally` que rodea **toda** la tarea, así que corre también si la tarea lanza un
    `Error`.
- **Métricas:** contadores de Micrometer (Actuator ya está) por motivo de no atender el pedido: `ip`, `duplicate` y
  `queue_full`, más un contador de desalojos del `Cache` (`recordStats`). Un rechazo por cola llena se registra como
  `WARN`. No hay sistema de alertas en el proyecto; los contadores dejan el dato listo para cuando lo haya.
- La decisión depende solo de la IP y de los pedidos anteriores de ese correo, nunca de que la cuenta exista, y el
  status, los headers y el cuerpo son los mismos en todos los casos.
- **Tiempo de un correo repetido:** encolar y no encolar no tardan lo mismo, aunque la diferencia es de
  microsegundos en memoria, muy por debajo del ruido de red. Ese canal diría, a lo sumo, que **alguien** pidió hace
  instantes una recuperación para ese correo, y no que la cuenta exista: el conjunto se llena antes de buscarla, igual
  para correos registrados que inventados. **Se acepta** este canal, y no se iguala con trabajo ficticio.
- **Varias instancias:** con más de una, cada una tendría su propio contador y su propio conjunto, y el límite real se
  multiplica por la cantidad de instancias. Ese día se mueve a Redis (Render Key Value); hasta entonces es una sola.

**Alternativas descartadas:**
- **Cola persistida en la base**, con reintentos: sobrevive reinicios, pero no frena una inundación (con muchas IPs y
  correos inventados, la tabla crece sin tope, y ponerle tope vuelve a descartar), y suma estados, reclamo con
  `SKIP LOCKED`, reintentos con temporizador propio y limpieza. Un pedido perdido se resuelve volviendo a pedir.
- **CAPTCHA (Turnstile)**: dependencia externa y fricción, y no evita perder pedidos. Queda como defensa adicional
  si el límite por IP no alcanza.

## Risks / Trade-offs

- **Cola llena por abuso distribuido**: el límite por IP (D10) frena a un origen solo, pero un ataque desde muchas
  IPs todavía puede llenar la cola de recuperación y hacer que se descarten pedidos legítimos durante ese rato. La
  respuesta no cambia, así que no se filtra nada; los avisos de cambio no se ven afectados (cola propia, D4); la
  pantalla le dice al usuario que vuelva a pedir si no le llega. Si pasa en la práctica, el siguiente paso es un
  CAPTCHA en el formulario.
- **IPs compartidas**: una clínica detrás de una sola IP comparte el cupo de 10 pedidos por hora. Alcanza para el
  uso normal (olvidos esporádicos); si no, se sube el número por configuración.
- **IP mal resuelta**: si Render dejara de entregar `CF-Connecting-IP`, todos los pedidos compartirían un único cupo:
  la recuperación se degrada, pero no se puede esquivar. Si alguien llegara al servicio sin pasar por Cloudflare,
  podría inventar la cabecera. Las dos cosas se verifican en el despliegue (Migration Plan 4).
- **Desalojo de contadores**: un ataque desde muchas IPs puede hacer que Caffeine descarte contadores y que alguna IP
  recupere cupo (D10). Queda medido por los contadores de desalojo y de `queue_full`.
- **Tiempo de un correo repetido**: canal aceptado. Dice, como mucho, que alguien pidió hace instantes la recuperación
  para ese correo, nunca que la cuenta exista (D10).
- **Varias instancias**: el límite en memoria deja de ser global (D10). Hoy hay una sola instancia.
- **Envíos perdidos**: si el proceso se reinicia con pedidos en cola, se pierden. El usuario vuelve a pedir. Un pedido
  perdido en la cola no cuenta para el límite; uno cuyo correo falla después de emitir el enlace, sí. Se acepta
  frente a una cola persistida con reintentos (D10, alternativas).
- **Seguimiento de clics de Brevo**: si está activo, Brevo reescribe el enlace del correo y el token pasa por sus
  servidores antes de llegar al navegador; `no-referrer` no lo evita. Es una **precondición bloqueante del
  despliegue**: el seguimiento de clics tiene que estar apagado para el correo de recuperación. Al aplicar se
  investiga si Brevo lo permite por mensaje (un header SMTP) para fijarlo en el adaptador; si no, se apaga en la
  cuenta y se verifica con un correo real que el enlace llega sin reescribir.
- **Filas viejas** de `password_reset_tokens` de cuentas que no vuelven a pedir: crecen poco y solo guardan hashes.
- **Correo comprometido**: quien controla la casilla controla la cuenta de clínica. Es inherente a la recuperación
  por correo. Lo mitigan el aviso de cambio (el titular se entera), el cierre de todas las sesiones y que la
  plataforma quede fuera.

## Migration Plan

1. Migración Flyway con `password_reset_tokens` (aditiva; los grants condicionales, como V26/V30).
2. Desplegar backend y frontend juntos: el enlace del login apunta a una ruta nueva que necesita los endpoints.
3. **Bloqueante antes de producción**: confirmar `MAIL_ENABLED` y el SMTP en Render, y comprobar con un correo de
   recuperación real que el enlace llega **sin reescribir** por el seguimiento de clics de Brevo. Sin esa
   comprobación no se despliega.
4. **Bloqueante antes de producción:** con pedidos reales a través de Render, comprobar tres cosas, sin dejar IPs en
   los logs permanentes:
   - que llega `CF-Connecting-IP` con la IP del cliente;
   - que un `CF-Connecting-IP` o un `X-Forwarded-For` mandados por el cliente no cambian la IP contada;
   - que el servicio no responde sin pasar por Cloudflare.

   Si falla alguna, se revisa D10 antes de desplegar.
5. **Rollback:** la tabla es aditiva; revertir la app la deja sin uso. Flyway no se revierte.

## Open Questions

_Ninguna._ Las decisiones de producto las cerró el responsable en el explore del 2026-10-29.
