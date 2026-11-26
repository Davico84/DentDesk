## 1. Workflows: CI completa en cada PR y nombres únicos (scope: ci)

- [ ] 1.1 En `backend.yml`, `frontend.yml` y `openspec.yml`, quitar el bloque `paths` del evento `pull_request` y dejar intacto el de `push` (D1). Sin tocar `branches`, `concurrency`, jobs, pasos ni comandos.
- [ ] 1.2 Agregar `name:` a cada job con los nombres de D2 (`backend-verify`, `frontend-lint`, `frontend-codegen-drift`, `frontend-test`, `frontend-e2e-smoke`, `openspec-validate`) sin cambiar los ids.
- [ ] 1.3 Comprobar con `rg` que ningún otro workflow (`db-backup.yml`, `wake-up.yml`) publica un check con esos nombres, y que los docs que nombran los jobs por su id (`docs/tooling-setup.md` §5, `docs/testing.md`) siguen siendo correctos o se actualizan.
- [ ] 1.4 Validar los tres YAML (parseo) antes del commit.

## 2. Ruleset versionado (scope: ci)

- [ ] 2.1 Crear `.github/rulesets/main.json` con el contenido de D3: rama `refs/heads/main`; reglas `pull_request` (0 aprobaciones, `allowed_merge_methods: ["merge"]`), `required_status_checks` (los 6 nombres con `integration_id: 15368`, `strict_required_status_checks_policy: false`), `non_fast_forward` y `deletion`. Enforcement de la etapa de prueba de D4.
- [ ] 2.2 Escribir en `docs/tooling-setup.md` §5 los comandos para crear (`POST`), actualizar (`PUT .../rulesets/{id}`) y comparar el Ruleset aplicado con el archivo (`gh api` + `jq`, normalizando campos que GitHub agrega: `id`, `source`, fechas, `_links`).

## 3. Docs (scope: docs)

- [ ] 3.1 `docs/tooling-setup.md` §5: la tabla de workflows con el filtro solo en `push`, los nombres de los checks y la convención de prefijo por área. Reemplazar la nota "Path filters + branch protection … Por ahora no hay branch protection" por el Ruleset de `main` y el riesgo de `[skip ci]`.
- [ ] 3.2 `docs/commits.md` §5c: reemplazar "Sin protección de rama" por la protección de `main` (PR obligatorio, 6 checks, sin rama al día, merge commit) y por qué el repo público lo permite.
- [ ] 3.3 `docs/architecture.md` §5: "CI por PR" lista los seis checks exigidos en `main`.
- [ ] 3.4 `docs/deployment.md`, revisión mensual: agregar la comparación del Ruleset con el archivo (D3, riesgo de drift).
- [ ] 3.5 `docs/vision.md`: enlazar el change en progreso en "Estado de capacidades", bajo `project-foundation`.

## 4. Verificación de los checks, antes de aplicar el Ruleset (scope: ci)

- [ ] 4.1 En el PR de este change (toca los tres YAML), comprobar con `gh api repos/{owner}/{repo}/commits/{sha}/check-runs -q '.check_runs[] | "\(.name) \(.app.id)"'` que aparecen exactamente los seis nombres con app id 15368 (scenario "Nombres publicados").
- [ ] 4.2 Tras mergear a `dev`, en el siguiente PR que solo toque `docs/` comprobar que corren los seis checks (scenario "PR solo de documentación"). Y en el push del merge, que no corren los workflows de áreas no tocadas (scenario "Push que no toca el área").

## 5. Aplicar y activar el Ruleset (scope: ci, con autorización del user en cada paso)

- [ ] 5.1 Con este change ya en `main` (release), aplicar `.github/rulesets/main.json` con `gh api --method POST`. Si la API rechaza `evaluate`, cambiar el archivo a `active` con el bypass de D4 (`RepositoryRole` 5, `bypass_mode: pull_request`) en un PR y aplicar eso.
- [ ] 5.2 Comprobar con `gh api repos/{owner}/{repo}/rules/branches/main` que las reglas efectivas son las del archivo, y correr la comparación de 2.2 (scenario "Comparar con lo aplicado").
- [ ] 5.3 Pasar el siguiente release `dev` → `main` por el Ruleset: mergeable con los seis checks en verde, sin aprobaciones, sin traer `main` a `dev` y sin usar el bypass (scenario "Release con los checks en verde"). Si hay *Rule insights* (evaluate), revisarlos.
- [ ] 5.4 Etapa definitiva (D4): en un PR, el archivo pasa a `active` sin `bypass_actors`; aplicar con `PUT` y repetir 5.2.
- [ ] 5.5 Comprobar **sin intentar un push real** (si la regla no estuviera activa, llegaría a producción): `rules/branches/main` incluye `pull_request`, `non_fast_forward` y `deletion`, y el PR a `main` ofrece solo "Create a merge commit" (scenario "Push directo o force push").
