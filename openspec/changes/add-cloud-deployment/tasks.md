> Commits separados por scope (`docs/commits.md`): backend/infra · frontend · docs. Antes de tocar el frontend, skill `frontend-guard`. Las tareas marcadas **(usuario)** son pasos en paneles externos: las hace el usuario con la guía; yo verifico el resultado. Los IT y la imagen necesitan Docker Desktop encendido.

## 1. Backend

- [x] 1.1 `application.yml`: `server.servlet.context-path: /api` y `server.port: ${PORT:8080}` (design D1/D2)
- [x] 1.2 `RefreshCookieFactory`: `Path` = base normalizada + `/auth` (`ServerProperties`); alta y borrado del mismo builder; tests con base vacía, `/api` y `/api/`
- [x] 1.3 IT con servidor real (`RANDOM_PORT`) y la base `/api`: login y refresh en `/api/auth/*` (cookie `Path=/api/auth` que viaja al refresh), liveness en `/api/actuator/health/liveness`, `/patients` sin la base no es la API, y preflight CORS (`OPTIONS` con `Origin`, `Access-Control-Request-Method` y credenciales) a `/api/auth/refresh`
- [x] 1.4 `OpenApiContractIT`: los paths siguen sin `/api`. Regenerar `contracts/openapi.json` si cambia, confirmar que el cliente de orval no cambia y documentar que `servers` no es parte del contrato verificado; `mvn verify` verde
- [x] 1.5 `modules/backend/Dockerfile` (multi-etapa, AOT cache entrenado con `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` de entrenamiento como variables de entorno, usuario sin privilegios, JVM para 512 MB) y `.dockerignore` (design D2)
- [x] 1.6 Verificación local de la imagen: `docker build`; inspección (sin `secrets.properties`, `.env`, `src/`, `mvnw`; usuario no root); `docker run -m 512m --cpus 0.1 -e PORT=10000` contra el PostgreSQL de compose: migra y liveness `200` en `/api/actuator/health/liveness`; registrar arranque y RSS en `docs/deployment.md`
- [x] 1.7 `render.yaml` en la raíz (design D3) y un test que lo lee y fija las variables, la rama y el health check

## 2. Frontend

- [x] 2.1 `core/config/apiBaseUrl.ts` (`resolveApiBaseUrl`) + test (desarrollo y producción, con y sin `VITE_API_URL`); `httpClient.ts` lo usa (design D4)
- [x] 2.2 Quitar `BACKEND_URL` (`vite.config.ts`, `vitest.config.ts`, `vite-env.d.ts`); `.env.example` con `VITE_API_URL`; tipar `VITE_API_URL`
- [x] 2.3 `vercel.json` en la raíz + test que fija: orden de los rewrites, destino `/api`, fallback, `outputDirectory`, filtro `dentdesk-frontend`, y que no hay rewrite de `/auth/*` ni `/actuator/*` (design D4)
- [x] 2.4 Build local idéntico al de Vercel desde la raíz (`pnpm install --frozen-lockfile` + build) y `pnpm validate` verde
- [x] 2.5 Prueba local de punta a punta con la base `/api`: backend + frontend en desarrollo, login, recarga con sesión (la cookie viaja a `/api/auth/refresh`) y una pantalla de paciente

## 3. Puesta en marcha (usuario, guiada)

- [x] 3.1 **(usuario)** Neon: proyecto en `aws-us-east-2`, roles por SQL y consulta de verificación de `pg_roles` (`docs/deployment.md` §4.2)
- [x] 3.2 **(usuario)** Render: Blueprint desde el repo en el workspace compartido con HSO; credenciales y `CORS_ALLOWED_ORIGINS=https://pendiente.invalid`; primer deploy migra **con el rol admin** y liveness `200`; en Neon, tablas de `odontorisas_admin` y consulta como `odontorisas_app` (design D6). Sin esto, no se abre a usuarios
- [x] 3.3 Ajustar la URL de Render en `vercel.json` si difiere de `dentdesk-api.onrender.com` (commit en `dev`, PR a `main`)
- [x] 3.4 **(usuario)** Vercel: importar el repo (raíz, rama `main`, sin `VITE_API_URL`); `CORS_ALLOWED_ORIGINS` en Render = origen de Vercel
- [ ] 3.5 **(usuario)** cron-job.org: liveness cada 10 min de 9:00 a 19:50 (`America/Lima`)

## 4. Verificación del despliegue

- [ ] 4.1 Matriz del design D7 (rutas, proxy, cookie, sesión en Chrome y Safari/iOS, CORS, Actuator, **RLS y aislamiento entre dos clínicas**, arranque en frío, soporte); resultados en `docs/deployment.md`
- [x] 4.2 Smoke E2E contra la URL pública (`E2E_BASE_URL=https://<app>.vercel.app pnpm exec playwright test e2e/auth.smoke.spec.ts`)

## 5. Docs

- [x] 5.1 Búsqueda final sin referencias a `/ingresar`, `/registro` ni al filtro de HSO en el repo
- [ ] 5.2 `docs/deployment.md`: base `/api` y liveness en `/api/actuator/health/liveness`; pasos concretos (Neon → Render → Vercel → monitor); rama de producción; CORS y previews; mediciones y matriz con resultados. `docs/backend.md` (base `/api`, cookie). `docs/vision.md` §8
- [ ] 5.3 `openspec validate add-cloud-deployment --strict`
