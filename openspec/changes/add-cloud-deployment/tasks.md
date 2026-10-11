> Commits separados por scope (`docs/commits.md`): backend/infra · frontend · docs. Antes de tocar el frontend, skill `frontend-guard`. Las tareas marcadas **(usuario)** son pasos en paneles externos: las hace el usuario con la guía; yo verifico el resultado. Los IT y la imagen necesitan Docker Desktop encendido.

## 1. Backend

- [ ] 1.1 `application.yml`: `server.servlet.context-path: /api` y `server.port: ${PORT:8080}` (design D1/D2)
- [ ] 1.2 `RefreshCookieFactory`: `Path` = base + `/auth`, leída de `server.servlet.context-path`; tests que lo fijan (con y sin base) y los IT de auth al día
- [ ] 1.3 Regenerar `contracts/openapi.json` (cambia la URL del servidor, no los paths) y confirmar que el cliente de orval no cambia; `mvn verify` verde
- [ ] 1.4 `modules/backend/Dockerfile` (multi-etapa, AOT cache, usuario sin privilegios, JVM para 512 MB) y `.dockerignore` (design D2)
- [ ] 1.5 Verificación local de la imagen: `docker build`; inspección (sin `secrets.properties`, `.env`, `src/`, `mvnw`; usuario no root); `docker run -m 512m --cpus 0.1 -e PORT=10000` contra el PostgreSQL de compose: migra y liveness `200` en `/api/actuator/health/liveness`; registrar arranque y RSS en `docs/deployment.md`
- [ ] 1.6 `render.yaml` en la raíz (design D3) y un test que lo lee y fija las variables, la rama y el health check

## 2. Frontend

- [ ] 2.1 `core/config/apiBaseUrl.ts` (`resolveApiBaseUrl`) + test (desarrollo y producción, con y sin `VITE_API_URL`); `httpClient.ts` lo usa (design D4)
- [ ] 2.2 Quitar `BACKEND_URL` (`vite.config.ts`, `vitest.config.ts`, `vite-env.d.ts`); `.env.example` con `VITE_API_URL`; tipar `VITE_API_URL`
- [ ] 2.3 `vercel.json` en la raíz (filtro con el nombre real del paquete) + test que fija el orden de los rewrites, el destino `/api`, la salida y que no se reenvía nada más
- [ ] 2.4 Build local idéntico al de Vercel desde la raíz (`pnpm install --frozen-lockfile` + build) y `pnpm validate` verde
- [ ] 2.5 Prueba local de punta a punta con la base `/api`: backend + frontend en desarrollo, login, recarga con sesión (la cookie viaja a `/api/auth/refresh`) y una pantalla de paciente

## 3. Puesta en marcha (usuario, guiada)

- [ ] 3.1 **(usuario)** Neon: proyecto en `aws-us-east-2`, roles por SQL y consulta de verificación de `pg_roles` (`docs/deployment.md` §4.2)
- [ ] 3.2 **(usuario)** Render: Blueprint desde el repo en el workspace compartido con HSO; credenciales y `CORS_ALLOWED_ORIGINS=https://pendiente.invalid`; primer deploy migra y liveness `200`
- [ ] 3.3 Ajustar la URL de Render en `vercel.json` si difiere de `dentdesk-api.onrender.com` (commit en `dev`, PR a `main`)
- [ ] 3.4 **(usuario)** Vercel: importar el repo (raíz, rama `main`, sin `VITE_API_URL`); `CORS_ALLOWED_ORIGINS` en Render = origen de Vercel
- [ ] 3.5 **(usuario)** cron-job.org: liveness cada 10 min de 9:00 a 19:50 (`America/Lima`)

## 4. Verificación del despliegue

- [ ] 4.1 Matriz del design D7 (rutas, proxy, cookie, sesión en Chrome y Safari/iOS, CORS, Actuator, **RLS y aislamiento entre dos clínicas**, arranque en frío, soporte); resultados en `docs/deployment.md`
- [ ] 4.2 Smoke E2E contra la URL pública (`E2E_BASE_URL=https://<app>.vercel.app pnpm exec playwright test e2e/auth.smoke.spec.ts`)

## 5. Docs

- [ ] 5.1 `docs/deployment.md`: base `/api` y liveness en `/api/actuator/health/liveness`; pasos concretos (Neon → Render → Vercel → monitor); rama de producción; CORS y previews; mediciones y matriz con resultados. `docs/backend.md` (base `/api`, cookie). `docs/vision.md` §8
- [ ] 5.2 `openspec validate add-cloud-deployment --strict`
