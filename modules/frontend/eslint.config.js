import js from "@eslint/js";
import globals from "globals";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import { defineConfig, globalIgnores } from "eslint/config";

// ── Límites entre módulos (update-module-boundaries, ADR-0021, docs/frontend.md §1.1) ──────────
//
// Mapa ÚNICO de dependencias permitidas entre `src/modules/*`: cada módulo puede importar de sí
// mismo, de `core` y de los módulos que declara aquí. Es el grafo real a 2026-11-27: las aristas
// están aceptadas, no son un ideal. Una arista nueva = una línea aquí, justificada en el PR.
// Todo directorio de `src/modules/` debe tener entrada (lo comprueba eslint.boundaries.test.ts).
//
// Alcance: imports estáticos con alias `@/modules/...` en código de producción. NO cubre tests
// (`*.test.*`, `*.spec.*`, carpetas `test/`), imports relativos o dinámicos, ni `screens/`,
// `routes/`, `layouts/` o `store/`, que orquestan módulos por diseño.
export const ALLOWED = {
  appointments: ["patients", "staff"],
  auth: ["staff"],
  branding: [],
  "clinical-history": ["appointments", "dental-chart", "patients", "staff"],
  core: [],
  "dental-chart": ["staff"],
  home: ["patients"],
  patients: [],
  staff: [],
};

// Un bloque por módulo, con `files` DISJUNTOS y generados en un solo bucle: en flat config, si un
// archivo cae en dos bloques con `no-restricted-imports`, el último REEMPLAZA las `patterns` del
// primero. Por eso ningún otro bloque de este archivo configura esa regla sobre `src/modules/**`.
// El glob termina en extensión a propósito: con `src/modules/<m>/**` ESLint 10 ignora el archivo.
const moduleBoundaries = Object.entries(ALLOWED).map(([module, deps]) => ({
  files: [`src/modules/${module}/**/*.{ts,tsx}`],
  ignores: ["**/*.test.{ts,tsx}", "**/*.spec.{ts,tsx}", "**/test/**"],
  rules: {
    "no-restricted-imports": [
      "error",
      {
        patterns: Object.keys(ALLOWED)
          .filter((other) => other !== module && other !== "core" && !deps.includes(other))
          .map((other) => ({
            group: [`@/modules/${other}`, `@/modules/${other}/**`],
            message:
              `"${module}" no puede importar de "${other}": la dependencia no está declarada en ` +
              "ALLOWED (eslint.config.js). Ver docs/frontend.md §1.1.",
          })),
      },
    ],
  },
}));

export default defineConfig([
  globalIgnores([
    "dist",
    "coverage",
    "test-results",
    "playwright-report",
    "src/modules/core/services/generated", // código generado por orval (se valida con tsc, no con eslint)
  ]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // ── "Un archivo = un componente" (docs/frontend.md §1), enforced por la BUILD ──────────────
    //
    // Es el equivalente frontend del tripwire de ArchUnit del backend: la regla existía escrita y
    // aun así se incumplía (skeletons y estados declarados dentro de la feature que reemplazan).
    // Un skeleton enterrado obliga a leer la feature entera para encontrarlo, y no se puede
    // probar ni reutilizar solo. Por defecto, no por disciplina.
    //
    // Alcance: SOLO el código de dominio (pantallas y componentes de módulo). `core/ui` queda
    // FUERA a propósito: allí una FAMILIA de primitivos comparte archivo por convención de shadcn
    // (`dialog.tsx` exporta Dialog, DialogContent, DialogHeader…). Es la excepción documentada,
    // no una puerta trasera: si un componente de dominio quiere colarse ahí, la regla de §4.1
    // ("¿lo usaría otra capacidad?") es la que manda.
    files: ["src/screens/**/*.tsx", "src/modules/*/components/**/*.tsx"],
    ignores: ["src/modules/core/**", "**/*.test.tsx"],
    plugins: { react },
    // Versión EXPLÍCITA, no "detect": la detección automática usa una API que ESLint 10 ya no
    // expone y revienta la regla ("contextOrFilename.getFilename is not a function"). Sin esto,
    // además, el plugin avisa en cada ejecución.
    settings: { react: { version: "19.2" } },
    rules: {
      // ignoreStateless: false -> también cuentan los componentes función, que es justo el caso.
      "react/no-multi-comp": ["error", { ignoreStateless: false }],
    },
  },
  ...moduleBoundaries,
]);
