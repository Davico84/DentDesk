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
tokens nuevos para la sesión actual, con la organización activa y el rol del access token que hizo la petición.
La sesión actual sigue abierta con un refresh recién emitido; las demás quedan sin refresh y caen en su próxima
renovación.

- *Alternativa descartada*: conservar solo el refresh de la cookie actual. Exige que la cookie llegue y deja vivo un
  token que pudo haberse filtrado junto con la contraseña. Emitir uno nuevo es más simple y más seguro.
- *Alternativa descartada*: una marca `password_changed_at` revisada en **cada petición** para invalidar también
  el access JWT de inmediato. Costaría una lectura de la base por petición en toda la API, por una ventana de
  15 min como máximo. El spec fija ese límite: el acceso vigente termina "a más tardar cuando venza su token".

### D3 — Una renovación simultánea no puede resucitar una sesión cerrada: bloqueo de la fila del usuario

**Carrera:** `refresh` borra su token y **después** inserta el nuevo. Si el cambio de contraseña hace su
`deleteByUser` entre esos dos pasos, el token nuevo se inserta después y **la otra sesión sobrevive**.

**Decisión:** el cambio de contraseña toma `FOR UPDATE` sobre la fila de `users`, y `refresh` toma `FOR SHARE` sobre
la misma fila **antes** de consumir su token. Quedan serializados:

- si la renovación va primero, su token nuevo existe cuando el cambio borra todo, y cae;
- si el cambio va primero, la renovación espera, encuentra su token borrado y responde `401`.

Dos cambios de contraseña simultáneos también quedan serializados por el `FOR UPDATE`; el segundo verifica la
contraseña actual contra el hash ya cambiado. Se prueba con hilos reales y un `ExecutorService` propio, y se
comprueba que el test **falla sin el bloqueo**.

### D4 — Contraseña actual incorrecta: cuenta para el bloqueo del login

Una contraseña actual incorrecta llama a `LoginAttemptService.recordFailure`, que sobrevive al rollback de la
petición. Con la cuenta bloqueada (`locked_until` futuro), el cambio se rechaza **antes** de comparar la contraseña.
Un cambio exitoso deja el contador en cero, como un login exitoso.

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
- [Mover la credencial de ruta rompe enlaces] → Redirección desde `/profile/credential` (D6).

## Migration Plan

Sin migración de base. Despliegue normal: backend y frontend juntos. El contrato cambia solo por adición. Rollback:
revertir el despliegue; los refresh borrados por un cambio de contraseña no se recuperan, y no hace falta.
