# OdontoRisas — Guía para agentes

Proyecto **greenfield** con desarrollo dirigido por especificaciones (spec-driven) usando **OpenSpec**.
Monorepo plano: `backend/` (Java/Spring) + `frontend/` (React/Vite), con `openspec/` y `docs/` en la raíz.

## Fuentes de verdad
- **Flujo OpenSpec** (setup, comandos, ciclo): `SETUP-OPENSPEC.md`.
- **Qué hace el sistema** (capacidades + cambios): `openspec/` (y `openspec/config.yaml` = contexto para la IA).
- **Cómo construimos** (estándares perennes): `docs/`
  - `docs/architecture.md` — stack cerrado + arquitectura back/front.
  - `docs/capabilities.md` — convención de granularidad de capacidades · `docs/roadmap.md` — capacidades planeadas.
  - `docs/testing.md` — estrategia de pruebas + puente Scenario→test.
  - `docs/backend-standards.md` — estándares Java/Spring · `docs/coding-standards.md` (+ react-query/zustand/ui-patterns) — estándares frontend.
  - `docs/commits.md` — estándar de commits (Conventional Commits en español).

## Reglas OpenSpec (no negociables)
- `specs/` = lo **YA construido**, no un backlog. Cada capacidad nace como *change* y llega a `specs/` al archivar.
- Capacidad = un solo `spec.md`. Formato: `## Requirements` → `### Requirement:` (SHALL) → `#### Scenario:` (WHEN/THEN). Cada requirement ≥ 1 scenario.
- Spec delta del change: `changes/<id>/specs/<cap>/spec.md` con `## ADDED | MODIFIED | REMOVED Requirements`.
- `change-id` en kebab-case liderado por verbo: `add-...`, `update-...`, `remove-...`.
- Tests **derivados de los Scenario** del spec (uno por WHEN/THEN).

## Flujo de trabajo
`/opsx:explore` (pensar) → `/opsx:propose` (change + artefactos) → revisión humana → `/opsx:apply` (implementar) → tests → `/opsx:sync`/`/opsx:archive`.
Ruta rápida por defecto: **propose → apply → archive**.

## Gates antes de codear / al subir
- `openspec validate <change> --strict` + revisión humana del proposal/delta.
- Backend: `mvn verify`. Frontend: `pnpm validate` (typecheck + lint + test).
- Commits: Conventional Commits en **español** (ver `docs/commits.md`). PRs con `gh`.

## Convenciones rápidas
- No commitear secrets/`.env`, `target/`, `dist/`, `node_modules/`.
- Nada destructivo en git sin permiso explícito.
- Al tocar back y front de una capacidad, separar tareas/commits por scope cuando sea posible.
