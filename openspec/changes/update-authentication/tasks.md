## 1. Modelo de datos (backend)

- [ ] 1.1 Migración Flyway `db/migration/V<n>__user_login_lockout.sql`: añadir a `users` las columnas `failed_login_attempts INT NOT NULL DEFAULT 0` y `locked_until TIMESTAMPTZ NULL` (aditivas, sin backfill).
- [ ] 1.2 `persistence/entity/User.java`: mapear ambas columnas (`failedLoginAttempts`, `lockedUntil` como `Instant`).

## 2. Configuración y tiempo (backend)

- [ ] 2.1 `@ConfigurationProperties @Validated` para `auth.lockout.max-attempts` (int, default 5, `@Min(1)`) y `auth.lockout.window` (`Duration` ISO-8601, default `PT15M`, validación `> 0`); el arranque falla si son inválidos. (D6)
- [ ] 2.2 Añadir las claves y sus equivalentes de entorno (`AUTH_LOCKOUT_MAX_ATTEMPTS`, `AUTH_LOCKOUT_WINDOW`) a `secrets.properties.example`.
- [ ] 2.3 Exponer un bean `Clock` (`Clock.systemUTC()`) e inyectarlo donde se calcula/compara `lockedUntil`, en vez de `Instant.now()` directo. (D11)

## 3. Persistencia del contador (backend)

- [ ] 3.1 Añadir al `UserRepository` la sentencia atómica `@Modifying` de fallo (incrementa, reinicia a 1 si la ventana ya expiró, y fija `locked_until` al alcanzar el umbral — todo en un solo `UPDATE`). (D7, D3)
- [ ] 3.2 Añadir la sentencia atómica de reset por éxito (`failed_login_attempts = 0`, `locked_until = NULL`).
- [ ] 3.3 Colaborador `LoginAttemptService` con `@Transactional(propagation = REQUIRES_NEW)` que ejecuta 3.1, de modo que el incremento **commitea aunque `login` lance después el 401**. (D7, resuelve el rollback de `@Transactional` + `RuntimeException`)
- [ ] 3.4 El reset de 3.2 se invoca **desde la transacción de `login`**, NO desde el colaborador `REQUIRES_NEW`: acertar la contraseña no cierra el login (`activeMembership` e `issueTokens` aún pueden fallar) y el reset debe revertirse con ellos. (D7 — asimetría deliberada con 3.3)

## 4. Lógica de login (backend)

- [ ] 4.1 `AuthService.login`: antes de verificar la contraseña, si `lockedUntil` está en el futuro, rechazar con `InvalidCredentialsException` sin tocar el contador (no incrementa ni prolonga la ventana). (D2, D8)
- [ ] 4.2 Si `lockedUntil` es null o ya pasó, tratar la identidad como no bloqueada y verificar la contraseña.
- [ ] 4.3 En fallo de contraseña de un correo registrado: invocar el colaborador de 3.3. (D8: solo el fallo de contraseña cuenta)
- [ ] 4.4 En rechazo por **estado** (identidad/membresía/organización no activa) con contraseña correcta: **no** tocar el contador. (D8)
- [ ] 4.5 En login exitoso: ejecutar el reset (3.2). (D3)
- [ ] 4.6 Confirmar que un correo inexistente no crea ni altera estado. (D5)
- [ ] 4.7 Confirmar que `refresh` **no** consulta el estado de bloqueo (el bloqueo no revoca sesiones vivas). (D10)
- [ ] 4.8 **No tocar `UserStatus`**: el lockout automático no escribe `LOCKED` ni ningún otro estado en la identidad. El gate por estado (`activeMembership`) y el gate por `locked_until` quedan independientes y ambos deben pasar. (D12)

## 5. Tests backend (uno por Scenario del delta + las garantías frágiles)

- [ ] 5.1 Bloqueo tras alcanzar el umbral de intentos fallidos.
- [ ] 5.2 **Persistencia pese al rechazo**: tras un login rechazado con 401, el contador quedó incrementado en la BD (test de integración real, no mock — es la garantía que el rollback rompería).
- [ ] 5.3 **Concurrencia**: N intentos fallidos simultáneos sobre la misma identidad cuentan N (sin lost update) y disparan el bloqueo.
- [ ] 5.4 Cuenta bloqueada con credenciales correctas → rechazo genérico, sin sesión.
- [ ] 5.5 Insistir durante el bloqueo no incrementa el contador ni retrasa la expiración.
- [ ] 5.6 Un rechazo por estado (con contraseña correcta) no incrementa el contador.
- [ ] 5.7 **El reset no sobrevive a un login fallido**: una identidad `DISABLED`/`LOCKED` con contador acumulado que envía la contraseña **correcta** es rechazada por estado y su contador **sigue intacto** (el reset se revirtió con la transacción). (D7 3.4)
- [ ] 5.8 Expiración + credenciales correctas → sesión establecida y contador reiniciado (avanzando el `Clock`, sin dormir).
- [ ] 5.9 Expiración + contraseña incorrecta → no re-bloquea de inmediato (el conteo parte de cero).
- [ ] 5.10 Correo inexistente: intentos repetidos no crean estado.
- [ ] 5.11 `AuthControllerIT`: el status/cuerpo/cabeceras de un rechazo por bloqueo son idénticos a los de credenciales inválidas (anti-enumeración a nivel de respuesta).
- [ ] 5.12 Bloquear no invalida una sesión vigente: el `refresh` de esa sesión sigue funcionando. (D10)
- [ ] 5.13 Expirar la ventana no reactiva una identidad `LOCKED`/`DISABLED`: sigue rechazando con el genérico y su `status` queda intacto. (D12)
- [ ] 5.14 Configuración inválida (`max-attempts = 0`, `window` no positiva) impide el arranque.
- [ ] 5.15 Migración + mapeo: la columna existe con su default y la entidad la lee/escribe correctamente.

## 6. Cierre (al archivar)

- [ ] 6.1 `docs/vision.md`: marcar el hueco "Sin bloqueo por intentos fallidos de login" como cerrado y enlazar el `spec.md` de `authentication`.
- [ ] 6.2 `docs/domain.md`: actualizar la identidad si los campos de bloqueo son relevantes para el modelo de dominio (si no, dejar nota de por qué no).

## 7. Frontend

- [ ] 7.1 Sin cambios: el contrato y el mensaje al usuario no cambian. (Confirmar que no hace falta tocar `LoginForm`/copy.)
