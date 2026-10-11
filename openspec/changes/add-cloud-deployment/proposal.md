# Proposal — add-cloud-deployment

## Why

Tercero de los cuatro changes del despliegue (`docs/vision.md` §8): `add-cold-start-warmup` y `add-support-contact` (hechos) → **`add-cloud-deployment`** → `add-database-backups`. Los dos primeros dejaron el sistema listo para dormir y despertar en planes gratuitos y con un contacto de soporte; todavía no se puede publicar:

- el backend no tiene imagen de contenedor, y Render no tiene runtime de Java propio: necesita Docker;
- no escucha en el puerto que asigna Render (`PORT`);
- el frontend apunta por defecto a `http://localhost:8080` también en producción, y arrastra una variable `BACKEND_URL` que nadie lee;
- la cookie de sesión (`SameSite=Lax`) exige servir la API **bajo el mismo origen que el frontend** (`docs/deployment.md` §5);
- **propio de Dentdesk**: la API vive en la raíz (`/patients`, `/auth`, `/cie10`…), con las mismas rutas que las pantallas de la app (`/patients/1` es a la vez una pantalla y un endpoint). Un proxy no puede distinguirlas: recargar la ficha de un paciente devolvería JSON. HSO no tiene este problema porque su API ya vivía bajo `/api`.

## What Changes

- **API bajo `/api`** (`server.servlet.context-path`), en local y en producción: `/api/patients`, `/api/auth/login`, `/api/actuator/health/liveness`. Los paths del contrato OpenAPI no cambian (son relativos a la base); la base del cliente pasa a terminar en `/api`. La cookie de refresh pasa a `Path=/api/auth`. Las pantallas de la app no cambian: **`/auth/login` y `/auth/register` siguen igual** (en HSO hubo que renombrarlas porque su proxy reenviaba `/auth/*`; aquí solo se reenvía `/api/*`).
- **Backend en contenedor**: `Dockerfile` multi-etapa (JDK 25 → JRE 25, solo la app, usuario sin privilegios, JVM acotada a 512 MB y AOT cache de Java 25 como en HSO, que bajó el arranque con 0,1 CPU de ~294 s a ~54 s), `.dockerignore` y `server.port: ${PORT:8080}`. Se verifica localmente con 512 MB y 0,1 CPU.
- **Blueprint de Render** (`render.yaml`): servicio Docker gratuito en Ohio, rama `main`, despliegue solo con los checks en verde, health check a liveness, variables fijas en el archivo y credenciales sin valor (las **dos** credenciales de base: la del rol de la app y la del rol de Flyway).
- **Frontend en Vercel con la API en el mismo origen** (`vercel.json` en la raíz): `/api/*` se reenvía a Render y todo lo demás sirve la app. En producción el cliente usa `/api` relativo; en desarrollo, `http://localhost:8080/api`. Se quita `BACKEND_URL`.
- **CORS** en despliegue: el origen exacto del frontend de producción.
- **Puesta en marcha guiada** (pasos del usuario en los paneles, documentados): Neon con los roles por SQL y la verificación de `rolbypassrls` (`docs/deployment.md` §4.2), Render (workspace compartido con HSO), Vercel, el monitor de cron-job.org de 9:00 a 19:50, y una **matriz de verificación** que incluye que **dos clínicas no se ven entre sí en la base desplegada**.

## Capabilities

### New Capabilities

- `cloud-deployment`: cómo se empaqueta y publica Dentdesk en planes gratuitos (imagen del backend, blueprint de Render, frontend con la API en el mismo origen, aislamiento entre clínicas en la base desplegada y verificación del despliegue).

### Modified Capabilities

Ninguna: las specs vigentes nombran los endpoints relativos a la base de la API (`POST /auth/login`), que no cambian; la base `/api` la fija `cloud-deployment`.

## Non-goals

- Dominio propio, entornos de staging o sesión en las *previews* de Vercel.
- Copias de seguridad: `add-database-backups`.
- Observabilidad, alertas o métricas.
- Registro por invitación (hoy cualquiera puede registrar una clínica: es el estado actual del producto, no de este change).

## Impact

- Backend: `application.yml` (`server.port`, `server.servlet.context-path`), `RefreshCookieFactory` (path de la cookie con la base), `modules/backend/Dockerfile` y `.dockerignore`, tests que fijan el path de la cookie. Sin cambios de dominio ni de base de datos.
- Contrato: `contracts/openapi.json` regenerado (cambia la URL del servidor, no los paths).
- Raíz: `render.yaml`, `vercel.json`.
- Frontend: `core/config/apiBaseUrl.ts` + `httpClient.ts`; `vite.config.ts`, `vitest.config.ts`, `vite-env.d.ts`, `.env.example` sin `BACKEND_URL`.
- Docs: `docs/deployment.md` (pasos, matriz y resultados), `docs/backend.md` (base `/api`, liveness), `docs/vision.md` §8.
- Cuentas externas (las crea el usuario): Neon, Render (workspace compartido con HSO), Vercel, cron-job.org. Sin costo.
