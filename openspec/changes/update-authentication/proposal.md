## Why

Hoy el login (`AuthService.login`) no lleva ningún control de intentos fallidos: un atacante puede
probar contraseñas contra un correo válido de forma ilimitada (fuerza bruta / credential stuffing).
La capacidad `authentication` ya está construida y protege bien otras superficies (email único con
`409`, rotación atómica de refresh token), pero este hueco quedó anotado como pendiente de seguridad
en `docs/vision.md`. Este change lo cierra sin infraestructura nueva.

## What Changes

- Se añade **bloqueo temporal de cuenta por intentos de login fallidos**: tras N fallos consecutivos
  contra un correo registrado, la identidad queda bloqueada durante una ventana de tiempo y el login
  se rechaza aunque las credenciales sean correctas.
- El estado del contador vive en la **identidad** (`users`): contador de fallos consecutivos + momento
  hasta el que la cuenta está bloqueada. Persistido en la BD → funciona entre réplicas **sin Redis**.
- El bloqueo **no cambia el mensaje** que ve el usuario: sigue siendo el genérico
  "Correo electrónico o contraseña incorrectos", para no romper la regla anti-enumeración vigente del
  login (no revelar si el correo existe ni por qué falló). El contador **solo** se aplica a correos
  registrados; un correo inexistente no crea estado.
- Solo cuenta el **fallo de contraseña**: un rechazo por estado (identidad/membresía/organización no
  activa) con contraseña correcta no incrementa, y seguir intentando mientras la cuenta ya está
  bloqueada no prolonga la ventana.
- Un login exitoso **reinicia** el contador. El bloqueo **expira solo** al pasar la ventana (sin
  desbloqueo manual en este alcance), y al expirar el contador **también se reinicia** — un fallo
  posterior no re-bloquea de inmediato.
- El conteo se persiste con un **`UPDATE` atómico en transacción propia**, para que sobreviva al
  rechazo del intento (si no, el rollback del 401 borraría el incremento y el bloqueo nunca se
  activaría) y para que varios intentos concurrentes no se pisen.
- Umbral y duración de bloqueo son **configuración externalizada y validada**:
  `auth.lockout.max-attempts` (default **5**, `>= 1`) y `auth.lockout.window` (default **`PT15M`**,
  `> 0`), con equivalentes de entorno; el arranque falla si son inválidos.
- El bloqueo frena **logins nuevos**; **no** invalida sesiones ya emitidas ni afecta al `refresh` de
  una sesión vigente.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `authentication`: se modifica el requirement **Inicio de sesión con credenciales** para incorporar
  el bloqueo por intentos fallidos y sus escenarios (fallos consecutivos → bloqueo, bloqueo con
  credenciales correctas, reinicio tras éxito, expiración de la ventana). Mismo actor: identidad/sesión.

## Non-goals

- **Rate limiting por IP / global**: es el gap que `docs/architecture.md` marca como dependiente de
  Redis (memoria no sirve entre réplicas). Fuera de alcance; sigue diferido.
- **Desbloqueo manual** (por un ADMIN) o **auto-servicio por email** ("desbloquea tu cuenta"): no en
  este change; el bloqueo se levanta solo al expirar la ventana.
- **CAPTCHA, MFA, verificación de correo**: superficies de seguridad distintas, no forman parte de este
  change.
- **Notificar al usuario** de un bloqueo (email de aviso): fuera de alcance.
- **Revocar sesiones vivas al bloquear**: el `refresh` no consulta el estado de bloqueo. El lockout
  defiende el path de credenciales, no es un mecanismo de revocación de sesión.
- **Login de tiempo constante**: la garantía anti-enumeración cubre la **respuesta** (status, cuerpo,
  cabeceras), no la latencia. Igualar tiempos exigiría verificar contra un hash señuelo en los paths
  que hoy saltan BCrypt; queda descartado por diseño (la garantía a nivel de respuesta se considera suficiente).

## Impact

- **Backend**:
  - `persistence/entity/User.java`: dos columnas nuevas (contador de fallos, bloqueado-hasta) +
    migración Flyway `db/migration`.
  - `service/auth/AuthService.java` (`login`): comprobar bloqueo antes de verificar contraseña;
    incrementar/bloquear en fallo; reiniciar en éxito. Se reutiliza `InvalidCredentialsException`
    (mensaje genérico intacto), sin excepción nueva visible al cliente.
  - Colaborador nuevo (`LoginAttemptService`) con transacción propia + sentencia `@Modifying` atómica
    en `UserRepository`, para que el conteo sobreviva al 401 y sea correcto bajo concurrencia.
  - Configuración: umbral + duración externalizados y validados (`secrets.properties.example` + env);
    bean `Clock` inyectable para que los tests de expiración no dependan del reloj real.
- **Frontend**: sin cambios de contrato ni de UI (el mensaje al usuario no cambia).
- **Tests**: nuevos escenarios en `AuthServiceTest` / `AuthControllerIT` (uno por Scenario del delta).
- **Docs**: al archivar, marcar el hueco como cerrado en `docs/vision.md`; `docs/domain.md` si el
  modelo de identidad gana campos relevantes de dominio.
