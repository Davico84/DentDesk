## 1. Base de datos

- [ ] 1.0 **Roles de BD (design.md D7)**: provisión de `odontorisas_admin` (owner/Flyway) y `odontorisas_app` (runtime, DML, no-owner) — script/instrucciones idempotentes (Open Question 6); `application.yml` con `spring.flyway.user`=admin y `spring.datasource.username`=app; `secrets.properties.example` con **dos** juegos de credenciales y la nota de que ningún rol de app puede ser `SUPERUSER`/`BYPASSRLS`
- [ ] 1.1 `ALTER DEFAULT PRIVILEGES FOR ROLE odontorisas_admin GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES` (+ `USAGE,SELECT ON SEQUENCES`) `TO odontorisas_app`; GRANT sobre `users`/`refresh_tokens` existentes
- [ ] 1.2 Crear `V6__organizations.sql`: tabla `organizations` (`id` identidad, `name`, `slug` único, `type`, `status`, timestamps)
- [ ] 1.3 En `V6`: tabla `organization_settings` (`organization_id` PK/FK 1:1, `timezone`, `locale`, `currency`)
- [ ] 1.4 En `V6`: tabla `memberships` (`id`, `user_id` FK, `organization_id` FK, `role`, `status`, timestamps, único `(user_id, organization_id)`)
- [ ] 1.5 En `V6`: backfill idempotente — si hay usuarios, crear organización por defecto (`type = CLINIC`) + settings y una membresía por usuario arrastrando su `users.role`; si `users` está vacía, no crear nada
- [ ] 1.6 En `V6`: `ALTER TABLE users ADD COLUMN status DEFAULT 'ACTIVE'`; `ALTER TABLE users DROP COLUMN role` (`refresh_tokens` **no** cambia); GRANT de las tablas nuevas al rol de app
- [ ] 1.7 Crear `V7__rls.sql`: `ENABLE`/`FORCE ROW LEVEL SECURITY` sobre `memberships` + política de identidad `membership_identity_isolation` (`user_id = current_setting('app.user_id')::bigint`, **sin** `missing_ok` → fail-closed ruidoso)

## 2. Backend — modelo de plataforma

- [ ] 2.1 `Organization.java` (+ enums `OrganizationType` con `CLINIC` y `OrganizationStatus`) y `OrganizationRepository` (búsqueda por `slug`); `slug` inmutable
- [ ] 2.2 `OrganizationSettings.java` (1:1 con `Organization`, defaults `timezone`/`locale`/`currency`) y su repositorio
- [ ] 2.3 `Membership.java` (+ enum `MembershipStatus`, `role` reutilizando `Role`) y `MembershipRepository` (por `user_id` y por `(user_id, organization_id)`)
- [ ] 2.4 Refactor de `User`: eliminar `role`; añadir `status` (`UserStatus` = `ACTIVE|LOCKED|DISABLED`); la identidad queda global
- [ ] 2.5 Servicio de derivación y desambiguación de `slug` a partir del nombre (Open Question 4)

## 3. Backend — mecanismo de aislamiento (design.md D6, D7, D10)

- [ ] 3.1 `common/IdentityContext.java` y `common/OrganizationContext.java` (`ThreadLocal<Long>`) con `set`/`get`/`clear`
- [ ] 3.2 `CurrentTenantIdentifierResolver<Long>` que lee de `OrganizationContext`; comportamiento sin contexto (Open Question 3); registrarlo en Hibernate 7.2
- [ ] 3.3 Propagación por transacción en **una sola sentencia**: `SELECT set_config('app.user_id', ?, true), set_config('app.organization_id', ?, true)` desde ambos contextos (Interceptor de Hibernate vs `DataSource` delegado — Open Question 1); se valida con los tests 6.2 y 6.3
- [ ] 3.4 **Alias `set_config` en H2** (Open Question 7): función no-op de test + `CREATE ALIAS`, para que los slices con H2 no rompan al abrir transacción; producción agnóstica
- [ ] 3.5 **Regla transaccional (design.md D12)**: test ArchUnit que exija `@Transactional` en el acceso a entidades bajo RLS (`memberships` y futuras de negocio)
- [ ] 3.6 Convención de RLS por tabla de negocio: helper/plantilla SQL reutilizable (`ENABLE`/`FORCE` + policy `organization_isolation`) documentada para las capacidades de negocio

## 4. Backend — autenticación con identidad y organización activa

- [ ] 4.1 `AccessClaims`: `(userId, organizationId, email, role)`; emitir/leer los claims `sub`, `oid` y `role` en `TokenService`
- [ ] 4.2 `JwtAuthenticationFilter`: poblar `IdentityContext` (desde `sub`) y `OrganizationContext` (desde `oid`), exponer el rol a Spring Security y **limpiar ambos contextos en un `finally`**; rechazar como no autenticado un token válido sin `oid`
- [ ] 4.3 `AuthService.register`: crear `Organization` + `OrganizationSettings` + `User` + `Membership(ADMIN)` en una única `@Transactional`, fijando `app.user_id` antes de insertar la membresía (design.md D8); `RegisterCommand` +nombre de organización
- [ ] 4.4 `AuthService.login`: autenticar por identidad; fijar `app.user_id`; cargar la (única) membresía y emitir el token con su organización y rol (design.md D5)
- [ ] 4.5 `AuthService.refresh`: derivar la organización activa vía `User → Membership`; releer el rol vigente; emitir el token con `oid` y rol
- [ ] 4.6 `RegisterRequest` +nombre de organización con Bean Validation; `UserResponse` expone la organización activa (nombre, `slug`) y el rol

## 5. Contrato y frontend

- [ ] 5.1 Regenerar `contracts/openapi.json` y los artefactos de orval (`pnpm generate:api`); comprobar el gate "sin drift" de `api-type-contracts`
- [ ] 5.2 `RegisterForm`: campo "Nombre de la clínica" (mapea a `Organization.name`) con validación Zod en paridad con Bean Validation (`docs/coding-style.md §7`)
- [ ] 5.3 Store de sesión (Zustand): exponer la organización activa y el rol a partir de la respuesta de auth; ajustar `RouteGuard` para leer el rol de la sesión (cambia el origen, no el uso)
- [ ] 5.4 Tests de frontend (Vitest + MSW): "Nombre de organización ausente (validación en cliente)" y "Registro exitoso"

## 6. Tests derivados de los Scenario

- [ ] 6.1 Introducir el slice de integración con **PostgreSQL real (Testcontainers)** para RLS (Open Question 2); confirmar que encaja con `mvn verify` en CI
- [ ] 6.2 `multi-tenancy` — "Un usuario solo ve sus propias membresías" y "Una consulta de membresías que omite el filtro sigue estando aislada": **RLS de identidad real sobre `memberships`**; consulta sin `WHERE user_id` que RLS restringe. Fallaría si el rol de BD hiciera bypass (valida D7)
- [ ] 6.3 `multi-tenancy` — "Una consulta que omite el filtro sigue estando aislada" (organización): entidad-fixture con `@TenantId` + migración de test con RLS; consulta sin `WHERE organization_id` que RLS restringe (valida D6, D10)
- [ ] 6.4 `multi-tenancy` — "Una consulta de negocio solo alcanza datos de su propia organización", "Acceso directo por identificador a un recurso de otra organización", "Operación de negocio sin organización en el contexto" (sobre la entidad-fixture)
- [ ] 6.5 `multi-tenancy` — "El contexto se deriva del token", "El cliente no puede suplantar la organización", "Token de acceso sin organización activa" (security-test / webmvc-test)
- [ ] 6.6 `multi-tenancy` — "El contexto no sobrevive a la petición": dos peticiones consecutivas de organizaciones distintas en el mismo hilo
- [ ] 6.7 `multi-tenancy` — "Alta correcta de una organización", "El alta es atómica", "Dos organizaciones con el mismo nombre", "El slug no cambia al renombrar", "Nombre de organización ausente o inválido"
- [ ] 6.8 `multi-tenancy` — "Un correo identifica a una única persona", "El rol pertenece a la membresía, no a la identidad", "El rol de administrador está acotado a su organización", "Deshabilitar una membresía no deshabilita la identidad"
- [ ] 6.9 `authentication` — actualizar los tests de registro (crea `Organization`+`Settings`+`User`+`Membership` ADMIN), login (token con `oid`+rol de membresía) y refresh (`oid` derivado, rol releído)

## 7. Documentación (gobernanza del ciclo, `docs/vision.md §4`)

- [ ] 7.1 `docs/vision.md`: §1 producto = SaaS multi-tenant (capa de plataforma reutilizable); §2 nueva fila `multi-tenancy` (técnica); §3 marcar 🚧 y enlazar este change; §6 secuencia (ninguna capacidad de negocio antes de ésta); §7 facturación SaaS como candidata futura, distinta de `billing`
- [ ] 7.2 `docs/domain.md`: `User` como identidad global; entidades `Organization`, `OrganizationSettings`, `Membership`; actor administrador de organización; glosario (organización/tenant, membresía, aislamiento, identidad); `organizationId` en las entidades de negocio; los dos `status`; ER actualizado (rol movido a `Membership`)
- [ ] 7.3 `docs/architecture.md`: sección "Multi-tenancy" (identidad vs tenant vs membresía; RLS de dos claves; resolución del contexto; `set_config` combinado por transacción; **dos roles de BD** admin/app con menor privilegio; H2 no prueba RLS → Testcontainers)
- [ ] 7.4 `docs/backend.md`: regla de persistencia (toda `@Entity` de negocio lleva `@TenantId` + `organization_id` y su migración aplica `ENABLE`/`FORCE` + policy `organization_isolation` **sin `missing_ok`**; `open-in-view` en `false`); **regla transaccional** (acceso a entidades RLS siempre en `@Transactional`, enforced por ArchUnit — D12); **regla de acceso** (el `organizationId`/`userId` se leen solo de `OrganizationContext`/`IdentityContext`, nunca de `@RequestHeader`/`@PathVariable`/`@RequestBody`); **convención async** (D13: `TaskDecorator` para async-de-request, contexto explícito para jobs de fondo); prohibido `nativeQuery` sin filtro
- [ ] 7.5 `docs/testing.md`: el test de aislamiento pasa a ser obligatorio por capacidad de negocio, y se ejecuta sobre PostgreSQL real (Testcontainers), no H2

## 8. Cierre

- [ ] 8.1 `openspec validate add-multi-tenancy --strict`
- [ ] 8.2 `mvn verify` (backend, incluye RLS de `memberships` + fixture de organización con Testcontainers) y `pnpm validate` (frontend) en verde
- [ ] 8.3 Actualizar `design.md` con la resolución de las Open Questions
- [ ] 8.4 Commits por scope (backend / frontend / docs / openspec), Conventional Commits en español (`docs/commits.md`)
