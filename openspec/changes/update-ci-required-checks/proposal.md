## Why

**`main` no tiene ninguna protección.** Un push directo, un force push o un merge con la CI en rojo llegan a producción: Render despliega cada push a `main`. Hoy el único freno es que Render no despliega `main` en rojo (`docs/commits.md` §5c), y eso solo actúa **después** de que el código ya está en `main`.

`docs/commits.md` lo justificaba así: "el repo es privado en el plan gratis y GitHub no permite reglas que exijan los checks". **Ya no es cierto**: el repo es **público** desde noviembre de 2026, y en un repo público los Rulesets y los minutos de Actions en runners estándar son gratis.

Antes de exigir checks hay que resolver lo que `docs/tooling-setup.md` §5 ya advierte. Los tres workflows de CI tienen filtros `paths` también en `pull_request`. Un PR que no toca el área de un workflow no lo dispara, y un check exigido que nunca llega deja el merge **pendiente para siempre**. Por ejemplo, un release que solo trae docs quedaría bloqueado.

Una revisión técnica (2026-11-26) encontró dos trampas más:
- **El nombre de los checks.** El nombre real de cada check es el id del job (`verify`, `lint`, `test`, `validate`…), no el `Backend / verify` que muestra la interfaz. Además, `test`, `lint` y `validate` son nombres genéricos que otro workflow podría repetir.
- **La opción de exigir la rama al día.** Rompería los releases: cada release `dev` → `main` crea en `main` un commit de merge que `dev` no tiene.

## What Changes

- **CI completa en cada PR:** se quitan los filtros `paths` del evento `pull_request` en `backend.yml`, `frontend.yml` y `openspec.yml`. Los del evento `push` quedan como están. Todo PR a `main` o `dev` corre los seis checks, toque lo que toque.
- **Nombres únicos para los seis checks:** `backend-verify`, `frontend-lint`, `frontend-codegen-drift`, `frontend-test`, `frontend-e2e-smoke` y `openspec-validate`. Se ponen con `name:` en cada job; los ids, pasos y comandos no cambian.
- **Ruleset de `main` versionado en el repo** (`.github/rulesets/main.json`) y aplicado con `gh api`:
  - exige PR con 0 aprobaciones;
  - exige los seis checks, publicados por la app GitHub Actions;
  - no exige la rama al día;
  - bloquea el force push y el borrado de la rama.
- **Activación por etapas:** primero en modo Evaluate, o, si el plan no lo ofrece, Active con bypass del administrador **solo vía PR**. Después de un release limpio, Active sin bypass.
- **Comprobación automática de nombres:** un paso de `openspec-validate` falla si alguno de los seis nombres falta o aparece más de una vez entre los workflows.
- **Comparación repetible del Ruleset** (`.github/scripts/ruleset-check.sh`) entre lo aplicado en GitHub y el archivo.
- **Docs:**
  - `docs/tooling-setup.md` §5: el filtro solo en push, los nombres de los checks y el Ruleset; reemplaza la nota "por ahora no hay branch protection".
  - `docs/commits.md` §5c: la protección de `main` reemplaza a "Sin protección de rama".
  - `docs/architecture.md` §5: los checks exigidos.
  - `docs/vision.md`: estado de capacidades.

## Non-goals

- **No se protege `dev`.** Con los checks corriendo en cada PR, el rojo se ve antes de mergear. Proteger `dev` queda para cuando haya más de un colaborador.
- **No se agrega un job agregador ni filtros por job** (`dorny/paths-filter`): con minutos gratis no compensan su complejidad (ver design D1).
- **No se exigen los checks de Vercel**: son externos y pueden fallar por motivos ajenos al código.
- **No se fijan por SHA las acciones de `frontend.yml`** (`actions/checkout@v4`…). Queda anotado como change aparte.
- **No se cambian los jobs ni los comandos de la CI**, ni la concurrencia. La única excepción es un paso nuevo en `openspec-validate` que comprueba la unicidad de los nombres (design D5).
- **No se usa Merge Queue.** Si se activa algún día, los workflows deben sumar el evento `merge_group` (design D3).
- **No se exige historia lineal**: los releases son commits de merge.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `project-foundation`: ADDED requirements de CI completa en cada PR, nombres únicos de los checks y protección de `main` con checks obligatorios. No se modifica ningún requirement existente.

## Impact

- **CI:** `.github/workflows/backend.yml`, `frontend.yml` y `openspec.yml`: se quita `paths` en `pull_request` y se agrega `name:` en cada job. Los PR a `dev` tardan lo que tarde el workflow más lento (backend, unos 4 min), aunque no toquen el backend.
- **GitHub:** un Ruleset nuevo sobre `main`. Ya no se puede pushear directo a `main`; el release sigue siendo un PR `dev` → `main`, como hoy.
- **Nada cambia en:**
  - Render, que sigue desplegando con el push a `main`;
  - `db-backup.yml`, que corre programado o a mano, no en PRs, y cuyo job es `backup`;
  - `wake-up.yml`.
- **Sin cambios de código** de backend ni de frontend.
