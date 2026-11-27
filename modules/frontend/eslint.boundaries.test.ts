/**
 * Límites entre módulos del frontend (update-module-boundaries, design D1 y D4): la regla de lint
 * se ve fallar con la config REAL, sin tocar `src/`. Cada caso lintea un archivo falso con
 * `lintText`, así que no depende de que el código actual tenga o no la violación.
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { ESLint } from "eslint";
import { beforeAll, describe, expect, it } from "vitest";
import { ALLOWED } from "./eslint.config.js";

const FRONTEND = path.dirname(fileURLToPath(import.meta.url));
const MODULES_DIR = path.join(FRONTEND, "src/modules");
const GENERATED_DIR = path.join(MODULES_DIR, "core/services/generated");

const allowed: Record<string, readonly string[]> = ALLOWED;
const modules = Object.keys(allowed);

let eslint: ESLint;

beforeAll(() => {
  eslint = new ESLint({ cwd: FRONTEND, overrideConfigFile: path.join(FRONTEND, "eslint.config.js") });
});

/** Errores de `no-restricted-imports` al lintear `code` como si viviera en `filePath`. */
async function boundaryErrors(filePath: string, code: string): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath });
  return result.messages
    .filter((m) => m.ruleId === "no-restricted-imports")
    .map((m) => m.message);
}

function importFrom(specifier: string): string {
  return `import { x } from "${specifier}";\nexport const y = x;\n`;
}

describe("límites entre módulos del frontend", () => {
  it("un import declarado en el mapa no da error", async () => {
    expect(
      await boundaryErrors(
        "src/modules/clinical-history/components/X.tsx",
        importFrom("@/modules/staff/hooks/useProfessionalCredential"),
      ),
    ).toEqual([]);
  });

  it("un import no declarado da error y nombra al módulo importado", async () => {
    const errors = await boundaryErrors(
      "src/modules/patients/components/X.tsx",
      importFrom("@/modules/staff/hooks/useProfessionalCredential"),
    );
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('"staff"');
  });

  it("también cuenta el import del módulo sin subruta", async () => {
    expect(await boundaryErrors("src/modules/patients/X.ts", importFrom("@/modules/staff"))).toHaveLength(1);
  });

  it("core no puede importar de un módulo de negocio", async () => {
    expect(
      await boundaryErrors("src/modules/core/components/X.tsx", importFrom("@/modules/patients/hooks/usePatients")),
    ).toHaveLength(1);
  });

  it("core puede importar de sí mismo", async () => {
    expect(
      await boundaryErrors("src/modules/core/components/X.tsx", importFrom("@/modules/core/ui/button")),
    ).toEqual([]);
  });

  it("un archivo de prueba está exento", async () => {
    expect(
      await boundaryErrors("src/modules/patients/components/X.test.tsx", importFrom("@/modules/auth/hooks/useAuth")),
    ).toEqual([]);
    expect(
      await boundaryErrors("src/modules/patients/test/helpers.ts", importFrom("@/modules/auth/hooks/useAuth")),
    ).toEqual([]);
  });

  // Cubre que ningún bloque pisa a otro (en flat config el último `no-restricted-imports` reemplaza
  // al anterior): cada módulo, incluido core, recibe TODAS sus restricciones y solo esas.
  it("cada módulo recibe exactamente las restricciones de su entrada del mapa", async () => {
    const wrong: string[] = [];
    for (const from of modules) {
      for (const to of modules) {
        const expected = to !== from && to !== "core" && !allowed[from].includes(to);
        const errors = await boundaryErrors(`src/modules/${from}/X.ts`, importFrom(`@/modules/${to}/x`));
        if ((errors.length > 0) !== expected) {
          wrong.push(`${from} → ${to}: esperaba ${expected ? "error" : "sin error"}`);
        }
      }
    }
    expect(wrong).toEqual([]);
  });

  it("todo directorio de src/modules tiene entrada en el mapa, y toda entrada tiene directorio", () => {
    const dirs = readdirSync(MODULES_DIR).filter((d) => statSync(path.join(MODULES_DIR, d)).isDirectory());
    expect([...dirs].sort()).toEqual([...modules].sort());
  });

  it("el código generado por orval no importa de módulos de negocio", () => {
    const offending: string[] = [];
    const walk = (dir: string) => {
      for (const entry of readdirSync(dir)) {
        const full = path.join(dir, entry);
        if (statSync(full).isDirectory()) {
          walk(full);
        } else if (/\.(ts|tsx)$/.test(entry)) {
          for (const match of readFileSync(full, "utf8").matchAll(/["']@\/modules\/([a-z-]+)/g)) {
            if (match[1] !== "core") {
              offending.push(`${path.relative(FRONTEND, full)} → ${match[1]}`);
            }
          }
        }
      }
    };
    walk(GENERATED_DIR);
    expect(offending).toEqual([]);
  });
});
