# Estándar de commits — OdontoRisas

Formato: **Conventional Commits**, mensajes **en español**.
Combina el formato convencional (tipos + scope) con las buenas prácticas del skill `commit` del demo `lidr-specboot-main` (focalizado, subject + body, PRs con `gh`).

---

## 1. Formato

```
<tipo>(<scope>): <resumen en imperativo, minúscula, sin punto final>

<body opcional: qué cambió y por qué, en bullets>

<footer opcional: BREAKING CHANGE / refs a tickets>
```

- **Subject** ≤ ~72 chars, imperativo ("agrega", "corrige", "actualiza"), sin punto final.
- **Body** cuando el cambio no es trivial: bullets con el qué y el porqué.
- Un commit = un cambio coherente y **focalizado** (no mezclar features).

> [!WARNING]
> **commitlint rechaza el subject en mayúscula inicial, PascalCase o TODO-MAYÚSCULAS** (`subject-case`). Un nombre propio al arranque tumba el commit: `fix: ArchUnit no leía…` **falla** → `fix: archunit no leía…`. Reformula para que empiece en minúscula (mueve el nombre propio o baja su inicial). Igual con `Flyway…`, `BrandingControllerIT…`, `ADR…`.

## 2. Tipos permitidos

| Tipo | Uso |
|---|---|
| `feat` | nueva funcionalidad de cara al usuario |
| `fix` | corrección de bug |
| `docs` | documentación (docs/, openspec/, README, specs) |
| `refactor` | cambio interno sin alterar comportamiento |
| `test` | añadir o ajustar pruebas |
| `chore` | tooling, config, dependencias, andamiaje |
| `build` | build/empaquetado (Maven, Vite) |
| `ci` | pipelines / GitHub Actions |
| `perf` | mejora de rendimiento |
| `style` | formato/whitespace sin cambio de lógica |
| `revert` | revierte un commit previo |

## 3. Scopes sugeridos (monorepo)

`backend`, `frontend`, `openspec`, `docs`, `ci`, `auth`, `<capacidad>` (p. ej. `feat(auth): ...`).

Para cambios que tocan back y front de una misma capacidad, prefiere **commits separados** por scope; si es inseparable, usa el scope de la capacidad (`feat(auth): ...`).

## 4. Ejemplos

```
feat(auth): agrega registro de usuario

- valida correo y contraseña (>= 8 chars)
- crea cuenta e inicia sesión, redirige según rol
- error si el correo ya existe (no duplica ni inicia sesión)
```
```
fix(backend): corrige 401 al refrescar token expirado
```
```
docs(openspec): cierra stack modules/backend/frontend y arquitectura
```
```
chore(frontend): configura vitest + msw y lint-staged
```

### BREAKING CHANGE
```
feat(api): unifica formato de respuesta de errores

BREAKING CHANGE: el body de error pasa de { message } a { error: { message, code } }
```

## 4b. Trazabilidad: enlazar el commit con la tarea (`Refs:`)

Las **tareas codificadas** viven en el `tasks.md` de cada *change* de OpenSpec (IDs `1.1`, `3.2`…) — esa es la fuente única. Para saber desde un commit qué tarea cerró, añade un footer **`Refs:`** con el `change-id` y los IDs de tarea:

```
feat(auth): implementa registro de usuario

- valida correo/contraseña y crea la cuenta

Refs: add-authentication #2.1 #2.3
```

Reglas:
- Formato del footer: `Refs: <change-id> #<task-id> [#<task-id> ...]`.
- Un commit puede cerrar una o varias tareas del **mismo** change. Si toca varios changes, usa una línea `Refs:` por change.
- A nivel capacidad, el `change-id` ya mapea al roadmap (`docs/vision.md`).
- Al cerrar la tarea, marca su checkbox (`- [x]`) en el `tasks.md` del change.
- `Refs:` es informativo (no lo valida commitlint); mantenlo consistente.

## 5. Reglas de trabajo (del skill `commit` del demo)

- **Focalizado**: si se indican features/tickets, se commitean **solo** esos cambios; el resto queda sin stagear.
  - Si un **mismo archivo mezcla** cambios de la feature y otros no relacionados, usa **`git add -p`** para stagear **solo los hunks** que tocan — no arrastres lo demás en el commit.
- **Modo solo-mensaje (dry-run)**: si se pide *"solo el mensaje"*, *"no toques git"*, *"solo el commit"* o *"dry run"*, se produce el **plan de staging + el mensaje** (subject + body) **sin ejecutar `git`** (ni `add`, ni `commit`, ni `push`, ni `gh`). Para revisarlo o pegarlo a mano.
- **No commitear** secrets, `.env`, artefactos generados ni `target/`, `dist/`, `node_modules/`.
- **PRs con `gh`** (GitHub CLI). Título alineado con el commit; descripción con resumen, testing y follow-ups.
- **Nada destructivo** sin permiso explícito (`git push --force`, `reset --hard`). Ante push rechazado: reportar y sugerir pull/rebase.
- Branches de feature pequeñas y descriptivas: `feat/...`, `fix/...`.

## 5b. Ramas: `dev` es el tronco, `main` es release

Decidido el **2026-09-23**, después de encontrar que la rama por defecto del repo era `feat/inializatingOpenSpec` —una rama de inicialización del 7 de julio, con el nombre mal escrito— mientras todo el trabajo iba a `dev` y `main` llevaba dos meses 242 commits atrás.

| Rama | Qué es | Cuándo se actualiza |
|---|---|---|
| **`dev`** | **el tronco**, y la **rama por defecto** del repo | en cada merge de PR. Todo sale de acá y vuelve acá |
| **`main`** | **release** | **solo al desplegar**. No es espejo de `dev` |
| `feat/*` `fix/*` `docs/*` `chore/*` `test/*` | trabajo en curso | se borran al mergear |

**Los PR van a `dev`.** La rama por defecto ya lo hace automático; no hace falta `--base`.

### Por qué `main` puede estar atrasada

**Porque no hay despliegue todavía.** Sincronizarla en cada merge sería trabajo repetido que no responde ninguna pregunta: mientras no exista un entorno productivo, *"qué hay en `main`"* no significa nada distinto de *"qué hay en `dev`"*.

⚠️ **Que esté atrasada no es desorden si se sabe por qué.** El desorden era no saber cuál era la rama por defecto. El día que haya despliegue, `main` empieza a responder *"esto es lo que está corriendo"*, y recién entonces mantenerla al día tiene valor.

### Ramas y tags que no se borran

- **`Starter-Template`** — tiene un commit que no está en `dev`. Se conserva como rama.
- **Los puntos de bifurcación son tags, no ramas** (`bifurcacion/*`). Una rama dice *"hay trabajo en curso acá"*; un tag dice *"este punto es un marcador fijo"*. Bifurcar desde un tag es igual de fácil: `git checkout -b lo-que-sea bifurcacion/saas-template`.

Hoy existen `bifurcacion/saas-template`, `bifurcacion/multi-tenancy` y `bifurcacion/openspec-inicial`.

⚠️ **Borrar una rama ya mergeada no pierde ningún commit** — se pierde solo el nombre, y el merge commit del PR ya lo registra. Por eso las 47 ramas de avance se borraron sin riesgo, y las tres bifurcaciones se convirtieron en tags antes de borrarlas.

## 6. Automatización (pendiente de andamiaje)

- **commitlint** + config Conventional Commits para validar en `commit-msg` (hook) y en CI.
- Front: `lint-staged` en pre-commit (ya previsto en el tooling del template).
- CI: rechazar PRs con mensajes que no cumplan el formato.
