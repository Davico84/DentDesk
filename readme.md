# OdontoRisas

**SaaS multi-clínica** de gestión dental: varias clínicas (organizaciones) con datos **aislados** gestionan pacientes (menores y adultos), citas, tratamientos con odontograma, imágenes, facturación e inventario, con acceso por rol dentro de su organización.

Proyecto **greenfield** desarrollado con **especificaciones dirigidas (spec-driven) usando [OpenSpec](https://github.com/Fission-AI/OpenSpec)**. Monorepo modular: **backend** Java/Spring + **frontend** React/Vite.

---

## Filosofía: spec-driven

Todo el desarrollo gira en torno a **capacidades** (capabilities), y cada capacidad se **especifica antes de construirse**. Dos preguntas, dos fuentes de verdad:

| Pregunta | Vive en |
|---|---|
| **¿Qué hace el sistema?** (capacidades + cambios) | `openspec/` |
| **¿Cómo lo construimos?** (estándares perennes) | `docs/` |

> Las **capacidades = specs**. No se escribe código de una capacidad sin su spec.

---

## Estructura del proyecto

```
OdontoRisas/
├─ openspec/                  # 🧭 QUÉ hace el sistema (OpenSpec)
│  ├─ specs/                  #   capacidades YA CONSTRUIDAS (1 carpeta = 1 capacidad)
│  │  ├─ project-foundation/spec.md
│  │  ├─ authentication/spec.md
│  │  └─ api-type-contracts/spec.md
│  ├─ changes/                #   capacidades EN PROGRESO (propuestas de cambio)
│  │  ├─ add-multi-tenancy/   #     proposal.md · design.md · tasks.md · specs/ (delta)
│  │  └─ archive/             #     changes ya archivados (add-authentication, ...)
│  └─ config.yaml             #   contexto del proyecto para la IA
│
├─ docs/                      # 📚 CÓMO construimos (estándares perennes)
│  ├─ vision.md               #   ⭐ catálogo/roadmap de capacidades + gobernanza del ciclo
│  ├─ domain.md               #   modelo de dominio: actores, glosario, entidades, ER
│  ├─ architecture.md         #   stack cerrado + arquitectura back/front
│  ├─ backend.md              #   estándares Java/Spring (capas, DTOs, JPA, seguridad)
│  ├─ frontend.md             #   estándares frontend (React Query, Zustand, UI, capa API)
│  ├─ coding-style.md         #   estilo general (TypeScript, nombres) + paridad de validación
│  ├─ testing.md              #   estrategia de pruebas + puente Scenario→test
│  ├─ tooling-setup.md        #   tooling reproducible (scripts, MSW, husky, CI)
│  ├─ commits.md              #   Conventional Commits en español + convención Refs:
│  └─ adr/                    #   Architecture Decision Records
│
├─ modules/                   # 🧩 el código de la aplicación
│  ├─ backend/                #   Java 25 · Maven · Spring Boot 4 (capas:
│  │                          #     presentation · service · persistence · infra · common)
│  └─ frontend/               #   React 19 · Vite · TypeScript (src/:
│                             #     modules/{core,auth} · screens · routes · store · assets · styles)
│
├─ contracts/
│  └─ openapi.json            # 📜 contrato OpenAPI versionado (fuente del codegen del front)
│
├─ .github/workflows/         # CI por área (backend · frontend · openspec)
├─ SETUP-OPENSPEC.md          # flujo y comandos de OpenSpec (setup, ciclo)
├─ CLAUDE.md                  # guía para agentes de IA (reglas del proyecto)
└─ readme.md                  # este documento
```

---

## OpenSpec: dónde se definen las capacidades

Este es el corazón del proyecto. Una **capacidad** es un área cohesiva de comportamiento con su propio `spec.md`.

- **`openspec/specs/<capacidad>/spec.md`** → lo **YA construido**. Formato:
  `## Requirements` → `### Requirement:` (con **SHALL**) → `#### Scenario:` (**WHEN/THEN**). Cada requirement tiene ≥ 1 scenario, y **los tests se derivan de esos scenarios**.
- **`openspec/changes/<id>/`** → capacidades **en progreso**: `proposal.md` (por qué), `design.md` (cómo), `tasks.md` (pasos) y `specs/<cap>/spec.md` (el **delta**: `## ADDED | MODIFIED | REMOVED Requirements`). Al terminar, el delta se fusiona en `openspec/specs/` y el change pasa a `changes/archive/`.

### Anatomía de una spec: por qué tiene *esas* secciones

Una spec de OdontoRisas **no** lleva las secciones clásicas de un documento funcional (*Objetivo · Actores · Alcance · Reglas de negocio · Casos excepcionales · Restricciones · Criterios de aceptación · Fuera de alcance*). Lleva **estas**, porque son las que **OpenSpec exige** — su propio parser rechaza una spec que no las tenga:

```
# <capacidad> Specification

## Purpose            ← para qué existe la capacidad  (obligatorio: el parser falla sin él)

## Requirements
### Requirement: <nombre>          ← una regla, redactada con SHALL
#### Scenario: <nombre>            ← un caso comprobable
- **WHEN**  <condición>
- **THEN**  <resultado esperado>
```

La razón de fondo es que **una spec no es un documento que se lee: es un documento que se ejecuta.** Cada `#### Scenario` es un **test**: la regla del proyecto es *un WHEN/THEN → un test*. Un apartado como "Reglas de negocio" en prosa no se puede verificar; un `Scenario` sí, y la CI se encarga de que no mienta.

**Entonces, ¿dónde están las otras secciones?** No desaparecen — están **repartidas** por el artefacto que de verdad les corresponde, en vez de amontonadas en un único documento:

| Sección clásica | Dónde vive aquí |
|---|---|
| **Objetivo** | `## Purpose` de la spec · y el `## Why` del `proposal.md` |
| **Actores** | dentro del `### Requirement:` que los nombra · catálogo en [`docs/domain.md`](docs/domain.md) |
| **Alcance** | `## What Changes` del `proposal.md` |
| **Fuera de alcance** | `## Non-goals` del `proposal.md` |
| **Reglas de negocio** | **son** los `### Requirement:` (SHALL) |
| **Casos excepcionales** | `#### Scenario:` de error y de borde (**obligatorios**, no un anexo) |
| **Restricciones** | `## Decisions` y `## Risks / Trade-offs` del `design.md` |
| **Criterios de aceptación** | **son** los `#### Scenario:` — no se escriben aparte |

> Reglas de negocio y criterios de aceptación **son la misma cosa escrita una sola vez**. Ahí está la ganancia: en un documento clásico se escriben dos veces y **se desincronizan**.

#### Una desviación deliberada: el `Purpose` va también en el *delta*

OpenSpec **obliga** a que la spec construida tenga `## Purpose`… pero **su plantilla del delta no te lo pide**. Resultado: el objetivo de la capacidad acababa escribiéndose **al archivar**, cuando ya está todo construido y no queda nada que discutir — la **única** parte del spec que se saltaba la revisión humana.

Por eso, **en este proyecto el delta de una capacidad nueva (`add-*`) lleva su `## Purpose` desde el `propose`**, para que se revise como todo lo demás; al archivar solo se copia. Un `update-*` **no** lo repite: ya vive en la spec construida, y duplicarlo sería crear una segunda fuente de verdad que se desincroniza. (Convención nuestra, no de OpenSpec; queda registrada en [`docs/vision.md`](docs/vision.md) y [`CLAUDE.md`](CLAUDE.md).)

**El ciclo** (detalle en [`SETUP-OPENSPEC.md`](SETUP-OPENSPEC.md)):

```
/opsx:explore → /opsx:propose → revisión humana → /opsx:apply → tests → /opsx:archive
```

[`docs/vision.md`](docs/vision.md) es el **puente** entre las tres fases: `planeada` (roadmap) → `en progreso` (change activo) → `construida` (`specs/`).

### Estado de las capacidades

| Capacidad | Tipo | Estado | Dónde |
|---|---|---|---|
| `project-foundation` | técnica | ✅ construida | [`openspec/specs/project-foundation/`](openspec/specs/project-foundation/spec.md) |
| `authentication` | negocio | ✅ construida | [`openspec/specs/authentication/`](openspec/specs/authentication/spec.md) |
| `api-type-contracts` | técnica | ✅ construida | [`openspec/specs/api-type-contracts/`](openspec/specs/api-type-contracts/spec.md) |
| ↳ *reforzada* | | ✅ | El contrato declara **status reales y errores tipados**; los errores transversales se inyectan solos; **CI vigila las dos direcciones del drift** (backend→contrato→cliente) |
| `multi-tenancy` | técnica | ✅ construida | [`openspec/specs/multi-tenancy/`](openspec/specs/multi-tenancy/spec.md) |
| `organization-branding` | negocio | ✅ construida | [`openspec/specs/organization-branding/`](openspec/specs/organization-branding/spec.md) |
| ↳ *cierre del aislamiento* | | ✅ construido | El contexto de tenant se fija **solo** en cada transacción, defendido por una regla de build ([`update-multi-tenancy`](openspec/changes/archive/2026-07-15-update-multi-tenancy/proposal.md), archivado) |
| `patients` | negocio | ✅ construida | La ficha del paciente: **núcleo del dominio** y **primera entidad de negocio aislada por organización** — [`openspec/specs/patients/`](openspec/specs/patients/spec.md) |
| `staff`, `appointments`, `dental-chart`, `treatments`, `images`, `patient-billing`, `inventory`, `dashboard` | negocio | ⏳ planeadas | roadmap en [`docs/vision.md`](docs/vision.md) |

> El producto es un **SaaS multi-tenant** (modelo identidad/organización/membresía, aislamiento por Row-Level Security). Reglas para construir capacidades de negocio: [`docs/backend.md §8.1`](docs/backend.md).

> El catálogo completo, el orden sugerido y la convención de granularidad están en [`docs/vision.md`](docs/vision.md).

---

## Stack

- **Backend**: Java 25 · Maven · **Spring Boot 4** · Spring Web MVC + springdoc-openapi · Spring Data JPA + **PostgreSQL** · **Flyway** (`ddl-auto=none`) · Spring Security + JWT.
- **Frontend**: **React 19** · Vite · TypeScript · Tailwind + shadcn · TanStack Query + axios · Zustand · react-hook-form + zod · react-router v7. Cliente API **generado con orval** desde `contracts/openapi.json`.

Detalle y versiones en [`docs/architecture.md`](docs/architecture.md).

---

## Cómo arrancar

**Prerequisitos**: JDK 25 · Node 22 · pnpm · PostgreSQL. (Tooling reproducible en [`docs/tooling-setup.md`](docs/tooling-setup.md).)

### Backend
```bash
cd modules/backend
cp secrets.properties.example secrets.properties   # rellena DB_* y JWT_SECRET
./mvnw spring-boot:run                              # arranca en http://localhost:8080
```
Contrato OpenAPI en `http://localhost:8080/v3/api-docs` (y Swagger UI en `/swagger-ui.html`).

### Frontend
```bash
pnpm install                                        # instala todo el workspace
pnpm --filter odontorisas-frontend dev              # arranca en http://localhost:5173
```

### Pruebas y calidad
```bash
cd modules/backend && ./mvnw verify                 # backend (test slices + ArchUnit)
pnpm --filter odontorisas-frontend validate         # frontend (typecheck + lint + test)
```

#### Estrategia de pruebas y razón de ser
Para garantizar la estabilidad y la paridad con producción sin comprometer la velocidad de desarrollo, el proyecto clasifica y aplica las pruebas según su propósito (detalle en [`docs/testing.md`](docs/testing.md)):

##### Backend
*   **Tests de Arquitectura (ArchUnit)**: Validan de manera estática que no se rompan las dependencias entre capas (ej. `presentation` -> `service` -> `persistence`) ni las restricciones de multi-tenancy.
*   **Tests Unitarios (JUnit 5 + Mockito)**: Prueban la lógica de negocio pura y servicios de forma aislada, mockeando la persistencia para máxima velocidad.
*   **Tests de Integración y Slices (`@WebMvcTest`, `@SpringBootTest` + Testcontainers)**:
    *   *Controladores y Seguridad*: Prueban los endpoints HTTP, serialización y permisos por rol.
    *   *Persistencia y RLS (PostgreSQL real)*: Validan consultas JPA y el aislamiento multi-tenant a nivel de base de datos usando **Testcontainers**, lo que garantiza que los datos de una organización estén aislados de otra.

##### Frontend
*   **Tests Unitarios (Vitest)**: Validan funciones puras, helpers, mappers, validadores y estados globales (Zustand).
*   **Tests de Componentes e Integración (Vitest + React Testing Library + MSW)**: Prueban la renderización, interacción de formularios y comunicación con la API interceptando peticiones de red mediante Mock Service Worker (MSW).
*   **Tests de Extremo a Extremo (Playwright)**: Simulan flujos de usuario completos en navegadores reales (ej. workflows CRUD y flujos de login/logout).

##### Ejecución: local vs CI
Las clases **`*IT`** (integración con **Testcontainers**) corren en la fase `verify` vía `maven-failsafe-plugin`; los **`*Test`** (unitarios/ArchUnit) en `test` vía surefire.

*   **Solo unitarios (rápido, sin Docker):** `cd modules/backend && ./mvnw test`
*   **Todo, incluidos los IT (requiere Docker):** `./mvnw verify`

> [!WARNING]
> **Los IT con Testcontainers requieren Docker.** En **Windows con Docker Desktop** pueden **saltarse** en local (`Skipped`) por una incompatibilidad conocida entre el proxy de Docker Desktop y docker-java (`/info` devuelve HTTP 400) — **no es un fallo del código ni de la config del proyecto**. Estos tests **corren siempre en CI** (Linux, socket estándar). Si necesitas ejecutarlos en local en Windows, la vía fiable es lanzar `./mvnw verify` **desde una terminal WSL2** (Docker en el socket estándar, sin el proxy). Ver [`docs/testing.md`](docs/testing.md).

### Regenerar el cliente API (tras cambiar el contrato)
```bash
curl http://localhost:8080/v3/api-docs > contracts/openapi.json
pnpm --filter odontorisas-frontend generate:api
```

---

## Documentación

| Documento | Para qué |
|---|---|
| [`docs/vision.md`](docs/vision.md) | Objetivos de producto, **roadmap de capacidades**, gobernanza y granularidad |
| [`docs/domain.md`](docs/domain.md) | Modelo de dominio: actores, glosario, entidades y diagrama ER |
| [`docs/architecture.md`](docs/architecture.md) | Stack cerrado + arquitectura de backend y frontend |
| [`docs/backend.md`](docs/backend.md) | Estándares Java/Spring (capas, DTOs/validación, JPA, seguridad, errores RFC 9457) |
| [`docs/frontend.md`](docs/frontend.md) | Estándares frontend (arquitectura, capa API generada, React Query, Zustand, UI) |
| [`docs/coding-style.md`](docs/coding-style.md) | Estilo general (TypeScript, nombres) + paridad de validación Zod↔Bean Validation |
| [`docs/testing.md`](docs/testing.md) | Estrategia de pruebas y puente Scenario→test |
| [`docs/tooling-setup.md`](docs/tooling-setup.md) | Tooling reproducible (Vitest/MSW, husky/commitlint, CI) |
| [`docs/commits.md`](docs/commits.md) | Estándar de commits |
| [`SETUP-OPENSPEC.md`](SETUP-OPENSPEC.md) | Flujo y comandos de OpenSpec |
| [`CLAUDE.md`](CLAUDE.md) | Guía para agentes de IA (reglas del proyecto) |

---

## Convenciones

- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/) en **español** (hook `commit-msg` los valida). Ver [`docs/commits.md`](docs/commits.md).
- **Gates antes de subir**: `openspec validate <change> --strict` + revisión humana · `./mvnw verify` (backend) · `pnpm --filter odontorisas-frontend validate` (frontend).
- **No commitear** secretos (`secrets.properties`, `.env`), `target/`, `dist/`, `node_modules/`.
