# Design

## Context

Motivación y alcance: ver `proposal.md`. Requirements: `specs/authentication/spec.md`.

Estado actual que condiciona el diseño:

- `users` tiene `email` (único), `full_name` y `password_hash`; teléfono y país se borraron en `V5`. No hace falta
  migración.
- La sesión son dos tokens: un **access JWT de 15 min**, sin estado (`app.security.jwt.access-ttl`), y un **refresh
  de 7 días** persistido por hash en `refresh_tokens` (`V3`), que viaja en una cookie HttpOnly con
  **`Path=/api/auth`** (`RefreshCookieFactory.cookiePath`). Solo los endpoints bajo `/auth` reciben esa cookie.
- `AuthService.refresh` rota el refresh: lo consume con un `DELETE` por hash y emite uno nuevo. `logout` lo borra.
  `RefreshTokenRepository.deleteByUser` ya existe.
- El bloqueo temporal del login (`update-authentication`) cuenta los fallos en `users.failed_login_attempts` con
  `LoginAttemptService.recordFailure`, en transacción propia (`REQUIRES_NEW`) para sobrevivir al rollback.
- Las rutas `/me/**` ya existen (`/me/professional-credential`); el `JwtAuthenticationFilter` rechaza el token de
  plataforma fuera de `/platform/**`, así que una sesión de plataforma llega como no autenticada.
- Frontend: la sesión en memoria (`useSessionStore`) guarda un `UserResponse` con `fullName`, que es lo que muestra
  el menú de la cuenta. Hoy el menú tiene una sola entrada, "Mi credencial profesional" (`/profile/credential`).

## Goals / Non-Goals

**Goals:** cambiar la contraseña sin dejar viva ninguna otra sesión, ni siquiera por una renovación simultánea, y sin
que una sesión robada sirva para adivinar la contraseña actual.

**Non-Goals:** cortar al instante el access JWT de las otras sesiones (ver D2); verificar la fortaleza de la
contraseña más allá del mínimo actual.

## Decisions

### D1 — Dos recursos: el perfil en `/me/profile` y la contraseña en `/auth/password`

- `GET /me/profile` → `{ fullName, email }`. `PATCH /me/profile` con `{ fullName }` → el perfil actualizado. El
  usuario sale **siempre** de la sesión, nunca de un parámetro: no hay `id` en la ruta ni en el cuerpo. El DTO no
  tiene `email`, así que un correo en el cuerpo se ignora (Spring Boot no falla con propiedades desconocidas).
- `POST /auth/password` con `{ currentPassword, newPassword }` → `AuthResponse` (access nuevo y usuario) más la
  cookie de refresco nueva, igual que `login` y `refresh`.

**Por qué la contraseña bajo `/auth`:** es una operación de **sesión** (cierra sesiones y emite tokens), como
`refresh` y `logout`, y bajo `/auth` llega la cookie actual, que también se borra. *Alternativa descartada*:
`PATCH /me/password`. Funcionaría (el `Set-Cookie` puede fijar cualquier `Path`), pero separa del resto de la
gestión de sesión justo la operación que más la toca.

### D2 — "Cerrar las otras sesiones" = borrar todos los refresh y emitir uno nuevo para esta

En la misma transacción: se cambia el hash, se borran **todos** los refresh del usuario (`deleteByUser`) y se emiten
tokens nuevos para la sesión actual. La sesión actual sigue abierta con un refresh recién emitido; las demás quedan
sin refresh y caen en su próxima renovación.

**La clínica de los tokens nuevos es la del access token**, no una cualquiera: `refresh` hoy toma la primera
membresía activa (`activeMembership(...).findFirst()`), y el esquema permite varias por usuario
(`ux_memberships_user_org`). El cambio carga la membresía de **esa** organización (del contexto de la petición) y
exige que siga activa; si no lo está, responde `401` sin cambiar nada. El rol sale de esa membresía, no del token
(un rol cambiado mientras tanto queda al día).

`deleteByUser` borra en todas las clínicas porque `refresh_tokens.user_id` es global (`V3`).

- *Alternativa descartada*: conservar solo el refresh de la cookie actual. Exige que la cookie llegue y deja vivo un
  token que pudo haberse filtrado junto con la contraseña. Emitir uno nuevo es más simple y más seguro.
- *Alternativa descartada*: una marca `password_changed_at` revisada en **cada petición** para invalidar también
  el access JWT de inmediato. Costaría una lectura de la base por petición en toda la API, por una ventana de
  15 min como máximo. El spec fija ese límite: el acceso vigente termina "a más tardar cuando venza su token".

### D3 — Ninguna sesión puede nacer ni renovarse durante el cambio: bloqueo de la fila del usuario

**Carreras** (las dos terminan con una sesión viva tras el cierre):

- **Renovación:** `refresh` borra su token y **después** inserta el nuevo. Si el `deleteByUser` del cambio cae
  entre esos dos pasos, el token nuevo se inserta después.
- **Login:** `login` verifica la contraseña con BCrypt (~100 ms) sin bloqueo y después emite el refresh. Si el
  cambio commitea en el medio, el login emite una sesión con la contraseña **anterior** y sobrevive al cierre.

**Restricción:** no se puede tener la fila bloqueada al llamar a `LoginAttemptService.recordFailure`. Corre en
`REQUIRES_NEW` y actualiza la **misma** fila: esperaría a la transacción externa, que espera a la interna.
Postgres no lo detecta como deadlock (el ciclo pasa por el hilo de Java): se cuelga hasta un timeout con la
conexión tomada. Por eso **se verifica sin bloqueo y se bloquea después**, re-chequeando el hash bajo el bloqueo.

**Protocolo** (todos los bloqueos de fila se adquieren en orden `users` → `refresh_tokens`, sin ciclos; `refresh`
hace antes una lectura **no bloqueante** de su token, que no cuenta):

1. **Cambio de contraseña:**
   1. leer el usuario **sin bloqueo**; si está bloqueado → `429` (D4);
   2. BCrypt contra la actual; si falla → `recordFailure` (no hay bloqueo tomado) y `400`;
   3. `SELECT ... FOR UPDATE` de la fila como **lectura fresca**: consulta explícita del repositorio con
      `PESSIMISTIC_WRITE` que relee la base, nunca la entidad `User` ya cargada en el paso 1.1 (el contexto de
      persistencia la devolvería sin releer y el re-chequeo usaría un hash viejo). Si `password_hash` ya **no es**
      el verificado (otro cambio ganó) →
      `400 current-password-incorrect` **sin** contar el fallo (la contraseña era correcta al verificarla); si
      `locked_until` es futuro (otra petición bloqueó en el medio) → `429`;
   4. hash nuevo, contador a cero, `deleteByUser`, tokens nuevos (D2).
2. **Login:** el `UPDATE` condicional que ya resetea el contador (`resetLoginAttemptsIfNotLocked`, D13 de
   `update-authentication`) pasa a exigir también **`password_hash = <el verificado>`**. Ese `UPDATE` toma el
   bloqueo de la fila hasta el commit, y en `READ COMMITTED` Postgres re-evalúa el `WHERE` sobre la versión nueva si
   tuvo que esperar. Si el cambio ganó → 0 filas → `401` genérico, sin emitir tokens. Si el login ganó, el cambio
   espera a que commitee y su `deleteByUser` borra el refresh recién emitido. `recordFailure` del login no cambia:
   corre antes de tomar ningún bloqueo.
3. **Renovación:** `SELECT ... FOR SHARE` de la fila **antes** de consumir su token. Si el cambio va primero, la
   renovación espera, encuentra su token borrado y responde `401`; si va primero la renovación, su token nuevo
   existe cuando el cambio borra todo.
4. **Logout:** sin cambios. Solo borra su propio refresh y no toca `users`, así que no puede cerrar un ciclo.

Dos cambios simultáneos con la misma contraseña actual: los dos verifican sin bloqueo, el primero en bloquear gana y
el segundo encuentra el hash cambiado en el paso 1.3 → `400`.

Se prueba con hilos reales y un `ExecutorService` propio (renovación contra cambio, login contra cambio, contraseña
incorrecta contra cambio, dos cambios), y se comprueba que los dos primeros **fallan sin el bloqueo**. El
intercalado se **fuerza con latches** (un punto de espera inyectable solo en tests, p. ej. entre la verificación y
`issueTokens` del login, o entre el consumo y la inserción del refresh), no con el azar del timing: sin eso, la
carrera antigua podría no reproducirse y el test "fallaría sin el bloqueo" solo a veces.

### D4 — Contraseña actual incorrecta: cuenta para el bloqueo del login

Una contraseña actual incorrecta llama a `LoginAttemptService.recordFailure`, que sobrevive al rollback de la
petición. Con la cuenta bloqueada (`locked_until` futuro), el cambio se rechaza **antes** de comparar la contraseña.
Un cambio exitoso deja `failed_login_attempts = 0` **y** `locked_until = NULL`, como un login exitoso, también
cuando quedaba un bloqueo ya vencido.

- Contraseña actual incorrecta → `400` con tipo propio (`/errors/current-password-incorrect`) y `detail` en español.
  Es un usuario ya autenticado: no hay enumeración que proteger, y el mensaje genérico del login confundiría.
- Cuenta bloqueada → `429` (`/errors/account-temporarily-locked`), con un mensaje que no dice cuánto falta.
- Nueva igual a la actual → `400` (`/errors/new-password-same-as-current`).

*Trade-off aceptado*: quien tenga una sesión robada puede **bloquear** el login del dueño durante el período de
bloqueo. Es el mismo riesgo que ya existe probando contraseñas en el login, y es preferible a dejarle adivinar la
contraseña sin límite.

### D5 — Nombre: mismas reglas que el registro, y la sesión en memoria se actualiza

- Validación igual en el servidor y en Zod: 2–120 caracteres después de quitar los espacios de los extremos; se
  guarda sin esos espacios. Es el rango del registro (`RegisterRequest`), un poco más estricto: el registro hoy
  cuenta los espacios. No se toca el registro en este change.
- No hace falta emitir un access nuevo: el JWT no lleva el nombre. Tras guardar, el frontend actualiza el `user` de
  `useSessionStore` con el `fullName` devuelto, y el menú de la cuenta lo muestra sin recargar.
- La foto del firmante (`update-staff-professional-credential`) ya copia el nombre: lo firmado no cambia.

### D6 — Frontend: una página con tres secciones, en el módulo `auth`

- Ruta `PATHS.PROFILE` = `/profile`, sin restricción de rol. Screen de una línea (`docs/frontend.md` §1).
- `modules/auth/components/`: `ProfilePage` (compone las secciones), `ProfileDetailsForm`, `ChangePasswordForm`.
  Schemas Zod en `modules/auth/schemas/profile.ts`; la regla de la contraseña nueva (mínimo 8) se comparte con
  `registerSchema` en vez de copiarse. Hooks de React Query en `modules/auth/hooks/`.
- La credencial se **reutiliza** desde `staff` (`ProfessionalCredentialPage` o su contenido) como tercera sección,
  con el ancla `#credencial`.
- Secciones apiladas, sin pestañas: son tres bloques cortos, y una pestaña escondería el aviso de la credencial
  vencida.
- `/profile/credential` redirige a `/profile#credencial`, para no romper enlaces guardados. `SigningCapacityNotice`
  enlaza directo al ancla. El menú de la cuenta pasa a tener una sola entrada, "Mi perfil".
- La ayuda del campo "Habilitado hasta" pasa a decir que es la fecha de vigencia de la constancia de habilitación del
  COP.
- Al cambiar la contraseña, la respuesta trae un access nuevo: se guarda con `setSession`. Se limpian los campos y
  se avisa con un toast que las otras sesiones se cerraron.

## Risks / Trade-offs

- [Las otras sesiones conservan hasta 15 min de acceso] → Límite explícito del spec; el refresh ya no existe, así que
  no pueden extenderlo. Si alguna vez hace falta el corte instantáneo, es D2 alternativa 2.
- [Bloqueo del login provocado desde una sesión robada] → Trade-off aceptado en D4; el bloqueo es temporal.
- [`FOR SHARE` en cada `refresh` agrega una espera] → Solo contra un cambio de contraseña simultáneo del mismo
  usuario, que es raro; el resto de las renovaciones no compiten.
- [Tocar `login` y `refresh`, que ya funcionan en producción] → Cambio mínimo: una condición más en un `UPDATE`
  existente y un `FOR SHARE`. Los ITs de login, bloqueo y renovación existentes deben seguir en verde sin tocarlos.
- [Un bloqueo tomado mientras se llama a `REQUIRES_NEW` cuelga la petición] → D3 lo prohíbe por protocolo; el IT de
  contraseña incorrecta concurrente lo detecta si alguien lo reintroduce.
- [Mover la credencial de ruta rompe enlaces] → Redirección desde `/profile/credential` (D6).

## Migration Plan

Sin migración de base. Despliegue normal: backend y frontend juntos. El contrato cambia solo por adición. Rollback:
revertir el despliegue; los refresh borrados por un cambio de contraseña no se recuperan, y no hace falta.
