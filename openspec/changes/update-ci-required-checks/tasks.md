## 1. Workflows: CI completa en cada PR y nombres únicos (scope: ci)

- [x] 1.1 En `backend.yml`, `frontend.yml` y `openspec.yml`, quitar el bloque `paths` del evento `pull_request` y dejar intacto el de `push` (D1). Sin tocar `branches`, `concurrency`, jobs, pasos ni comandos.
- [x] 1.2 Agregar `name:` a cada job con los nombres de D2 (`backend-verify`, `frontend-lint`, `frontend-codegen-drift`, `frontend-test`, `frontend-e2e-smoke`, `openspec-validate`) sin cambiar los ids.
- [x] 1.3 Comprobar que los docs que nombran los jobs por su id (`docs/tooling-setup.md` §5, `docs/testing.md`) siguen siendo correctos o se actualizan.
- [x] 1.3b Escribir `.github/scripts/check-ci-check-names.sh` (D5): con `yq`, nombre efectivo de cada job de `.github/workflows/*.yml` (`name` o id); falla nombrando el check si alguno de los seis aparece 0 o más de 1 vez. En el paso de CI, `yq` se descarga del release oficial de `mikefarah/yq` con versión y SHA-256 fijos (variables `YQ_VERSION`/`YQ_SHA256`, verificado con `sha256sum --check --strict` antes de usarlo), nunca el de la imagen. Probarlo en local contra los workflows actuales (pasa) y contra dos copias temporales fuera del repo: una con un job extra `name: frontend-test` (falla, scenario "Nombre repetido") y otra sin `backend-verify` (falla, scenario "Nombre exigido que desaparece").
- [x] 1.3c Agregarlo como paso del job `openspec-validate` en `openspec.yml`, antes de `openspec validate`.
- [x] 1.4 Validar los tres YAML (parseo) antes del commit, y confirmar con `rg` que los tres no usan `secrets.` ni piden `permissions` de escritura (riesgo de forks).

## 2. Ruleset versionado (scope: ci)

- [x] 2.1 Crear `.github/rulesets/main.json` con el contenido de D3, en la forma de la API REST: `"target": "branch"` y `conditions.ref_name.include: ["refs/heads/main"]` (no `refs/heads/main` en `target`); reglas `pull_request` (0 aprobaciones, `allowed_merge_methods: ["merge"]`), `required_status_checks` (los 6 nombres con `integration_id: 15368`, `strict_required_status_checks_policy: false`), `non_fast_forward` y `deletion`. Enforcement de la etapa de prueba de D4.
- [x] 2.2 Escribir `.github/scripts/ruleset-check.py` (D3): `gh api --paginate .../rulesets`, falla si hay 0 o más de 1 Ruleset con ese `name`; lee `GET .../rulesets/{id}`; falla si falta `bypass_actors`; normaliza ambos lados como dice D3 (todos los `parameters`, arreglos internos ordenados, `rules` ordenadas por su JSON canónico completo) y sale con error ante cualquier diferencia, mostrando el diff. Probar la normalización sin GitHub, con la función de normalizar aislada: el archivo contra sí mismo con `rules` y checks reordenados (sin diferencia, scenario "Mismo contenido en otro orden"), contra una copia con un `integration_id` cambiado y otra con `strict_required_status_checks_policy: true` (diferencia en ambas), y contra una respuesta sin `bypass_actors` (falla).
- [x] 2.3 Escribir en `docs/tooling-setup.md` §5 los comandos para crear (`POST`) y actualizar (`PUT .../rulesets/{id}`) y cómo correr `ruleset-check.py`.

## 3. Docs (scope: docs)

- [x] 3.1 `docs/tooling-setup.md` §5: la tabla de workflows con el filtro solo en `push`, los nombres de los checks, la convención de prefijo por área y la comprobación automática de D5. Reemplazar la nota "Path filters + branch protection … Por ahora no hay branch protection" por el Ruleset de `main`, el riesgo de `[skip ci]` y el aviso de Merge Queue (`merge_group`).
- [x] 3.2 `docs/commits.md` §5c: reemplazar "Sin protección de rama" por la protección de `main` (PR obligatorio, 6 checks, sin rama al día, merge commit) y por qué el repo público lo permite.
- [x] 3.3 `docs/architecture.md` §5: "CI por PR" lista los seis checks exigidos en `main`.
- [x] 3.4 `docs/deployment.md`, revisión mensual: agregar `ruleset-check.py` (D3, riesgo de drift).
- [x] 3.5 `docs/vision.md`: enlazar el change en progreso en "Estado de capacidades", bajo `project-foundation`.

## 4. Verificación de los checks, antes de aplicar el Ruleset (scope: ci)

- [x] 4.1 En el PR de este change (toca los tres YAML), comprobar con `gh api repos/{owner}/{repo}/commits/{sha}/check-runs -q '.check_runs[] | "\(.name) \(.app.id)"'` que aparecen exactamente los seis nombres con app id 15368 (scenario "Nombres publicados"). Hecho en el PR #145: los seis check runs, app 15368; el paso de nombres pasó.
- [x] 4.2 Tras mergear a `dev`, en el siguiente PR que solo toque `docs/` comprobar que corren los seis checks (scenario "PR solo de documentación"). Y en el push del merge, que no corren los workflows de áreas no tocadas (scenario "Push que no toca el área"). Hecho: el PR #146 (solo `docs/`) corrió los seis; el push de su merge a `dev` (717dc58) no corrió ningún workflow.

## 5. Aplicar y activar el Ruleset (scope: ci, con autorización del user en cada paso)

- [x] 5.1 Con este change ya en `main` (release), aplicar `.github/rulesets/main.json` con `gh api --method POST`. Si la API rechaza `evaluate`, cambiar el archivo a `active` con el bypass de D4 (`RepositoryRole` 5, `bypass_mode: pull_request`) en un PR y aplicar eso. Hecho el 2026-11-27 tras el release #147: la API rechazó `evaluate` ("not supported on this plan", 422); se aplicó `active` con el bypass de D4 (Ruleset id 28202865).
- [x] 5.2 Correr `ruleset-check.py`: sin diferencias contra el archivo, incluidos `enforcement`, `conditions` y `bypass_actors` (scenario "Comparar con lo aplicado"). Como control adicional, `rules/branches/main` muestra las reglas efectivas. Hecho: la primera comparación mostró dos parámetros por defecto de GitHub en `pull_request` (`require_extra_approval_for_unattributed_changes: true`, solo suma una aprobación a PRs del agente Copilot cuando ya se exige ≥ 1, y `required_reviewers: []`); se agregaron al archivo y `ruleset-check.py` coincide. `rules/branches/main`: `deletion`, `non_fast_forward`, `pull_request`, `required_status_checks`.
- [ ] 5.3 Pasar el siguiente release `dev` → `main` por el Ruleset: mergeable con los seis checks en verde, sin aprobaciones, sin traer `main` a `dev` y sin usar el bypass (scenario "Release con los checks en verde"). Si hay *Rule insights* (evaluate), revisarlos.
- [ ] 5.4 Etapa definitiva (D4), **obligatoria antes del archive**: en un PR, el archivo pasa a `active` sin `bypass_actors`; aplicar con `PUT` y repetir 5.2. Con el bypass puesto el requirement de checks obligatorios no se cumple.
- [ ] 5.5 Comprobar **sin intentar un push real** (si la regla no estuviera activa, llegaría a producción): `rules/branches/main` incluye `pull_request`, `non_fast_forward` y `deletion`, y el PR a `main` ofrece solo "Create a merge commit" (scenario "Push directo o force push").
- [ ] 5.6 Render (`autoDeployTrigger: checksPass` sobre los checks del push a `main`): en el release de 5.3, con cambios de código, el push a `main` publica checks y Render despliega. Para un release solo de docs, el resultado esperado es **cero checks y sin deploy**: queda escrito en `docs/deployment.md` y en `docs/commits.md` §5c y se confirma la primera vez que ocurra. Si no ocurre antes del archive, queda anotado como pendiente de observar, no como hecho.
