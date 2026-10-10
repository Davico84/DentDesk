/// <reference types="node" />
import { readdirSync, readFileSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Requirement "Sin colores literales en componentes" (project-foundation, `update-tailwind-v4`
 * design.md D7): cambiar un color se hace en `globals.css`, no en un componente. Las únicas
 * excepciones son colores que NO deben seguir al tema, cada una con su motivo.
 */
const SRC = join(import.meta.dirname, "..");

/** Archivo (relativo a `src/`, con `/`) → por qué puede tener colores literales. */
const EXCEPTIONS: Record<string, string> = {
  "modules/dental-chart/utils/findingColor.ts": "rojo y azul normativos de NTS 188 (5.12/5.13): no cambian con el tema",
  "modules/dental-chart/utils/chartPalette.ts": "el odontograma se dibuja como papel, igual en claro y en oscuro",
  "modules/branding/presets.ts": "los colores de marca de cada preset son datos, no estilo",
  "modules/core/theme/deriveTheme.ts": "genera los tokens del tema de cada clínica a partir de su color de marca",
};

// Un hex termina donde termina el hex: dentro de una clase arbitraria le sigue `_` (`#671a86_0%`),
// que `\b` considera parte de la palabra y dejaría pasar.
const LITERAL = /#[0-9a-fA-F]{3,8}(?![0-9a-fA-F])|\brgba?\(|\bhsla?\(/;

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((e) => e.isFile() && /\.(ts|tsx)$/.test(e.name) && !/\.test\.tsx?$/.test(e.name))
    .map((e) => join(e.parentPath, e.name))
    .filter((f) => !f.includes(`${sep}services${sep}generated${sep}`));
}

const toKey = (file: string) => relative(SRC, file).split(sep).join("/");

describe("sin colores literales en componentes", () => {
  const files = sourceFiles(SRC);

  it("ningún archivo fuera de las excepciones tiene colores literales", () => {
    const offenders = files
      .filter((f) => !(toKey(f) in EXCEPTIONS))
      .flatMap((f) =>
        readFileSync(f, "utf8")
          .split("\n")
          .map((line, i) => ({ line, n: i + 1 }))
          .filter(({ line }) => LITERAL.test(line))
          .map(({ line, n }) => `${toKey(f)}:${n}  ${line.trim()}`),
      );
    expect(offenders, "usa un token de globals.css en vez de un color literal").toEqual([]);
  });

  it("cada excepción sigue teniendo colores literales (si no, hay que sacarla de la lista)", () => {
    for (const key of Object.keys(EXCEPTIONS)) {
      const file = files.find((f) => toKey(f) === key);
      expect(file, `${key} ya no existe`).toBeDefined();
      expect(LITERAL.test(readFileSync(file!, "utf8")), `${key} ya no tiene colores literales`).toBe(true);
    }
  });
});
