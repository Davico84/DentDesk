# Arquitectura y Stack — OdontoRisas

Decisión de stack **cerrada el 2026-07-02**. Este documento es la fuente de verdad del *cómo* construimos.
El *qué* (capacidades + cambios) vive en `openspec/`. El flujo de trabajo, en `SETUP-OPENSPEC.md`.

Referencias base:
- Estructura de repo: **monorepo modular** (`modules/`), con `docs/` y `openspec/` en la raíz — alineada con el template FixRiver (`modules/frontend`); del demo `lidr-specboot-main` se tomó la idea de docs/specs en la raíz.
- Backend: especificación del equipo (Java 25 / Spring Boot).
- Frontend: template FixRiver `modules/frontend/proyecto-base` (versiones reales tomadas de su `package.json`).

---

## 1. Estructura del repositorio (monorepo modular)

```
OdontoRisas/
├─ openspec/              # specs + changes (qué hace el sistema)
│  └─ config.yaml         # schema spec-driven + contexto/reglas para la IA
├─ docs/                  # estándares perennes (este archivo, backend, frontend, testing, adr/)
├─ modules/backend/               # Java 25 · Maven · Spring Boot (app principal)
├─ modules/frontend/              # React 19 · Vite · TypeScript (SPA)
├─ .github/workflows/     # CI
├─ CLAUDE.md              # guía para agentes → apunta a docs/ y openspec/
└─ SETUP-OPENSPEC.md      # setup y flujo OpenSpec
```

---

## 2. Backend — Java 25 · Spring Boot 4.0.6

| Área | Tecnología |
|---|---|
| Lenguaje / build | Java 25 · Maven |
| Framework | Spring Boot 4.0.6 |
| API REST | Spring Web MVC + springdoc-openapi (Swagger, por perfil) |
| Realtime | Spring WebSocket (STOMP) — **planeado, aún no implementado** (sin `spring-boot-starter-websocket` en el `pom.xml`); encajará con `@stomp/stompjs` + `sockjs` del front cuando se construya (`docs/vision.md §7`) |
| Persistencia | Spring Data JPA (Hibernate) + PostgreSQL |
| Migraciones | Flyway (`db/migration`) · `ddl-auto=none` (schema lo maneja Flyway) |
| Seguridad | Spring Security + JWT (`com.auth0:java-jwt`) |
| Validación | Bean Validation (`starter-validation`) |
| Caché | Spring Cache + Caffeine |
| Almacenamiento | AWS S3 (subida de imágenes) |
| IA | Spring AI (chat memory JDBC) — para el bot |
| Utilidades | Lombok · mail vía webhook |
| Configuración | Única (sin perfiles); todos los valores externalizados como secretos (env vars + `secrets.properties` local ignorado) |
| Testing | Test slices: H2, JPA-test, security-test, webmvc-test, websocket-test, flyway-test |

### Arquitectura por capas (paquetes reales)
```
presentation   → controller · dto
      ↓
service        → <dominio> (+ mapper co-ubicado)      # lógica de negocio / casos de uso
      ↓
persistence    → entity · repository · specification (queries dinámicas)

infra          → config · security · storage · mail   # adaptadores técnicos (transversal)
common         → utilidades / tipos compartidos
```
- La **lógica de negocio** vive en `service.<dominio>`, **no** en `infra`. `infra` es solo adaptadores técnicos (framework, seguridad, S3, mail).
- `presentation` no accede a `persistence` directamente: pasa por `service`.
- `persistence` no depende de `presentation` ni de `service` (las entidades JPA son el modelo de dominio; no hexagonal puro, decisión consciente).
- `specification` centraliza las queries dinámicas (JPA Criteria/Specifications).
- Migraciones versionadas en `db/migration`; el schema NO lo gestiona Hibernate (`ddl-auto=none`).

---

## 3. Frontend — React 19 · Vite · TypeScript

Base: template FixRiver `modules/frontend/proyecto-base`.

| Área | Tecnología |
|---|---|
| Core | React 19 · TypeScript · Vite |
| UI | Tailwind CSS 3 + shadcn/Radix · `lucide-react` · `sonner` · `framer-motion` · `@base-ui/react`¹ · `recharts`¹ |
| Datos servidor | `@tanstack/react-query` + `axios` |
| Estado cliente | Zustand |
| Formularios | `react-hook-form` + `zod` (`@hookform/resolvers`) |
| Routing | `react-router-dom` v7 + guards centrales `RequireAuth`/`RequireGuest` (roles ADMIN/USER; sin `roles` = cualquier autenticado) |
| Realtime | `@stomp/stompjs` + `sockjs-client`¹ |
| Testing | Vitest + React Testing Library + MSW · e2e con Playwright |
| Calidad | ESLint + typescript-eslint · pre-commit con `lint-staged` |

¹ **Stack cerrado, no instalado todavía.** Se agrega la primera vez que una capacidad lo necesite de
verdad, no antes (detalle por librería en `docs/frontend.md §1`, `§4.1` y `§4.2`).
| Gestor | pnpm |

### Arquitectura orientada a pantallas (Screens)
```
src/
├─ layouts/     # RootLayout (bootstrap sesión+marca, hoy); AppLayout planeado (Sidebar+Header, nav por rol)
├─ screens/     # vistas por dominio (admin/, auth/, home) — orquestan, no conectan servicios
├─ modules/     # lógica de negocio encapsulada y portable
│  ├─ core/     # components, ui (shadcn), services, utils, enum — sin deps a otros módulos
│  ├─ auth/     # servicios/lógica de autenticación
│  └─ [dominio]/
├─ store/       # stores Zustand (estado cliente)
├─ routes/      # definición de rutas + constantes PATHS
└─ styles/      # estilos globales / temas
```
- **Screens** orquestan e instancian componentes de módulos; no llaman a axios ni renderizan tablas directas.
- **Modules** encapsulan lógica reutilizable; `core/` no depende de otros módulos.
- **`RequireAuth`/`RequireGuest`** centralizan auth + roles: sesión activa en ruta pública (`RequireGuest`) → redirige por rol; sin sesión en ruta privada (`RequireAuth`) → login (con `?next=`); rol no permitido → `AccessDenied`.

Detalle ampliado de los estándares de frontend en `docs/frontend.md` (arquitectura pantallas/módulos, React Query, Zustand, patrones de UI) y `docs/coding-style.md` (estilo general de código). Adaptados del template FixRiver.

---

## 3.5 Multi-tenancy (SaaS)

Capa de plataforma **reutilizable** que aísla los datos de cada organización (tenant). Detalle en `openspec/specs/multi-tenancy/` y reglas de persistencia en `docs/backend.md §8.1`.

- **Modelo**: `User` = identidad global (correo único, sin rol ni organización) · `Organization` = el tenant (`slug` inmutable, `type`, `status`; `OrganizationSettings` 1:1) · `Membership` = puente identidad↔organización que **lleva el rol**. Un usuario puede tener varias membresías (multi-organización); en el MVP el registro crea una y el selector se difiere a `users`.
- **Aislamiento en dos claves (Row-Level Security)**: tablas de **negocio** aisladas por `organization_id`; `memberships` por **identidad** (`user_id`); `users`/`refresh_tokens` sin RLS (se leen por credencial). RLS con `@TenantId` (Hibernate filtra en app) **+** políticas RLS en PostgreSQL (barrera de motor). Hibernate 7.2 filtra en app; la generación automática de RLS llega en Hibernate 8.
- **Contexto por petición**: `JwtAuthenticationFilter` lee `sub`/`oid` del token firmado → `IdentityContext`/`OrganizationContext` (`ThreadLocal`, limpiados en `finally`) → alimentan al `CurrentTenantIdentifierResolver` y a `set_config('app.user_id'|'app.organization_id', …, true)` (transaction-local) que consumen las políticas RLS.
- **Dos roles de BD (menor privilegio)**: `odontorisas_admin` (owner, solo Flyway/DDL) + `odontorisas_app` (runtime, solo DML, **no** owner). Como la app no es owner, RLS le aplica de forma **ineludible** y una inyección no puede desactivarla; `FORCE ROW LEVEL SECURITY` como defensa en profundidad. **Ningún rol de app puede ser `SUPERUSER` ni tener `BYPASSRLS`** (saltan RLS). Flyway usa credenciales propias sobre el mismo datasource; en local caen a las de la app si no se definen (ver `secrets.properties.example` y `scripts/db-roles.sql`).
- **Observabilidad**: `organizationId`/`userId` en el MDC junto al `traceId`.

---

## 4. Contrato back ↔ front

- **Auth**: JWT emitido por Spring Security; el front lo guarda y lo envía vía `axios` (interceptor). Refresh de token ante 401.
- **Roles**: ADMIN / USER (alineados con el tipo `Role` del front — `store/useSessionStore.ts` — y la seguridad por rol del back).
- **Realtime**: STOMP sobre WebSocket — **planeado, aún no implementado** en ningún lado del contrato (`docs/vision.md §7`).
- **API docs**: springdoc-openapi (Swagger) por perfil como contrato de referencia.

---

## 5. Calidad y CI

- **Gate de spec** (antes de codear): `openspec validate <change> --strict` + revisión humana.
- **Backend**: `mvn verify` (compila + test slices).
- **Frontend**: `pnpm validate` (typecheck + lint + test); e2e Playwright cuando aplique.
- **CI por PR**: `openspec validate --strict` + validate de backend + frontend.
- **Commits**: Conventional Commits. **Pre-commit** (front): `lint-staged`.
- **Seguridad y observabilidad (planeado, aún NO adoptado)**: escaneo de vulnerabilidades de dependencias (empezar por **GitHub Dependabot** + `pnpm audit` / OWASP Dependency-Check en CI; **Snyk** si se requiere SAST, licencias o escaneo de contenedores, con cuenta/`SNYK_TOKEN`) y monitoreo de errores en runtime con **Sentry** (SDK front React + back Spring Boot, correlacionable con el `traceId` de RFC 9457). Se introducen con el setup de deploy/entornos. Ver `docs/vision.md §7`.

---

## 5.1 Despliegue y escalado horizontal

**La API es stateless, así que escala horizontal sin *sticky sessions*.** Cualquier instancia puede servir cualquier petición. Esto no es casualidad: cae solo de las decisiones de `authentication` y `multi-tenancy`, y **hay que no romperlo**.

| Decisión | Por qué permite balancear |
|---|---|
| `SessionCreationPolicy.STATELESS` | **No hay sesión en memoria del servidor** — la propiedad clave |
| Access token = JWT | Se valida con la firma, sin consultar a nadie |
| Refresh token **persistido en BD** (hash) | Rotación y revocación funcionan entre instancias, no en la RAM de una |
| Contexto de tenant desde el claim `oid` **en cada petición** | `ThreadLocal` que se limpia en el `finally`: cero estado entre peticiones |
| RLS con `set_config(..., true)` | Es **transaction-local**: sirve con cualquier pool o instancia |
| `ddl-auto=none` + Flyway | Flyway **bloquea** su tabla de historial: N réplicas arrancando a la vez es seguro (una migra, las demás esperan) |
| Imágenes → S3 | Nada en el disco local de la instancia |
| `/actuator/health` expuesto | El balanceador (o k8s) ya tiene su health check |

**Lo único que hacía falta añadir:**

```yaml
server:
  forward-headers-strategy: framework
```

Detrás de un proxy que termina TLS, la petición llega por `http` aunque el cliente usara `https`. Sin esto, la **cookie `Secure` del refresh token** no se comporta como debe y las URLs absolutas salen con el esquema equivocado. Con `framework`, Spring respeta las cabeceras `X-Forwarded-*`.

> ⚠️ **Lo que SÍ rompería el balanceo** (hoy no existe, pero llegará):
> - **`@Scheduled`**: un job correría en **todas** las réplicas (trabajo duplicado) → necesitaría ShedLock o *leader election*. *(Hay un tripwire de ArchUnit que rompe la build si entra `@Async`/`@Scheduled` sin resolver la propagación de contexto.)*
> - **WebSocket/STOMP** (las `notifications` de `vision.md §7`): son stateful por conexión → sticky sessions o un *broker relay* (Redis/RabbitMQ).
> - **Rate limiting en memoria**: no funcionaría entre réplicas → necesitaría Redis.

## 6. Andamiaje (change `add-project-scaffolding`)

- [x] `modules/backend/`: proyecto Maven + Spring Boot (capas, config única+secrets, Flyway).
- [x] `modules/frontend/`: Vite + React (estructura screens/modules, tooling Vitest/MSW/ESLint).
- [x] `docs/`: testing, coding-standards, react-query, zustand, ui-patterns, tooling-setup, backend-standards, capabilities, roadmap, data-model.
- [x] `docs/adr/0001` — decisión de stack y estructura.
- [x] `.github/workflows/ci.yml` (openspec + backend + frontend).
- [x] `CLAUDE.md` raíz + commitlint/husky + pre-commit lint-staged.
