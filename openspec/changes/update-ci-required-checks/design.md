## Context

Hay tres workflows de CI y los tres disparan con `pull_request` y `push` hacia `main` y `dev`, con filtro `paths` en los dos eventos:

| Workflow | Jobs (id) | Filtro `paths` |
|---|---|---|
| `backend.yml` | `verify` | `modules/backend/**`, el propio YAML |
| `frontend.yml` | `lint`, `codegen-drift`, `test`, `e2e-smoke` | `modules/frontend/**`, `contracts/**`, `pnpm-*`, el propio YAML |
| `openspec.yml` | `validate` | `openspec/**`, el propio YAML |

Datos comprobados el 2026-11-26:
- **Nombres de los checks.** Con la API de check-runs del último commit de Frontend y de los PR #143/#144, los check runs se llaman igual que el **id del job**: `verify`, `lint`, `codegen-drift`, `test`, `e2e-smoke` y `validate`, todos de la app `github-actions` (`integration_id` 15368). La interfaz muestra `Backend / verify`, pero el Ruleset compara contra el nombre del check run.
- **Estado del repo.** Es público, no tiene Rulesets (`gh api repos/.../rulesets` devuelve `[]`) y `main` no tiene protección clásica (404).
- **Concurrencia.** Cada workflow agrupa por `<wf>-${{ github.ref }}` con `cancel-in-progress: true`.
- **Releases.** Son PRs `dev` → `main` mergeados con commit de merge (`docs/commits.md` §5c).

## Goals / Non-Goals

**Goals:**
- Que un check exigido **siempre llegue** en un PR a `main`, para que nunca quede pendiente por un filtro.
- Que los nombres exigidos sean **inequívocos** en todo el repo.
- Que la protección de `main` sea **reproducible y revisable** como cualquier otro cambio, y no un ajuste invisible de la interfaz.

**Non-Goals:**
- Reducir el tiempo de CI de los PR. Se acepta que un PR de docs tarde lo que tarde el backend.
- Proteger `dev`, o exigir aprobaciones.

## Decisions

### D1. Quitar `paths` de `pull_request`, mantenerlo en `push`

**Elegida.** Cada PR corre los seis checks, así que un check exigido siempre llega. El push queda filtrado: después de mergear no se repite la CI del área que no cambió, porque el PR ya la corrió sobre el resultado del merge (`refs/pull/N/merge`).

**Alternativas descartadas:**
- **Workflow coordinador con `workflow_run`, que reporta checks a mano.** Es frágil: si un workflow no se dispara, el coordinador tampoco recibe el evento. Además obliga a publicar check runs con un token.
- **Un solo workflow con `dorny/paths-filter` y un job agregador `ci-ok`** que se exige solo (`if: always()` y falla si un job necesario falló). Es la opción correcta para monorepos grandes, pero suma una acción de terceros, un `if` que si se escribe mal da verde falso, y fusionar tres workflows en uno.
- **Mantener los filtros y exigir solo algunos checks.** Volvería a dejar áreas sin cubrir en un release.

**Por qué alcanza:** el repo es público, así que los minutos en runners estándar no se cobran. El costo es solo la espera, de unos 4 min por el backend.

### D2. Nombres únicos con `name:` en el job, sin tocar los ids

`name: backend-verify` (y los otros cinco) en cada job. El check run toma el `name:` del job. Los ids (`verify`, `lint`…) quedan igual, así que no se rompe ningún `needs:` (hoy no hay ninguno) ni ninguna referencia en los docs al job por su id.

El prefijo del área (`backend-`, `frontend-`, `openspec-`) es la convención: un job nuevo con check exigible lleva el prefijo de su workflow. **Alternativa descartada:** dejar los ids genéricos y exigirlos. Otro workflow que agregue un job `test` sobre el mismo commit (por ejemplo, uno manual) satisfaría el check aunque el de frontend no corriera.

### D3. Ruleset versionado en `.github/rulesets/main.json`, aplicado con `gh api`

El archivo es el cuerpo exacto de la API de Rulesets de REST. Se aplica con:
- **Crear:** `gh api --method POST repos/{owner}/{repo}/rulesets --input .github/rulesets/main.json`.
- **Actualizar:** `PUT .../rulesets/{id}`.
- **Comparar:** `gh api .../rulesets/{id}` normalizado con `jq` contra el archivo. El comando queda en `docs/tooling-setup.md`.

**Alternativa descartada:** configurarlo solo en la interfaz. Es invisible para la revisión y no queda en el historial: justo lo que el proyecto evita con OpenSpec.

Contenido:

| Regla | Valor | Por qué |
|---|---|---|
| objetivo | `refs/heads/main` | solo `main` (non-goal: `dev`) |
| `pull_request` | `required_approving_review_count: 0`, `allowed_merge_methods: ["merge"]` | un solo desarrollador no puede aprobarse a sí mismo; los releases son commits de merge y así se mantiene la historia de §5c |
| `required_status_checks` | los 6 nombres, cada uno con `integration_id: 15368` (GitHub Actions), `strict_required_status_checks_policy: false` | un check publicado por otra app no cuenta. Sin "rama al día": cada release deja en `main` un merge que `dev` no tiene, y exigirlo obligaría a traer `main` a `dev` antes de cada release |
| `non_fast_forward` | sí | bloquea el force push |
| `deletion` | sí | bloquea el borrado |
| historia lineal | **no** | incompatible con los commits de merge |

El push directo queda bloqueado por la regla `pull_request`.

### D4. Activación en dos etapas

1. **Etapa de prueba.** Si el plan lo permite, `enforcement: "evaluate"`: registra en *Rule insights* lo que bloquearía, sin bloquear. Si la API rechaza `evaluate` (la documentación lo limita a GitHub Enterprise), se usa `enforcement: "active"` con `bypass_actors: [{ actor_type: "RepositoryRole", actor_id: 5, bypass_mode: "pull_request" }]`. El id 5 es el rol *Admin*. El administrador puede saltarse la regla **desde un PR**, no con un push directo. Sirve de red si un nombre quedó mal escrito.
2. **Definitiva.** Después de un release que pase sin usar el bypass, o sin bloqueos en *Rule insights*: `active` y sin `bypass_actors`. Cada etapa es un cambio al archivo y un `PUT`.

**Alternativa descartada:** activar directo sin bypass. Un nombre mal escrito dejaría el release bloqueado, y habría que desactivar el Ruleset entero para salir.

## Risks / Trade-offs

- **`[skip ci]` en un commit del PR deja los checks sin publicar.** El merge queda pendiente. Mitigación: no usar `[skip ci]` en ramas de PR; se documenta.
- **Un YAML inválido** no publica sus checks y deja el merge pendiente. Es lo deseable: la regla funciona como alarma.
- **El merge a `main` puede no ser exactamente lo que probó el PR** si `main` cambió mientras tanto, porque no se exige la rama al día. En la práctica, `main` solo recibe releases desde `dev`, de a uno. Los PR corren sobre `refs/pull/N/merge` y Render no despliega `main` en rojo (la CI del push a `main` sigue filtrada por área).
- **Cancelaciones en `main` y `dev`.** Dos merges seguidos cancelan la corrida del primero (`cancel-in-progress`). No afecta al Ruleset, que mira los checks del PR. Queda igual que hoy.
- **El e2e inestable bloquea releases.** Se acepta: si falla de forma intermitente, se arregla el test, no se saca el check.
- **Drift del Ruleset** si alguien lo edita en la interfaz. Mitigación: el comando de comparación de D3 entra en la revisión mensual de `docs/deployment.md`, junto con la de las copias.
