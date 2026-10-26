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

### `main` es lo que corre en producción

**Desde el 2026-10-11 hay despliegue** (`docs/deployment.md` §9): Render y Vercel publican `main`. Por eso `main` ya no
es "una rama atrasada": responde *"esto es lo que está corriendo"*. Render despliega solo si **todos los checks de la
CI pasan sobre el commit de `main`** (`render.yaml`, `autoDeployTrigger: checksPass`); con `main` en rojo, producción
se queda en la versión anterior.

### Ramas y tags que no se borran

- **`Starter-Template`** — tiene un commit que no está en `dev`. Se conserva como rama.
- **Los puntos de bifurcación son tags, no ramas** (`bifurcacion/*`). Una rama dice *"hay trabajo en curso acá"*; un tag dice *"este punto es un marcador fijo"*. Bifurcar desde un tag es igual de fácil: `git checkout -b lo-que-sea bifurcacion/saas-template`.

Hoy existen `bifurcacion/saas-template`, `bifurcacion/multi-tenancy` y `bifurcacion/openspec-inicial`.

⚠️ **Borrar una rama ya mergeada no pierde ningún commit** — se pierde solo el nombre, y el merge commit del PR ya lo registra. Por eso las 47 ramas de avance se borraron sin riesgo, y las tres bifurcaciones se convirtieron en tags antes de borrarlas.

## 5c. Ciclo de un change y de un release

Acordado el **2026-10-15**, después de un día con cinco PRs a `main` (#89, #91, #94, #97, #99), varios solo para
arreglar el anterior, y cuatro fallos que aparecieron recién en la CI o en producción.

```
propose → prompt para Codex (en el chat) → revisión incorporada → PR → merge a dev
apply   → antes del PR: tests nuevos en condiciones de CI + suite completa
        → prompt para Codex (en el chat) → revisión incorporada → PR → merge a dev
release → PR dev → main solo cuando hay algo para producción, con la CI de dev en verde sobre el merge
```

- **La revisión de Codex va antes del PR.** El prompt de revisión se pasa en el chat, no en la descripción del PR;
  lo que Codex encuentre se incorpora en la misma rama y recién entonces se abre el PR (corregido el 2026-10-26).
  Mergear sin revisión obliga a incorporarla en la rama siguiente.
- **Changes chicos: un solo PR** con propuesta y apply. Los grandes (varias decisiones de diseño, concurrencia, API
  nueva) mantienen los dos: la revisión previa de `add-staff` cerró tres carreras antes de escribir código.
- **Antes de abrir un PR con tests nuevos, correrlos como en la CI**: el runner tiene 2 núcleos, así que el pool común
  de hilos tiene **uno** (`JAVA_TOOL_OPTIONS=-Djava.util.concurrent.ForkJoinPool.common.parallelism=1`). Y nunca
  comprobar un id o un valor buscándolo como texto en un JSON: se compara el campo (`JsonPath`). Los dos casos
  fallaron solo en la CI (`docs/testing.md`).
- **Release a `main` por valor, no por change.** Se sube cuando hay algo que usar en producción, y recién cuando la CI
  de `dev` pasó sobre el commit de merge (no basta con que pasara en el PR: puede ser otra ejecución).
- **Un fallo de la CI se diagnostica con `gh run view <id> --log-failed`**; no hace falta copiar el log a mano.
- **Sin protección de rama**: el repo es privado en el plan gratis y GitHub no permite reglas que exijan los checks
  (requiere GitHub Pro o repo público). El freno real hoy es Render, que no despliega `main` en rojo.

## 6. Automatización (pendiente de andamiaje)

- **commitlint** + config Conventional Commits para validar en `commit-msg` (hook) y en CI.
- Front: `lint-staged` en pre-commit (ya previsto en el tooling del template).
- CI: rechazar PRs con mensajes que no cumplan el formato.
