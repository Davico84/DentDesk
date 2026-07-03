## 1. Backend — redefinición de capas (project-foundation MODIFIED)

- [ ] 1.1 Crear paquete `service` (nivel raíz) y mover ahí la lógica de negocio; `service.<dominio>` con `mapper` co-ubicado
- [ ] 1.2 Dejar `infra` solo con adaptadores técnicos (`config`, `security`, `storage`, `mail`); renombrar `infra.utils` → `common`/`shared`
- [ ] 1.3 Actualizar `LayeredArchitectureTest`: `persistence` no→`presentation` ni→`service`; `presentation` no→`persistence`; `service` no→`presentation` (sin regla "infra no→service")
- [ ] 1.4 Actualizar `docs/architecture.md §2` (diagrama de capas) para reflejar `service` fuera de `infra`
- [ ] 1.5 `./mvnw -B verify` en verde tras el refactor (paquetes aún casi vacíos)

## 2. Backend — contrato OpenAPI (api-type-contracts)

- [ ] 2.1 Añadir dependencia `springdoc-openapi-starter-webmvc-*` v3.0.3 (compatible Spring Boot 4.x)
- [ ] 2.2 Verificar que `/v3/api-docs` responde `200 OK` con documento OpenAPI válido (Scenario "Exposición en runtime")
- [ ] 2.3 Configurar export del contrato en build: `springdoc-openapi-maven-plugin` (fallback `spring-boot:start` + `curl` si no soporta Boot 4) y **commitear el snapshot en `contracts/openapi.json`** (versionado, fuera de `target/`) (Scenario "Snapshot del contrato versionado")
- [ ] 2.4 Confirmar que las restricciones de Bean Validation de los DTOs aparecen en `contracts/openapi.json` (Scenario "Restricciones de validación reflejadas en el contrato")

## 3. Backend — modelo y persistencia (authentication)

- [ ] 3.1 Entidad `User` (`id`, `email` único, `password_hash`, `role` ADMIN/USER, timestamps) en `persistence.entity` + repositorio
- [ ] 3.2 Migración Flyway `V2__users.sql` (tabla `users` con índice único en `email`)
- [ ] 3.3 Persistencia de refresh tokens en **tabla DB** para rotación/revocación: entidad JPA + `V3__refresh_tokens.sql` + repositorio

## 4. Backend — seguridad y servicios (authentication)

- [ ] 4.1 `PasswordEncoder` BCrypt; utilidades JWT (emisión/validación access HS256 + refresh) en `infra.security`
- [ ] 4.2 `SecurityConfig` real: filtro JWT, rutas públicas (`/auth/**`, `/actuator/health`, `/v3/api-docs`), CORS con credenciales, CSRF acorde a cookie de refresh (`SameSite=Lax`)
- [ ] 4.3 `service.auth`: registro (rol `USER` por defecto, rechazo de correo duplicado), login (mensaje genérico en fallo), refresh (rotación + reintento), logout (invalidar refresh)
- [ ] 4.4 Emisión del refresh como cookie `HttpOnly`+`Secure`+`SameSite=Lax`; access en el cuerpo de respuesta

## 5. Backend — API (authentication)

- [ ] 5.1 DTOs `*Request`/`*Response` (`record`) con Bean Validation (`@Email`, `@NotBlank`, `@Size(min=8)` en password) — paridad con Zod (`docs/coding-standards.md §7`)
- [ ] 5.2 `AuthController` en `presentation`: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- [ ] 5.3 Manejo de errores: 409 correo duplicado, 401 credenciales/refresh inválidos, 400 validación (respuesta consistente)

## 6. Backend — tests (derivados de los Scenario)

- [ ] 6.1 Test de registro: éxito (crea + inicia sesión), correo duplicado (409, no duplica), (webmvc/security slice o IT)
- [ ] 6.2 Test de login: credenciales válidas (sesión), inválidas (401 + mensaje genérico, sin sesión)
- [ ] 6.3 Test de refresh: access expirado → nuevo access con refresh válido; refresh inválido/expirado → 401 (fuerza logout)
- [ ] 6.4 Test de logout: invalida el refresh (un refresh posterior con el token viejo falla)
- [ ] 6.5 Test de autorización por rol en endpoints protegidos (rol sin permiso → 403)

## 7. Frontend — codegen y cliente (api-type-contracts)

- [ ] 7.1 Añadir `orval` (devDep) + `orval.config.ts` (input `../../contracts/openapi.json` versionado, output `src/modules/core/services`, `client: react-query`)
- [ ] 7.2 Mutator axios en `core/services`: instancia con `withCredentials`, inyección de `Authorization` e interceptor 401 → refresh → reintento (Scenario refresh)
- [ ] 7.3 Script `generate:api`; generar tipos+hooks y **commitear los artefactos generados**; `pnpm typecheck` verde usándolos (Scenarios de generación/consumo y "Artefactos generados versionados")
- [ ] 7.4 Check de **drift en CI**: regenerar contrato + tipos y fallar si difieren de lo versionado (Scenario "Detección de drift en CI")

## 8. Frontend — sesión y módulo auth (authentication)

- [ ] 8.1 Store de sesión (Zustand, **no** persistido): access en memoria, usuario/rol, estados de carga
- [ ] 8.2 Bootstrap de sesión al montar la app: intento de refresh inicial (splash/carga) para restaurar sesión (Scenarios persistencia)
- [ ] 8.3 Pantallas `login` y `registro` con `react-hook-form` + `zod` (min 8 chars, email, campos requeridos — paridad con backend)
- [ ] 8.4 Acción de logout: limpia store y datos locales, redirige a login (Scenario logout)

## 9. Frontend — rutas y guardas (authentication)

- [ ] 9.1 `RouteGuard`: sin sesión en ruta privada → login; con sesión en `/auth` → panel; rol sin permiso → acceso denegado con volver (Scenarios de protección)
- [ ] 9.2 Redirección por rol tras login/registro (mapa rol → ruta de inicio)

## 10. Frontend — tests (Vitest + RTL + MSW, derivados de los Scenario)

- [ ] 10.1 Registro: éxito redirige; correo duplicado muestra error; password < 8 → error de validación sin petición (MSW)
- [ ] 10.2 Login: válidas → sesión+redirect; inválidas → mensaje genérico; vacíos → validación sin petición
- [ ] 10.3 Persistencia: recarga con sesión restaurable → autenticado; sin refresh válido → no autenticado y limpia datos
- [ ] 10.4 Refresh: 401 → refresca y reintenta transparente; refresh inválido → logout+login (MSW)
- [ ] 10.5 Guardas: ruta privada sin sesión → login; `/auth` con sesión → panel; rol sin permiso → acceso denegado

## 11. Docs y cierre

- [ ] 11.1 `openspec validate add-authentication --strict` en verde
- [ ] 11.2 `mvn verify` (backend) y `pnpm validate` (frontend) en verde; CI activa jobs
- [ ] 11.3 Al archivar: actualizar `docs/data-model.md` (entidad `User`, roles) y marcar `authentication` construida en `docs/roadmap.md` (gobernanza `CLAUDE.md`)
