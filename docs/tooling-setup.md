# Tooling y Setup — OdontoRisas

Guía **reproducible** del tooling de calidad. Adaptada del template FixRiver a la estructura real de este repo (monorepo con `modules/frontend`, hooks de git en la **raíz**). Complementa `docs/testing.md` (pruebas) y `docs/coding-style.md`.

> Comandos de frontend desde `modules/frontend/`. Los hooks de commit son de repo (raíz).

---

## 1. Scripts de frontend (`modules/frontend/package.json`)

```jsonc
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "lint": "eslint .",
    "typecheck": "tsc -b",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "validate": "pnpm typecheck && pnpm lint && pnpm test"
  }
}
```

> **`pnpm validate`** = chequeo completo (typecheck + lint + test). Úsalo antes de subir. El gestor es **pnpm** (`packageManager` fijado en `package.json` para que CI use la misma versión).

---

## 2. Testing — Vitest + RTL + MSW

Instalado en devDependencies: `vitest`, `@vitest/coverage-v8`, `jsdom`, `@testing-library/{react,jest-dom,user-event}`, `msw`.

Archivos (ya presentes):
- `vitest.config.ts` — plugin react, alias `@`, `environment: "jsdom"`, `setupFiles: ["./src/test/setup.ts"]`, `globals: false`, `define` de globals de Vite (`BACKEND_URL`), y **`pool: "threads"`**.
- `src/test/setup.ts` — `@testing-library/jest-dom/vitest` + `afterEach(cleanup)` + ciclo de vida de MSW (`listen/resetHandlers/close`) + shims de jsdom.
- `src/test/mocks/{handlers,server}.ts` — handlers por defecto y `setupServer`.
- `src/test/utils.tsx` — `renderWithProviders` (React Query + Router; se ampliará con sesión/i18n al existir).

> [!NOTE]
> **Windows**: se usa `pool: "threads"` en Vitest. El pool por defecto (`forks`) provocaba `Timeout waiting for worker to respond` al arrancar el worker.

Convención de tests y matriz de decisiones: `docs/testing.md`.

---

## 3. Commits — husky + commitlint (raíz del repo)

A diferencia del template (monorepo con `core.hooksPath` manual), aquí el `.git` y el `package.json` raíz están juntos, así que se usa **husky estándar en la raíz** (aplica a todos los módulos).

Instalado en el `package.json` **raíz**: `husky`, `@commitlint/cli`, `@commitlint/config-conventional`.

- `package.json` (raíz) → `"prepare": "husky"` (inicializa hooks tras `pnpm install`).
- `commitlint.config.mjs` (raíz) → `extends: ['@commitlint/config-conventional']` + tipos de `docs/commits.md`.
- `.husky/commit-msg` → `pnpm exec commitlint --edit "$1"` (rechaza mensajes no-conventional).

Activación: tras clonar, ejecutar **`pnpm install` en la raíz** una vez.

---

## 4. Pre-commit — lint-staged (frontend)

Hook `pre-commit` en la raíz que ejecuta `lint-staged` **acotado a `modules/frontend/`** (los commits de solo-backend no se ven afectados).

- `modules/frontend/lint-staged.config.mjs`:
  ```js
  export default {
    "*.{ts,tsx}": ["eslint --fix"],
    "*.{ts,tsx,json}": () => "tsc -b",
  };
  ```
- `.husky/pre-commit` (raíz): si hay archivos staged bajo `modules/frontend/`, hace `cd modules/frontend && pnpm exec lint-staged`.
- `lint-staged` está en las devDependencies de `modules/frontend`.

---

## 5. CI

Un workflow **por área** en `.github/workflows/`, cada uno con **path filter** (corre solo si cambia su área) y `concurrency` (cancela runs superados). Disparan en push/PR a `main` y `dev`.

| Workflow | Filtro de rutas | Jobs |
|---|---|---|
| `openspec.yml` | `openspec/**` | `validate` → `openspec validate --all --strict` |
| `frontend.yml` | `modules/frontend/**`, `contracts/**` | `lint` (typecheck + eslint) · `test` (`vitest --coverage` + artifact) · **`codegen-drift`** |
| `backend.yml` | `modules/backend/**` | `verify` (`./mvnw -B verify`, incluye **`ContractDriftIT`**) · *(futuro: lint Spotless/Checkstyle, coverage JaCoCo)* |

### 5.1 Los dos guardianes del contrato

El contrato OpenAPI es la fuente única de verdad, así que CI vigila que **la cadena entera** siga sincronizada. Hacen falta **las dos direcciones**: con una sola, el contrato puede mentir sobre el backend sin que nadie se entere (que es exactamente lo que pasaba).

```
backend ──[ContractDriftIT]──► contracts/openapi.json ──[codegen-drift]──► cliente generado
```

| Guardián | Dónde | Qué hace |
|---|---|---|
| **`ContractDriftIT`** | test, dentro del `verify` del backend | Compara la especificación que el backend publica **ahora** con `contracts/openapi.json`. **Cambiar la API sin regenerar el contrato rompe la build.** |
| **`codegen-drift`** | job del workflow de frontend | Regenera el cliente con orval y falla si difiere de lo commiteado. |

> **Por qué el primero es un test y no un job que arranque un servidor**: usa la misma maquinaria de springdoc, corre dentro del `verify` que CI ya ejecuta y rompe la build igual — sin añadir un servidor y un `curl` como piezas frágiles.

**Si CI te dice que hay drift**, regenera y commitea ambos:
```bash
# con el backend arrancado — OJO: con `-o`, nunca canalizando por un script
curl -o contracts/openapi.json http://localhost:8080/v3/api-docs
pnpm --filter odontorisas-frontend generate:api
```
> [!WARNING]
> Exporta el contrato con `curl -o`. Un `curl | python` en Windows lo dejó **doble codificado** (el `stdin` de Python usa cp1252, no UTF-8), lo que reventaba a `ContractDriftIT` y propagaba *mojibake* al cliente generado.

> [!NOTE]
> **Path filters + branch protection**: si más adelante marcas estos checks como *required*, un workflow que no dispara (porque su área no cambió) aparece como *pending/skipped* y puede bloquear el merge. Cuando llegue ese momento, usar "required workflows" a nivel de organización o un job agregador. Por ahora no hay branch protection.
