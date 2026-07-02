## 1. Backend — proyecto base (Maven · Spring Boot 4.0.6)

- [ ] 1.1 Generar `backend/` con `pom.xml` (Java 25, Spring Boot 4.0.6) y Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`)
- [ ] 1.2 Añadir dependencias base: web, actuator, data-jpa, validation, security, cache + caffeine, flyway, springdoc-openapi, lombok, postgresql (runtime), spring-ai (chat memory jdbc)
- [ ] 1.3 Crear la aplicación principal y los paquetes de capas: `presentation` (controller, dto), `infra` (config, security, service, utils/mappers), `persistence` (entity, repository, specification)
- [ ] 1.4 Configurar perfiles: `application.yml` base + `application-{dev,qa,pro}.yml`; secretos en `application-dev-secrets.yml` (ignorado) con `*.example` versionado
- [ ] 1.5 Configurar Flyway (`spring.jpa.hibernate.ddl-auto=none`) y migración inicial `db/migration/V1__init.sql`
- [ ] 1.6 Configurar Actuator health y springdoc (Swagger habilitado por perfil)
- [ ] 1.7 Añadir `compose.yaml` mínimo con PostgreSQL para desarrollo local

## 2. Backend — pruebas (test slices + Testcontainers)

- [ ] 2.1 Configurar Testcontainers (PostgreSQL) + `spring-boot-testcontainers`; base común de test (`@ServiceConnection`)
- [ ] 2.2 Test de contexto/arranque (`@SpringBootTest`) sobre Testcontainers — cubre "Arranque en perfil dev"
- [ ] 2.3 Test webmvc del endpoint de salud → 200 OK
- [ ] 2.4 Test de Flyway que verifica migración en base limpia (contenedor) y que Hibernate no altera el esquema
- [ ] 2.5 Verificar `./mvnw -B verify` en verde (requiere Docker local para Testcontainers)

## 3. Frontend — proyecto base (Vite · React 19 · TS)

- [ ] 3.1 Generar `frontend/` con `package.json` (scripts dev/build/preview/lint/typecheck/test/validate) portando versiones del template FixRiver
- [ ] 3.2 Portar configs: `vite.config.ts`, `tsconfig*`, `eslint.config`, Tailwind/PostCSS
- [ ] 3.3 Crear estructura `src/{layouts,screens,modules(core/),store,routes,styles}` con una `home` mínima y `routes` base
- [ ] 3.4 Portar tooling de test: `vitest.config.ts`, `src/test/{setup.ts,utils.tsx,mocks/handlers.ts,mocks/server.ts}` (MSW)
- [ ] 3.5 Instalar dependencias y generar `pnpm-lock.yaml`

## 4. Frontend — pruebas

- [ ] 4.1 Prueba con React Testing Library que renderiza la app (home) — cubre "Prueba base con MSW"
- [ ] 4.2 Verificar `pnpm build` y `pnpm validate` en verde

## 5. Validación de commits (raíz)

- [ ] 5.1 `package.json` raíz mínimo con devDeps de hooks (husky, @commitlint/cli, @commitlint/config-conventional)
- [ ] 5.2 Configurar husky + hook `commit-msg` que corre commitlint
- [ ] 5.3 Hook `pre-commit` que delega en el `lint-staged` del frontend (acotado a `frontend/`)
- [ ] 5.4 Probar: commit inválido se rechaza; commit `feat(scope): ...` pasa

## 6. CI y cierre

- [ ] 6.1 Verificar que los jobs `backend` y `frontend` de `.github/workflows/ci.yml` se activan al existir los módulos
- [ ] 6.2 `openspec validate add-project-scaffolding --strict` en verde
- [ ] 6.3 Actualizar `docs/architecture.md` §6 (marcar andamiaje hecho) y crear `.env.example`
