# Design — add-cold-start-warmup

## Context

- **Destino** (decisión del usuario, 2026-10-10): Neon + Render + Vercel en planes gratuitos, región Ohio (us-east-2) para la base y el backend. Referencia: HSO, `openspec/changes/archive/2026-10-05-add-cold-start-warmup` de `D:/proyectos/HSOFACOP`, con el mismo backend de partida.
- **Render free**: suspende el servicio tras 15 min sin tráfico entrante; la siguiente petición espera el arranque (~1 min). 750 h de instancia al mes por workspace: un servicio despierto 24/7 (~730 h) cabe. 512 MB, 0,1 CPU.
- **Neon free**: suspende el cómputo tras ~5 min sin actividad; las conexiones persistentes o los keepalives lo impiden. Cupo de horas de cómputo al mes que no alcanza para 24/7. Las cifras de los planes se anotan con fecha en `docs/deployment.md`, porque cambian.
- **Actuator hoy** (`application.yml`, `docs/backend.md` §11.1): expone `health,info`; `/actuator/health/**` y `/actuator/info` son públicos en `PublicPaths`; `show-details: always` (decisión consciente para local).
- **Pool**: HikariCP 7.0.2 gestionado por Spring Boot 4.0.6 (y Awaitility 4.3.0 vía `spring-boot-starter-test`; confirmado con `mvn dependency:tree`), sin configurar: `minimumIdle` = `maximumPoolSize` = 10 y `keepaliveTime` = 120 000 ms.
- **Base con RLS y dos roles** (`odontorisas_admin` dueño, solo Flyway; `odontorisas_app` solo DML, `NOSUPERUSER NOBYPASSRLS`; `modules/backend/scripts/db-roles.sql`). El contexto de RLS se fija por transacción.
- **Frontend**: `RootLayout` llama a `useSessionBootstrap` (refresh single-flight) y muestra "Cargando…" mientras el `status` es `idle`/`loading`. Toda carga pasa por ahí, con o sin sesión: es la primera petición al backend.
- **Sesión**: refresh token en cookie `HttpOnly; Secure; SameSite=Lax` (`COOKIE_SECURE`); CSRF deshabilitado porque la cookie se protege con `SameSite=Lax` + `POST`.
- **Login**: `AuthLayout` (en `modules/auth`) tiene el panel de marca con `SmileArcBackground` y los tokens fijos `--brand-hero-*` (`update-tailwind-v4` D5).

## Goals / Non-Goals

**Goals:** un ping barato y público para el monitor externo, sin tocar la base; una base que pueda suspenderse; Actuator público mínimo; que una espera larga al cargar se explique; documentar lo que hace falta para desplegar sin perder el aislamiento entre clínicas.

**Non-Goals:** los del proposal.

## Decisions

### D1. Ping: liveness de Actuator, no un controlador propio

- `GET /actuator/health/liveness` ya es público, los monitores lo entienden y no hay código que mantener. Se activa con `management.endpoint.health.probes.enabled: true` (fuera de Kubernetes no se activa solo).
- **Liveness, no `/actuator/health`**: el health general consulta la base en cada llamada; un ping cada 10 min mantendría Neon despierta. El grupo liveness no incluye `db` salvo `management.endpoint.health.group.liveness.include`, que no se configura.
- No se fija el cuerpo completo: con `show-details: always` incluye `components.livenessState`; con `never`, solo `status`. El contrato es `200`, `status: UP`, sin `db`.
- **El health check de Render no sustituye al monitor externo**: Render no documenta que cuente como tráfico que evite la suspensión. El monitor es obligatorio (`docs/deployment.md`).

*Descartado:* un `/api/health-check` propio. Duplica Actuator y suma otra ruta pública.

### D2. Actuator público mínimo

- `show-details: ${HEALTH_SHOW_DETAILS:always}`. En Render, `never`: `/actuator/health` responde solo `status`, sin publicar el estado de la base ni del disco.
- `/actuator/info` sigue expuesto pero **autenticado**: sale de `PublicPaths` y lo cubre `anyRequest().authenticated()`. Nada lo usa, y así no queda una ruta pública cuyo contenido pueda crecer sin revisión.

### D3. Pool que deja dormir a la base

`spring.datasource.hikari`:
- `minimum-idle: ${DB_POOL_MIN_IDLE:0}`;
- `idle-timeout: ${DB_POOL_IDLE_TIMEOUT_MS:60000}` (mínimo de Hikari: 10 000);
- `keepalive-time: 0` (desactivado, fijo, sin variable);
- `maximum-pool-size: ${DB_POOL_MAX_SIZE:5}`.

Hikari retira las conexiones ociosas tras `idleTimeout`, con la tolerancia de su revisión periódica (hasta ~30 s más): no se promete un pool vacío al segundo exacto. La siguiente petición abre una conexión y Neon despierta en menos de un segundo. Mismos valores en local.

**Conexión a Neon: cadena directa**, `sslmode=require`. El endpoint `-pooler` es PgBouncer en modo transacción: Flyway necesita semántica de sesión para sus locks, y el contexto de RLS se fija por transacción, así que no se depende del pooler sin una prueba específica.

### D4. Medir la espera del splash, no un ping aparte

- La primera petición de cada carga ya es el refresh de sesión y el splash ya la espera. Medir esa espera es exacto: si pasa de 4 s, el usuario está esperando de verdad.
- En **cada** carga, no solo la primera del día: el servidor puede dormirse varias veces si el monitor falla. Como solo aparece pasados 4 s, en una carga normal no se ve.
- Al terminar el intento, con éxito o con error, la pantalla desaparece sin animación de cierre. Error = comportamiento actual: sesión limpia y login.

### D5. Pantalla de arranque en frío

| Espera | Estado | Contenido |
|---|---|---|
| 0–4 s | `loading` | Logo + "Cargando…" (como hoy) |
| 4–90 s | `warming` | Título "Preparando tu consultorio digital", mensaje "Estamos preparando tu consultorio digital para iniciar el día, esto puede tomar un minuto…", barra estimada y "Esto suele tardar menos de un minuto. No cierres esta pestaña: continuará sola." (desde 45 s: "Ya casi está…") |
| > 90 s | `stuck` | Icono de reloj en tono `warning`, "Está tardando más de lo normal", "El servidor todavía no responde. Puedes seguir esperando o volver a intentarlo.", **Reintentar**, "Si el problema continúa, avisa al administrador." |
| sin red | `offline` | "Sin conexión a internet", "Revisa tu wifi o tus datos móviles. Volveremos a intentarlo solos cuando regrese la conexión.", botón secundario "Reintentar ahora" |

- `offline` tiene prioridad desde el segundo 0 (`navigator.onLine` falso), así no se confunde una red caída con el servidor dormido. Al volver la red (`online`), recarga.
- **Barra honesta**: `92 × (1 − e^(−s/22))` %. Avanza rápido al inicio, ronda el 85 % al minuto y nunca llega sola al 100 %. Sin pasos inventados ("conectando a la base…") que no se pueden medir. `role="progressbar"` con `aria-valuenow`; se anima con `transform: scaleX`.
- **Reintentar** = `window.location.reload()`. La petición en curso no se cancela: si responde antes, la app entra.
- **Marco**: en escritorio, el mismo panel de marca que el login (la mayoría de arranques en frío terminan en el login, así la transición no salta); en celular, una columna con el logo a color. Solo tokens (`primary`, `muted`, `warning`, `--brand-hero-*`), con modo oscuro.
- **Accesibilidad**: región `role="status"` / `aria-live="polite"` que se anuncia **una vez por cambio de estado**, no en cada tic de la barra; el foco va a "Reintentar" al aparecer `stuck`; con "reducir movimiento" no hay spinner, pulso ni transición de la barra (`MotionConfig` ya lo respeta para framer-motion; lo demás con `motion-reduce:`).
- **Ubicación** (`docs/frontend.md` §1):
  - `core/hooks/useElapsedWhile(active)`: segundos transcurridos mientras `active`; se reinicia al pasar a inactivo y limpia su intervalo al desmontar (StrictMode);
  - `core/hooks/useOnlineStatus()`;
  - `core/components/ServerWarmupScreen`: no conoce el dominio; recibe los segundos y si hay red;
  - `core/components/BrandPanel`: el panel de marca extraído de `AuthLayout`, con `SmileArcBackground` movido a `core/components` (hoy en `modules/auth`, y `core` no puede depender de `auth`);
  - `RootLayout` renderiza `ServerWarmupScreen` mientras `status` es `idle`/`loading`.
- Superficie nueva de UI: la pasada visual va con el skill `frontend-design` (reparto acordado), con capturas en claro/oscuro a 375 y 1280 px de cada estado.

### D6. Guía de despliegue (`docs/deployment.md`, nuevo)

Se crea con lo que este change deja listo y lo que el despliegue debe cumplir:
- **Monitor externo** (UptimeRobot o cron-job.org): `GET /actuator/health/liveness` cada 10 min; es el único mecanismo que mantiene despierto el backend.
- **Neon**: proyecto en us-east-2; cadena **directa** con `sslmode=require` en `DB_URL`; variables `DB_POOL_*`.
- **⚠️ Roles y RLS en Neon** (propio de Dentdesk): los roles creados desde el panel de Neon heredan `neon_superuser`, que tiene `BYPASSRLS`. Si `odontorisas_app` se crea así, **el aislamiento entre clínicas no existe en producción** sin que nada falle a la vista. Se crean por SQL con `scripts/db-roles.sql` (adaptado al SQL Editor de Neon, sin variables de `psql`) y se verifica antes de abrir la app:
  ```sql
  SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname LIKE 'odontorisas_%';
  -- odontorisas_app debe tener rolsuper = false y rolbypassrls = false
  ```
  La app usa `odontorisas_app` (`DB_USERNAME`); Flyway, `odontorisas_admin` (`DB_ADMIN_USERNAME`).
- **Variables de Render**: `HEALTH_SHOW_DETAILS=never`, `SWAGGER_ENABLED=false`, `COOKIE_SECURE=true`, `JWT_SECRET` ≥ 32 bytes, credenciales de base.
- **Condición de la cookie entre dominios**: `*.vercel.app` y `*.onrender.com` son *sites* distintos (Public Suffix List): con `SameSite=Lax` la cookie de refresh no viajaría. Se resuelve en `add-cloud-deployment` sirviendo la API bajo el mismo origen con los rewrites de Vercel (que esperan hasta 120 s el arranque en frío). Este change solo lo documenta: **se puede aplicar sin resolverlo**, pero el sistema no es desplegable de extremo a extremo hasta ese change.

### D7. Cupo de horas de Render compartido con HSO

HSO ya está desplegado en Render free y despierto 24/7 (~730 h al mes). El cupo gratuito son **750 h al mes por workspace**: no alcanza para un segundo servicio también despierto todo el día. Opciones, a decidir **antes del primer despliegue** (`add-cloud-deployment`), no en este change:

1. **Workspace de Render propio para Dentdesk** (otra cuenta o equipo): cada uno con sus 750 h; los dos despiertos 24/7. Más simple de operar.
2. **Monitor solo en horario de atención** (cron-job.org permite horarios) para uno o ambos: por ejemplo 7:00–22:00 son ~465 h al mes por servicio; fuera de horario duermen y el primer ingreso muestra la pantalla de este change.
3. **Dejar dormir a Dentdesk** sin monitor hasta tener clínicas reales usándolo.

Este change sirve igual en cualquiera de las tres: el ping solo se usa si se monitorea, y la pantalla de arranque en frío cubre justo las opciones 2 y 3. `docs/deployment.md` registra las opciones y el presupuesto de horas.

## Risks / Trade-offs

- **El monitor es un tercero**: si falla, vuelve el arranque en frío, pero ahora con explicación.
- **Neon tarda en despertar**: la primera petición espera dentro del `connection-timeout` de Hikari (30 s por defecto), de sobra.
- **axios sin timeout**: el refresh espera el arranque completo. Si se pone uno (o lo impone el proxy), debe superar ~90 s.
- **512 MB con Java 25 + Hibernate + Flyway**: se mide en `add-cloud-deployment` (HSO midió ~295 MiB).
- **Un rol de app mal creado en Neon** no da ningún error visible: por eso la verificación de `rolbypassrls` es un paso explícito de la guía, y `add-cloud-deployment` la incluye en su matriz de verificación.
