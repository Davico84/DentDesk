# Design — add-cloud-deployment

## Context

- Referencia: HSO, `openspec/changes/archive/2026-10-06-add-cloud-deployment` de `D:/proyectos/HSOFACOP`, mismo backend de partida, ya desplegado en Neon + Render + Vercel. Se reutilizan su `Dockerfile` (con AOT cache), su `render.yaml` y su `vercel.json`, con las diferencias de este design.
- `add-cold-start-warmup` dejó: liveness público sin base, `HEALTH_SHOW_DETAILS`, pool que deja dormir a Neon, pantalla de arranque en frío y `docs/deployment.md` (con el horario 9–20 compartido con HSO y los roles de Neon sin `BYPASSRLS`). `add-support-contact` dejó el contacto de soporte.
- **Render**: no tiene runtime nativo de Java, servicios Java = Docker. Free: 512 MB, 0,1 CPU, puerto en `PORT`. El workspace se comparte con HSO.
- **Rutas de la API hoy**: en la raíz (`/auth`, `/patients`, `/patients/{id}/…`, `/cie10`, `/dental-chart/catalog`, `/organizations/me/branding`, `/actuator`). **Chocan con las pantallas** de la app (`/patients`, `/patients/:id`, `/patients/:id/clinical-history`…).
- **Cookie de refresh**: `HttpOnly; Secure; SameSite=Lax; Path=/auth` (`RefreshCookieFactory`, constante `COOKIE_PATH`). CSRF deshabilitado por esa cookie.
- **Frontend**: `httpClient.ts` usa `import.meta.env.VITE_API_URL ?? "http://localhost:8080"`; `vite.config.ts` define un `BACKEND_URL` que nadie lee; el cliente lo genera orval con paths relativos (`/patients/…`).
- **Base**: dos roles (`odontorisas_admin` para Flyway, `odontorisas_app` para la app) y RLS (`scripts/db-roles.sql`). `DbRolesScriptIT` lo prueba contra un PostgreSQL 16 local como superusuario: **no** reproduce las restricciones de Neon (dueño del proyecto no superusuario, `neon_superuser`), que se verifican a mano en el despliegue (D6, D7).
- **Registro**: crea la organización y al usuario como ADMIN de su clínica (`AuthService`). No hace falta promover un primer ADMIN por SQL, como en HSO.
- **Flujo de ramas**: trabajo en `dev`, PR a `main` con el CI en verde, merge del usuario.

## Goals / Non-Goals

**Goals:** publicar gratis, reproducible desde el repo, sin secretos versionados, con la sesión funcionando tras recargar, el arranque en frío cubierto en la ruta crítica y el aislamiento entre clínicas verificado en la base real.

**Non-Goals:** los del proposal.

## Decisions

### D1. Toda la API bajo `/api`

`server.servlet.context-path: /api` en `application.yml`, en todos los entornos.

- **Por qué**: con la API en la raíz, el proxy de Vercel tendría que reenviar `/patients/*`, y entonces recargar la pantalla `/patients/1` devolvería el JSON del endpoint. Separar por **prefijo** es la única regla que el proxy puede aplicar sin heurísticas.
- **Qué no cambia** (revisado en el código por Codex, sin hallazgos): los controladores (mappings relativos), Spring Security (`PublicPaths` y los matchers comparan la ruta **sin** la base), el filtro JWT (solo lee `Authorization`), CORS (registrado en `/**`, que se evalúa sobre la ruta del servlet) y los **paths del contrato** OpenAPI (relativos: `/patients/{id}`), así que el cliente de orval, que solo lee `contracts/openapi.json` y no usa `servers`, no cambia. No hay redirects, `Location` ni URLs absolutas armadas a mano; `ProblemDetail.instance` pasará a incluir `/api` porque Spring lo toma de la URI real.
- **Tests**: todos usan MockMvc, que **no** aplica el context-path, así que siguen pasando, pero tampoco prueban el prefijo. Se agrega un IT con servidor real (`webEnvironment = RANDOM_PORT` y un cliente HTTP) que recorre `/api/auth/*`, `/api/actuator/health/liveness`, que `/patients` sin la base no es la API y el **preflight CORS** a `/api/auth/refresh` (tarea 1.3).
- **Contrato**: el guardián de drift (`ContractDriftIT`) ignora `servers` a propósito. `OpenApiContractIT` suma aserciones de que los paths siguen sin `/api` (`/auth/login`, no `/api/auth/login`). La URL de `servers` no es parte del contrato verificado (orval no la usa): se documenta así.
- **Cookie de refresh**: su `Path` pasa a ser la base + `/auth` (`/api/auth`). `RefreshCookieFactory` lo arma con `ServerProperties.getServlet().getContextPath()` **normalizado** (vacío, `/api` y `/api/` dan `/auth`, `/api/auth` y `/api/auth`) en vez de la constante, y la cookie de alta y la de borrado (logout) salen del mismo builder, así no se desalinean. Una cookie con `Path=/auth` no viajaría a `/api/auth/refresh`: la sesión no sobreviviría a una recarga.
- **Cookie vieja con `Path=/auth`**: queda huérfana en un navegador que ya la tuviera (no viaja a `/api/auth/*` y el logout nuevo no la borra; expira sola). Dentdesk **nunca se publicó**: solo puede existir en navegadores de desarrollo local. No se emite una cookie de borrado extra; se documenta.
- **Actuator**: queda en `/api/actuator/health/liveness` (health check de Render y monitor externo). `docs/deployment.md` y `docs/backend.md` se corrigen.
- **Pantallas de acceso**: **no se renombran**. `/auth/login` y `/auth/register` son rutas de la app y ya no chocan con `/api/auth/*`.

*Descartado:* el prefijo solo en producción (Vercel quitando `/api` al reenviar). La cookie seguiría con `Path=/auth` y el navegador la pediría en `/api/auth/refresh`: habría que cambiar el path solo en producción, y local y producción dejarían de comportarse igual.

*Descartado:* renombrar las pantallas, como HSO. Aquí no alcanza: chocan también `/patients/*`, que no se pueden renombrar sin romper los enlaces de toda la app.

### D2. Imagen del backend (como HSO)

`modules/backend/Dockerfile` multi-etapa:
1. `eclipse-temurin:25-jdk`: `mvnw` + `.mvn` + `pom.xml`, `dependency:go-offline` (capa cacheable), `src`, `package -DskipTests` (el deploy sale de `main`, que solo recibe merges con `mvn verify` en verde) y el jar **extraído** (`-Djarmode=tools extract`).
2. `eclipse-temurin:25-jre`: solo la app extraída, usuario sin privilegios, y **AOT cache de Java 25** creado en el build con una corrida de entrenamiento (contexto que arranca y sale tras el refresh, sin base: Flyway apagado e Hibernate sin metadatos JDBC). G1 explícito, solo C1, `MaxRAMPercentage=70`, sin código nativo en el cache (`-XX:-AOTAdapterCaching -XX:-AOTStubCaching`: Render construye y ejecuta en CPUs distintos, y en HSO falló con `SIGILL`).
- `.dockerignore`: `secrets.properties`, `.env*`, `target/`, `compose.yaml`, archivos de IDE.
- `server.port: ${PORT:8080}`.
- **Verificación local** (tarea 1.4): `docker run -m 512m --cpus 0.1 -e PORT=10000` contra el PostgreSQL de compose; se registran tiempo de arranque, memoria (RSS) y liveness en `docs/deployment.md`. HSO midió ~54 s y ~295 MiB; Dentdesk tiene más migraciones (22) y más entidades, así que se mide, no se supone.
- **Entrenamiento del AOT cache, distinto de HSO** (bloqueante encontrado por Codex): `application.yml` resuelve el datasource con `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` (Flyway con `${DB_ADMIN_*:...}`) y el JWT con `${JWT_SECRET}`. El Dockerfile de HSO pasaba `-Dspring.datasource.*`; aquí se pasan **las variables que la app consume** como variables de entorno de la corrida (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` de ≥ 32 bytes, todas de entrenamiento, no secretas), con Flyway apagado e Hibernate sin metadatos JDBC. El catálogo CIE10 lo carga Flyway (`V13`), no un bean al arrancar: no toca la base en el refresh. La tarea 1.6 construye la imagen de verdad (`docker build`); si el entrenamiento falla, el build falla.

### D3. Blueprint de Render (`render.yaml`, raíz)

- `type: web`, `runtime: docker`, `plan: free`, `region: ohio`, `branch: main`, `autoDeployTrigger: checksPass` (confirmado en HSO), `rootDir: modules/backend`, `dockerfilePath: ./Dockerfile`, `healthCheckPath: /api/actuator/health/liveness`.
- `name: dentdesk-api` → `https://dentdesk-api.onrender.com` (si el nombre está tomado, Render agrega un sufijo y se ajusta `vercel.json`).
- `envVars`:
  - fijas: `HEALTH_SHOW_DETAILS=never`, `SWAGGER_ENABLED=false`, `COOKIE_SECURE=true`;
  - con `sync: false` (se cargan en el panel): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ADMIN_USERNAME`, `DB_ADMIN_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`.
- Diferencia con HSO: las **dos** credenciales de base (Flyway con el dueño; la app con el rol sin `BYPASSRLS`).

### D4. Frontend en Vercel con la API en el mismo origen

`vercel.json` en la raíz (Root Directory = raíz del repo, así ve el `pnpm-lock.yaml`):

```json
{
  "installCommand": "pnpm install --frozen-lockfile",
  "buildCommand": "pnpm --filter dentdesk-frontend build",
  "outputDirectory": "modules/frontend/dist",
  "framework": null,
  "rewrites": [
    { "source": "/api/:path*", "destination": "https://dentdesk-api.onrender.com/api/:path*" },
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

- Los rewrites solo aplican si no hay un archivo estático con esa ruta. Vercel Hobby espera hasta 120 s en un rewrite externo: cubre el arranque en frío.
- Un test del frontend lee `vercel.json` y fija el orden, el destino `/api`, la salida y que no se reenvía nada más.
- **URL base del cliente**: `core/config/apiBaseUrl.ts`, `resolveApiBaseUrl({ VITE_API_URL, DEV })`: con `VITE_API_URL`, ese valor; si no, `http://localhost:8080/api` en desarrollo y `/api` en producción. `httpClient.ts` la usa. Se quitan el `define BACKEND_URL` (`vite.config.ts`, `vitest.config.ts`), su `declare` y la línea de `.env.example`, que pasa a documentar `VITE_API_URL`. Los tests de MSW siguen con `*/…`.
- El filtro de pnpm es **`dentdesk-frontend`**, el nombre real del paquete (en HSO era otro; copiarlo rompería el build de Vercel).
- El test de `vercel.json` fija: el orden (`/api/:path*` antes del fallback), el destino con `/api`, el fallback a `/index.html`, `outputDirectory`, el filtro `dentdesk-frontend`, y que **no** hay rewrites de `/auth/*` ni de `/actuator/*`.

### D5. CORS con el proxy

Vercel reenvía `Origin: https://<app>.vercel.app`; para el backend es una petición entre orígenes. `CORS_ALLOWED_ORIGINS` = **origen exacto** de producción. Primer despliegue: Render antes que Vercel, con `https://pendiente.invalid`; al tener el dominio de Vercel, se actualiza en el panel. *Previews* de Vercel: sin sesión (deliberado).

### D6. Puesta en marcha guiada (pasos del usuario)

1. **Neon**: proyecto en `aws-us-east-2`; roles por SQL y verificación (`docs/deployment.md` §4.2); cadena **directa** con `sslmode=require`.
2. **Render**: en el workspace compartido con HSO, "New Blueprint" desde el repo (rama `main`); cargar las credenciales y `CORS_ALLOWED_ORIGINS=https://pendiente.invalid`. Primer deploy: Flyway migra con el rol **admin** y liveness `200`. Después, en el SQL Editor de Neon: las tablas pertenecen a `odontorisas_admin` y `odontorisas_app` puede leerlas (los `GRANT` y `ALTER DEFAULT PRIVILEGES` de `V6`). **No se abre la app a usuarios** hasta completar esta comprobación y la de §4.2.
3. Completar la URL real de Render en `vercel.json` si difiere (commit en `dev`, PR a `main`).
4. **Vercel**: importar el repo, Root Directory = raíz, rama de producción `main`, sin `VITE_API_URL`. Luego `CORS_ALLOWED_ORIGINS` en Render = origen de Vercel.
5. **cron-job.org**: liveness cada 10 min de 9:00 a 19:50 (`America/Lima`).

### D7. Matriz de verificación del despliegue

| Área | Prueba | Esperado |
|---|---|---|
| Rutas | recargar `/auth/login`, `/auth/register`, `/patients/1` | la app, no JSON |
| Proxy | login y refresh desde la app | `200` vía Vercel → Render |
| Cookie | tras login, `Set-Cookie` del dominio de Vercel | `Path=/api/auth`, `Secure`, `HttpOnly`, `SameSite=Lax` |
| Sesión | recargar con sesión (Chrome y Safari/iOS) | sigue con sesión |
| CORS | refresh vía proxy | `200`, no `403` |
| Actuator | `/api/actuator/health/liveness` / `/api/actuator/health` / `/api/actuator/info` | `200 UP` / sin detalle / `401` |
| **RLS** | consulta de `pg_roles` (§4.2) | `odontorisas_app` sin `rolsuper`, `rolbypassrls` ni `neon_superuser` |
| **Aislamiento** | dos clínicas registradas, un paciente cada una | cada una ve solo el suyo; el de la otra por URL → "no encontrado" |
| Arranque en frío | fuera del horario del monitor: abrir, login, recargar | pantalla de espera → entra; tiempo total medido |
| Soporte | botones de WhatsApp y correo | abren lo esperado |
| Smoke E2E | `E2E_BASE_URL=<url> playwright test e2e/auth.smoke.spec.ts` | verde |

Los resultados se registran en `docs/deployment.md`.

## Risks / Trade-offs

- **Cambiar la base de la API** toca la cookie, el health check, el monitor y la URL del cliente a la vez. Mitigación: tests de la cookie con la base, test de `resolveApiBaseUrl`, e2e local completo (login + recarga) antes de desplegar.
- **Arranque con 0,1 CPU**: puede superar el minuto; la pantalla de espera lo cubre y Vercel espera 120 s. Si la medición local se acerca a 120 s, se ajusta la JVM antes de desplegar.
- **512 MB**: se mide (HSO, ~295 MiB).
- **Etiquetas móviles** (`25-jdk`/`25-jre`): reciben parches; si uno rompe el build, se fija por digest.
- **Un rol mal creado en Neon** no da error visible: la verificación de `pg_roles` y la prueba de aislamiento son pasos de la matriz, no opcionales.
- **Free tiers cambian**: cifras con fecha en `docs/deployment.md`.
