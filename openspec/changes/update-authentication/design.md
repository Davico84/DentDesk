## Context

`AuthService.login` (ver `modules/backend/.../service/auth/AuthService.java`) valida correo +
contraseña + estado (identidad/membresía/organización) y emite tokens, pero **no cuenta intentos
fallidos**. No hay throttling de ningún tipo en el path de login. `docs/architecture.md` ya anticipa
que el *rate limiting por IP en memoria* no serviría entre réplicas (necesitaría Redis) — pero el hueco
concreto anotado en `docs/vision.md` es más acotado: **bloqueo por cuenta tras N fallos**, que es
estado por identidad y se puede persistir en la BD sin infra nueva.

`AuthService` es `@Transactional` a nivel de clase y `login` rechaza con
`InvalidCredentialsException` (`RuntimeException`). Esto impone una restricción **no negociable** en el
diseño: cualquier escritura del contador hecha dentro de la transacción de `login` **se revierte** al
lanzar la excepción (rollback por defecto de Spring ante `RuntimeException`). La cuenta también corre
bajo réplicas concurrentes, así que el conteo debe ser correcto bajo concurrencia.

La capacidad exige mantener la regla anti-enumeración vigente: el login **nunca** revela cuál campo
falló ni el estado de la cuenta (`InvalidCredentialsException` → mensaje único genérico). El bloqueo
debe respetar eso a nivel de **respuesta** (ver D9 para el matiz de timing).

## Goals / Non-Goals

**Goals:**
- Frenar fuerza bruta / credential stuffing contra un correo concreto con bloqueo temporal por cuenta.
- Cero infraestructura nueva: estado persistido en la BD, correcto entre réplicas y bajo concurrencia.
- No degradar la postura anti-enumeración a nivel de respuesta (status/cuerpo/cabeceras idénticos).
- Umbral y ventana externalizados como configuración validada (patrón de config única del proyecto).

**Non-Goals:**
- Rate limiting por IP / global (el gap Redis de `architecture.md`) — sigue diferido.
- Desbloqueo manual por ADMIN o auto-servicio por email; notificación al usuario. Fuera de alcance.
- CAPTCHA, MFA, verificación de correo.
- **Invalidar sesiones ya emitidas** al bloquear (ver D10): el bloqueo frena logins nuevos, no revoca
  tokens vivos.
- **Constant-time login completo** (blindaje de timing por hash señuelo, ver D9): **descartado por
  diseño**, no diferido.

## Decisions

### D1 — Estado de bloqueo persistido en `users`, no en Redis ni en memoria
Se añaden dos columnas a la identidad: `failed_login_attempts` (int, `NOT NULL DEFAULT 0`) y
`locked_until` (`TIMESTAMPTZ NULL`). El bloqueo es **por cuenta**, no por IP, así que su estado es
naturalmente por-identidad y cabe en la fila del usuario.
- *Por qué:* es transaccional y compartido por todas las réplicas sin componentes nuevos —
  exactamente lo que `architecture.md` dice que le falta al enfoque en memoria.
- *Alternativa descartada — Redis / bucket4j:* resuelve rate limiting por IP (estado que no pertenece a
  ninguna fila), pero para bloqueo por cuenta añade dependencia operativa sin beneficio aquí. Se
  reserva para cuando entre el rate limiting por IP.
- *Alternativa descartada — tabla aparte `login_attempts`:* más flexible (histórico) pero sobra para
  el alcance; dos columnas en `users` es lo mínimo que cumple los scenarios.

### D2 — Comprobar el bloqueo ANTES de verificar la contraseña
Orden en `login`: cargar identidad → si `locked_until` está **en el futuro**, rechazar (genérico) sin
tocar el contador → si `locked_until` es null o ya pasó, tratar como no bloqueada y verificar
contraseña → éxito/fallo actualiza estado (D3/D7).
- *Por qué:* verificar BCrypt en una cuenta ya bloqueada es trabajo inútil. Rechazar antes es más
  barato. (El coste de timing de esto se discute en D9.)

### D3 — Contador de **fallos consecutivos**; el éxito reinicia; la expiración reinicia
- Fallo de contraseña incrementa `failed_login_attempts`. Al alcanzar el umbral, se fija
  `locked_until = now + window`.
- Un login **exitoso** pone el contador a 0 y `locked_until` a null.
- Al **expirar** la ventana (`locked_until <= now`), el estado se trata como no bloqueado y el
  **siguiente intento parte de cero**: si ese intento falla, el contador queda en **1** (no re-bloquea
  de inmediato); si acierta, se resetea. Esto lo garantiza el `UPDATE` atómico de D7, que detecta la
  expiración en la misma sentencia. **Resuelve la pregunta antes abierta** ("reiniciar vs congelar"):
  se reinicia, porque congelar en el umbral haría que un único fallo post-expiración re-bloqueara —
  comportamiento observable e indeseado.

### D4 — Respuesta idéntica a credenciales inválidas (anti-enumeración a nivel de respuesta)
El rechazo por bloqueo lanza el **mismo** `InvalidCredentialsException` (mismo mensaje, mismo status,
mismo cuerpo y cabeceras). No se añade código ni mensaje de "cuenta bloqueada" visible al cliente.
- *Por qué:* decisión de producto tomada explícitamente — no revelar el estado de la cuenta.
- *Trade-off asumido:* un usuario legítimo bloqueado no sabe por qué falla (ver Riesgos). El alcance de
  esta garantía es la **respuesta HTTP**, no el timing (ver D9).

### D5 — Solo correos registrados generan estado
El contador vive en la fila de `users`; un correo inexistente no tiene fila, así que no se crea ni toca
estado. El path de "usuario no encontrado" sigue lanzando el genérico.
- *Por qué:* no hay contraseña que adivinar contra un correo que no existe; el barrido de correos es
  competencia del rate limiting por IP (Non-goal). A nivel de respuesta es indistinguible; el residuo
  de timing se aborda en D9.

### D6 — Configuración externalizada, con nombres, formato y validación cerrados
- `auth.lockout.max-attempts` — entero, **default 5**, restricción `>= 1`. Env: `AUTH_LOCKOUT_MAX_ATTEMPTS`.
- `auth.lockout.window` — `java.time.Duration` ISO-8601, **default `PT15M`** (15 min), restricción `> 0`.
  Env: `AUTH_LOCKOUT_WINDOW`.
- Se leen con `@ConfigurationProperties` + `@Validated` (`@Min(1)` y validación custom `> 0`), en
  `secrets.properties.example` + env. Arranque **falla** si un valor viola las restricciones.
- *Cambio de config en caliente:* no reescribe contadores ya acumulados. El umbral se compara **en
  cada evaluación** contra el valor actual (bajar el umbral puede bloquear antes a quien ya acumulaba
  fallos); la ventana ya quedó materializada en `locked_until` al bloquear, así que bloqueos vigentes
  conservan su vencimiento original.

### D7 — Incremento atómico en transacción independiente (`REQUIRES_NEW`)
El estado del contador se actualiza con **una sola sentencia SQL atómica** ejecutada en una transacción
**propia** (`@Transactional(propagation = REQUIRES_NEW)`, en un colaborador dedicado, p. ej.
`LoginAttemptService`), que **commitea aunque `login` lance el 401 después**:

```sql
UPDATE users
SET failed_login_attempts = CASE
      WHEN locked_until IS NOT NULL AND locked_until <= :now THEN 1
      ELSE failed_login_attempts + 1 END,
    locked_until = CASE
      WHEN locked_until IS NOT NULL AND locked_until <= :now THEN NULL
      WHEN failed_login_attempts + 1 >= :maxAttempts THEN :until
      ELSE locked_until END
WHERE id = :id
```

- *Resuelve #1 (rollback):* el incremento vive en su propia transacción committeada; el rollback de la
  transacción de `login` al lanzar el 401 no lo afecta.
- *Resuelve #2 (lost update):* el incremento es `failed_login_attempts + 1` **en la propia sentencia**,
  no read-modify-write en la app; PostgreSQL serializa los `UPDATE` concurrentes sobre la misma fila, así
  que N fallos concurrentes cuentan N.
- *Resuelve #3 (expiración):* la misma sentencia detecta `locked_until <= now` y reinicia a 1.
- El **reset por éxito** (`failed_login_attempts = 0, locked_until = NULL WHERE id = :id`) puede ir en la
  transacción de `login` (commitea normalmente en el path feliz) o compartir el colaborador; da igual
  porque no hay rollback en el path exitoso.
- *Alternativa descartada — `noRollbackFor`:* haría persistir el incremento sin transacción aparte, pero
  **no** resuelve el lost update; seguiría necesitando el `UPDATE` atómico. `REQUIRES_NEW` + sentencia
  atómica cubre ambos con una sola pieza.

### D8 — Qué intentos cuentan
- **Solo** un fallo de **contraseña** (BCrypt no coincide) contra un correo registrado incrementa.
- Un rechazo por **estado** (identidad/membresía/organización no activa) con contraseña **correcta**
  **no** incrementa (no es un intento de adivinar credenciales; ya autenticó).
- Un intento mientras la cuenta está **bloqueada** se rechaza en D2 **antes** de tocar el contador: no
  incrementa ni **extiende** `locked_until` (la ventana no se prolonga por seguir intentando).

### D9 — Alcance de la garantía anti-enumeración: respuesta, no timing
La garantía es que un correo inexistente, uno con contraseña incorrecta y uno bloqueado producen la
**misma respuesta HTTP** (status/cuerpo/cabeceras). **No** se garantiza indistinguibilidad por
**latencia**: el correo inexistente y el bloqueado saltan BCrypt, así que responden más rápido que una
cuenta activa con contraseña incorrecta.
- *Decisión:* Confirmada. Se acepta el residuo de timing en este alcance. El spec limita explícitamente
  su claim a la respuesta. Se descarta la implementación de un hash señuelo (decoy hash) al considerarse
  que la garantía a nivel de respuesta es suficiente y no justifica la complejidad ni el consumo de
  CPU adicional por intento fallido/inexistente.
- *Y hay una razón más fuerte para descartarlo:* el señuelo **desharía la defensa que da D2**. Hoy una
  cuenta bloqueada (o un correo inexistente) se rechaza **antes** de BCrypt, o sea barato. Con señuelo,
  cada uno de esos intentos quemaría el coste completo de BCrypt (~100 ms), convirtiendo el login en un
  **amplificador de CPU** — y precisamente aquí no hay nada que lo frene, porque el rate limiting por IP
  es Non-goal. O sea: el señuelo cambiaría una fuga de enumeración por latencia (menor, y ya mitigada a
  nivel de respuesta) por un vector de DoS (mayor, y sin mitigación disponible en este alcance). No es
  un endurecimiento pendiente: sería un empeoramiento.

### D10 — El bloqueo frena logins nuevos, no revoca sesiones vivas
Bloquear una cuenta impide **nuevos** logins; **no** invalida access/refresh tokens ya emitidos, y el
endpoint de **refresh no consulta** el estado de bloqueo.
- *Por qué:* el lockout es una defensa del path de verificación de credenciales contra fuerza bruta, no
  un mecanismo de revocación de sesión. La revocación ya tiene sus propias vías (logout; gate por
  estado de identidad/membresía en login/refresh). Mezclarlas ampliaría el alcance sin necesidad.
- *Recomendación adoptada y confirmada:* El alcance se limita estrictamente a evitar nuevos inicios de
  sesión. No se expelen sesiones vivas (refresh sigue funcionando con normalidad).

### D11 — Tiempo inyectable (`Clock`)
El cálculo de `locked_until` y la comparación de expiración usan un `java.time.Clock` inyectado (no
`Instant.now()` directo), para que los tests de expiración sean deterministas (avanzar el reloj en vez
de dormir). `locked_until` en UTC (`Instant`/`TIMESTAMPTZ`), coherente con `RefreshToken.expiresAt`.

### D12 — `UserStatus.LOCKED` y `locked_until` son mecanismos **separados**
`UserStatus` ya contempla `LOCKED` (enum existente, hoy **sin uso** en código ni migraciones, ver
`docs/domain.md`). El bloqueo por intentos fallidos **no** lo reutiliza:

| | Mecanismo | Naturaleza | Quién lo levanta |
|---|---|---|---|
| `UserStatus.LOCKED` / `DISABLED` | estado **administrativo** de la identidad | permanente | una acción humana/administrativa |
| `locked_until` | bloqueo **automático** por fuerza bruta | temporal, se expira solo | el paso del tiempo |

- *Por qué no reutilizar el enum:* el lockout automático tiene que **revertirse solo** al expirar la
  ventana. Si lo expresara con `UserStatus`, al expirar habría que devolver el estado a `ACTIVE` — y eso
  **resucitaría una cuenta que un administrador bloqueó a mano**, convirtiendo una defensa en un agujero
  de seguridad. Son ciclos de vida distintos y no deben compartir columna.
- *Interacción:* el gate por estado (`activeMembership`, D14 del change original) y el gate por
  `locked_until` son **independientes y ambos deben pasar**. Una identidad `LOCKED`/`DISABLED` se rechaza
  con el genérico aunque `locked_until` sea null; una identidad `ACTIVE` dentro de la ventana se rechaza
  con el genérico aunque su estado sea correcto.
- *Conteo sobre identidades no activas:* `login` verifica la contraseña **antes** que el estado, así que
  una contraseña **incorrecta** contra una identidad `LOCKED`/`DISABLED` **sí** incrementa el contador
  (es un intento de adivinar credenciales como cualquier otro). Coherente con D8: lo que no cuenta es el
  rechazo por estado con contraseña **correcta**. Es comportamiento explícito, no accidental.
- *Terminología:* en el spec, "bloqueada" se refiere **siempre** al bloqueo temporal (`locked_until`);
  el estado administrativo se nombra como "identidad no activa".

## Risks / Trade-offs

- **Usuario legítimo bloqueado y confundido** (D4, mensaje genérico) → Mitigación: ventana corta
  (15 min) auto-expirable; UX mejor sería un change aparte que reevalúe el trade-off anti-enumeración.
- **DoS dirigido a una cuenta**: quien conoce un correo puede mantenerlo bloqueado fallando a propósito
  → Mitigación: ventana corta y auto-expirable acota el daño; el rate limiting por IP (futuro) es la
  defensa real contra abuso sostenido. Riesgo aceptado en este alcance.
- **Residuo de timing** (D9): la latencia distingue correo inexistente/bloqueado de cuenta activa con
  contraseña mala → Mitigación disponible (hash señuelo), descartada por diseño.
- **Cambio de config vs. contadores acumulados** (D6): bajar el umbral puede bloquear antes a quien ya
  acumulaba fallos → comportamiento documentado, aceptado.

## Migration Plan

1. Migración Flyway (`db/migration/V<n>__user_login_lockout.sql`): añade
   `failed_login_attempts INT NOT NULL DEFAULT 0` y `locked_until TIMESTAMPTZ NULL` a `users`.
   Columnas aditivas con default → segura, sin backfill.
2. Añadir claves de config validadas a `secrets.properties.example` + leerlas (`@ConfigurationProperties
   @Validated`). Inyectar `Clock` (bean, `Clock.systemUTC()` por defecto).
3. Desplegar backend. Sin cambios de contrato → frontend no necesita despliegue coordinado.
4. **Rollback:** revertir el código deja las columnas huérfanas pero inertes; migración de reverso
   opcional. Sin pérdida de datos de sesión.

## Open Questions

Ninguna. Todas las decisiones de diseño (D9, D10 y defaults) han sido confirmadas.

