import js from "@eslint/js";
import globals from "globals";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import { defineConfig, globalIgnores } from "eslint/config";

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
]);
