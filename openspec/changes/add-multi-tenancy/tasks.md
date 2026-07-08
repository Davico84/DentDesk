## 1. Base de datos

- [ ] 1.1 Crear `V6__clinics_and_memberships.sql`: tabla `clinics` (`id` identidad, `name`, `slug` único, `status`, timestamps)
- [ ] 1.2 En `V6`: tabla `memberships` (`id`, `user_id` FK, `clinic_id` FK, `role`, `status`, timestamps, único `(user_id, clinic_id)`)
- [ ] 1.3 En `V6`: backfill idempotente — si hay usuarios, crear clínica por defecto y una membresía por usuario arrastrando su `users.role`; si `users` está vacía, no crear nada
- [ ] 1.4 En `V6`: `ALTER TABLE users DROP COLUMN role`; añadir `refresh_tokens.clinic_id` (backfill desde la membresía y luego `NOT NULL`)
- [ ] 1.5 Documentar en `secrets.properties.example` que `DB_USERNAME` **no** puede ser `SUPERUSER` ni tener `BYPASSRLS` (design.md D7)

## 2. Backend — modelo de identidad, clínica y membresía

- [ ] 2.1 `persistence/entity/Clinic.java` (+ enum de estado) y `ClinicRepository` con búsqueda por `slug`
- [ ] 2.2 `persistence/entity/Membership.java` (+ enum de estado) y `MembershipRepository` con búsqueda por `user_id` y por `(user_id, clinic_id)`
- [ ] 2.3 Refactor de `User`: eliminar `role`; añadir `status` (design.md D4, Open Question 6); la identidad queda global
- [ ] 2.4 `RefreshToken`: añadir asociación con `Clinic` (clínica activa de la sesión)
- [ ] 2.5 Servicio de derivación y desambiguación de `slug` a partir del nombre de la clínica (Open Question 5)

## 3. Backend — mecanismo de aislamiento (design.md D6, D7, D10)

- [ ] 3.1 `common/ClinicContext.java` (`ThreadLocal<Long>`) con `set` / `get` / `clear`
- [ ] 3.2 `CurrentTenantIdentifierResolver<Long>` que lee de `ClinicContext`; definir comportamiento sin contexto (Open Question 4) y registrarlo en Hibernate 7.2
- [ ] 3.3 Propagación `SELECT set_config('app.clinic_id', ?, true)` al inicio de cada transacción (Interceptor de Hibernate vs `DataSource` delegado — Open Question 1); se valida haciendo pasar el test 6.2
- [ ] 3.4 Convención de RLS por tabla de negocio: helper/plantilla SQL reutilizable (`ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `clinic_isolation`) lista para que la consuman las capacidades de negocio

## 4. Backend — autenticación con identidad y clínica activa

- [ ] 4.1 `AccessClaims`: `(userId, clinicId, email, role)`; emitir/leer los claims `cid` y `role` en `TokenService`
- [ ] 4.2 `JwtAuthenticationFilter`: poblar `ClinicContext` desde `cid`, exponer el rol a Spring Security y **limpiar el contexto en un `finally`**; rechazar como no autenticado un token válido sin `cid`
- [ ] 4.3 `AuthService.register`: crear `Clinic` + `User` + `Membership(ADMIN)` en una única `@Transactional` (design.md D8); `RegisterCommand` +`clinicName`
- [ ] 4.4 `AuthService.login`: autenticar por identidad; cargar la (única) membresía y emitir el token con su clínica y rol (design.md D5)
- [ ] 4.5 `AuthService.refresh`: conservar la clínica activa del refresh token y releer el rol vigente de la membresía
- [ ] 4.6 `RegisterRequest` +`clinicName` con Bean Validation; `UserResponse` expone la clínica activa (nombre, `slug`) y el rol

## 5. Contrato y frontend

- [ ] 5.1 Regenerar `contracts/openapi.json` y los artefactos de orval (`pnpm generate:api`); comprobar el gate "sin drift" de `api-type-contracts`
- [ ] 5.2 `RegisterForm`: campo "Nombre de la clínica" con validación Zod en paridad con Bean Validation (`docs/coding-style.md §7`)
- [ ] 5.3 Store de sesión (Zustand): exponer la clínica activa y el rol a partir de la respuesta de auth; ajustar `RouteGuard` para leer el rol de la sesión (cambia el origen, no el uso)
- [ ] 5.4 Tests de frontend (Vitest + MSW): "Nombre de clínica ausente (validación en cliente)" y "Registro exitoso"

## 6. Tests derivados de los Scenario

- [ ] 6.1 Introducir el slice de integración con **PostgreSQL real (Testcontainers)** para RLS (Open Question 2); confirmar que encaja con `mvn verify` en CI
- [ ] 6.2 `multi-tenancy` — "Una consulta que omite el filtro de clínica sigue estando aislada": entidad-fixture con `@TenantId` + migración de test con RLS; consulta sin `WHERE clinic_id` que RLS restringe. **Es el test que valida D6, D7 y D10**; fallaría si el rol de BD hiciera bypass
- [ ] 6.3 `multi-tenancy` — "Una consulta de negocio solo alcanza datos de su propia clínica", "Acceso directo por identificador a un recurso de otra clínica", "Operación de negocio sin clínica en el contexto" (sobre la entidad-fixture)
- [ ] 6.4 `multi-tenancy` — "El contexto se deriva del token", "El cliente no puede suplantar la clínica", "Token de acceso sin clínica activa" (security-test / webmvc-test)
- [ ] 6.5 `multi-tenancy` — "El contexto no sobrevive a la petición": dos peticiones consecutivas de clínicas distintas en el mismo hilo
- [ ] 6.6 `multi-tenancy` — "Alta correcta de una clínica", "El alta es atómica", "Dos clínicas con el mismo nombre", "Nombre de clínica ausente o inválido"
- [ ] 6.7 `multi-tenancy` — "Un correo identifica a una única persona", "El rol pertenece a la membresía, no a la identidad", "El rol de administrador está acotado a su clínica"
- [ ] 6.8 `authentication` — actualizar los tests de registro (crea `Clinic`+`User`+`Membership` ADMIN, `clinicName`), login (token con `cid`+rol de membresía) y refresh (`cid` conservado, rol releído)

## 7. Documentación (gobernanza del ciclo, `docs/vision.md §4`)

- [ ] 7.1 `docs/vision.md`: §1 producto = SaaS multi-clínica; §2 nueva fila `multi-tenancy` (técnica); §3 marcar 🚧 y enlazar este change; §6 nota de secuencia (ninguna capacidad de negocio antes de ésta); §7 mencionar la facturación SaaS como candidata futura, distinta de `billing`
- [ ] 7.2 `docs/domain.md`: `User` como identidad global; entidades `Clinic` y `Membership`; actor administrador de clínica; glosario (clínica/tenant, membresía, aislamiento); `clinicId` en las entidades de negocio; ER actualizado (rol movido a `Membership`)
- [ ] 7.3 `docs/architecture.md`: sección "Multi-tenancy" (identidad vs tenant vs membresía; discriminador + RLS sobre negocio; resolución del contexto; `set_config` por transacción; requisito del rol de BD; H2 no prueba RLS → Testcontainers)
- [ ] 7.4 `docs/backend.md`: regla de persistencia — toda `@Entity` de negocio lleva `@TenantId` + `clinic_id`, y su migración aplica `ENABLE`/`FORCE ROW LEVEL SECURITY` + policy `clinic_isolation`; prohibido `nativeQuery` sin filtro de clínica; `open-in-view` debe seguir en `false`
- [ ] 7.5 `docs/testing.md`: el test de aislamiento pasa a ser obligatorio por capacidad de negocio, y se ejecuta sobre PostgreSQL real (Testcontainers), no H2

## 8. Cierre

- [ ] 8.1 `openspec validate add-multi-tenancy --strict`
- [ ] 8.2 `mvn verify` (backend, incluye el test de fixture con Testcontainers) y `pnpm validate` (frontend) en verde
- [ ] 8.3 Actualizar `design.md` con la resolución de las Open Questions
- [ ] 8.4 Commits por scope (backend / frontend / docs / openspec), Conventional Commits en español (`docs/commits.md`)
