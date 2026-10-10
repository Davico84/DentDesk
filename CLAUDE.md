# OdontoRisas — Guía para agentes

Proyecto **greenfield** con desarrollo dirigido por especificaciones (spec-driven) usando **OpenSpec**.
Monorepo modular: `modules/backend/` (Java/Spring) + `modules/frontend/` (React/Vite), con `openspec/` y `docs/` en la raíz.

> **El producto se llama Dentdesk** (decisión 2026-07-20, ver `docs/vision.md`). "OdontoRisas" queda
> como el nombre **interno** del repo/código — carpeta del repo, package Java (`com.odontorisas`),
> roles de base de datos (`odontorisas_admin`/`odontorisas_app`), `groupId` Maven — por estabilidad:
> renombrarlos toca migraciones Flyway ya aplicadas y una restructuración de paquete de bajo valor.
> Lo que **renderiza** al usuario (UI, copy, título de pestaña) ya dice Dentdesk. Al escribir código o
> rutas nuevas, seguí usando `odontorisas`/`com.odontorisas`; al escribir copy visible o docs de
> negocio/producto, usá Dentdesk.

## Fuentes de verdad
- **Flujo OpenSpec** (setup, comandos, ciclo): `SETUP-OPENSPEC.md`.
- **Qué hace el sistema** (capacidades + cambios): `openspec/` (y `openspec/config.yaml` = contexto para la IA).
- **Cómo construimos** (estándares perennes): `docs/`
  - **Negocio (sustituible):** `docs/vision.md` — objetivos de producto + roadmap + gobernanza del ciclo + granularidad de capacidades · `docs/domain.md` — modelo de dominio (vivo): actores, glosario y ER. Fuente de verdad local-first; Jira solo backlog (futuro).
  - **Tecnología (reutilizable):** `docs/architecture.md` — stack cerrado + arquitectura back/front · `docs/backend.md` — estándares Java/Spring · `docs/frontend.md` — estándares frontend (arquitectura, React Query, Zustand, patrones UI) · `docs/coding-style.md` — estilo general de código (TypeScript, nombres) · `docs/testing.md` — estrategia de pruebas + puente Scenario→test.
  - **Decisiones (el porqué):** `docs/adr/` — un ADR por decisión que sobrevive al change que la implementa. `docs/adr/README.md` — cómo se escriben y se revisan (las **cinco dimensiones**: normativa, dominio clínico, DDD, arquitectura, evolución).
  - **Cumplimiento normativo:** `docs/compliance/regulatory-matrix.md` — trazabilidad norma → ADR → requirement/scenario. Al escribir un ADR con motivación normativa, actualizar su fila. **No** es un resumen de las normas ni un segundo hogar para las decisiones.
  - `docs/tooling-setup.md` — tooling reproducible (scripts, Vitest/MSW, husky/commitlint, CI).
  - `docs/commits.md` — estándar de commits (Conventional Commits en español) + convención `Refs:` para enlazar tareas.

## Reglas OpenSpec (no negociables)
- `specs/` = lo **YA construido**, no un backlog. Cada capacidad nace como *change* y llega a `specs/` al archivar.
- Capacidad = un solo `spec.md`. Formato: `## Requirements` → `### Requirement:` (SHALL) → `#### Scenario:` (WHEN/THEN). Cada requirement ≥ 1 scenario.
- Spec delta del change: `changes/<id>/specs/<cap>/spec.md` con `## ADDED | MODIFIED | REMOVED Requirements`.
  - **Capacidad NUEVA** (`add-*`): el delta lleva además su **`## Purpose`** (antes de los requirements). El objetivo debe pasar por la **revisión humana**; escribirlo al archivar lo colaría sin revisar. **Capacidad ya construida** (`update-*`): el delta **no** repite el Purpose.
- `change-id` en kebab-case liderado por verbo: `add-...`, `update-...`, `remove-...`.
- Tests **derivados de los Scenario** del spec (uno por WHEN/THEN).

## Flujo de trabajo
`/opsx:explore` (pensar) → `/opsx:propose` (change + artefactos) → revisión humana → `/opsx:apply` (implementar) → tests → `/opsx:sync`/`/opsx:archive`.
Ruta rápida por defecto: **propose → apply → archive**.

### Gobernanza del ciclo (mantiene el contexto de la IA preciso)
`docs/vision.md` es el **puente**: planeada (roadmap) → en progreso (change activo) → construida (`specs/`).
- **Al `/opsx:propose`**: enlaza el change en progreso (`changes/<id>/proposal.md` y `design.md`) en la sección "Estado de capacidades" de `docs/vision.md`.
- **Al `/opsx:archive`** (en el mismo commit/PR): 1) si la capacidad tocó el dominio, actualiza `docs/domain.md` (de ahí extrae el modelo la IA); 2) marca la capacidad como construida y enlaza su `spec.md` en `docs/vision.md`.

## Gates antes de codear / al subir
- `openspec validate <change> --strict` + revisión humana del proposal/delta.
- Backend: `mvn verify`. Frontend: `pnpm validate` (typecheck + lint + test).
- Commits: Conventional Commits en **español** (ver `docs/commits.md`). PRs con `gh`.

## Convenciones rápidas
- No commitear secrets/`.env`, `target/`, `dist/`, `node_modules/`.
- Nada destructivo en git sin permiso explícito.
- Al tocar back y front de una capacidad, separar tareas/commits por scope cuando sea posible.
