# Tooling y Setup — OdontoRisas

Guía **reproducible** del tooling de calidad. Adaptada del template FixRiver a la estructura real de este repo (monorepo con `modules/frontend`, hooks de git en la **raíz**). Complementa `docs/testing.md` (pruebas) y `docs/coding-standards.md`.

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

Ya configurada en `.github/workflows/ci.yml` (corre en push/PR a `main` y `dev`):
- **openspec**: `npx -y @fission-ai/openspec@latest validate --all --strict`.
- **backend**: `./mvnw -B verify` (si existe `modules/backend/`).
- **frontend**: `pnpm install --frozen-lockfile` + `pnpm validate` (si existe `modules/frontend/`).
