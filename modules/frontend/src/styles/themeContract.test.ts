/// <reference types="node" />
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { compile } from "@tailwindcss/node";
import { beforeAll, describe, expect, it } from "vitest";

/**
 * Requirement "Tema definido por tokens CSS" (project-foundation, `update-tailwind-v4` design.md D7).
 * Compila el `globals.css` REAL con Tailwind (vitest corre con `css: false`, así que ningún otro
 * test ejercita el CSS) y comprueba que las utilidades salen de las variables.
 */
const DIR = import.meta.dirname;
const SOURCE = readFileSync(join(DIR, "globals.css"), "utf8");

let build: (candidates: string[]) => string;

beforeAll(async () => {
  const compiler = await compile(SOURCE, { base: DIR, onDependency: () => {} });
  build = (candidates) => compiler.build(candidates);
});

/** Bloque de declaraciones de un selector (`:root`, `.dark`) en el CSS fuente. */
function declarations(selector: string): Map<string, string> {
  const start = SOURCE.indexOf(`${selector} {`);
  const body = SOURCE.slice(start, SOURCE.indexOf("}", start));
  return new Map([...body.matchAll(/--([\w-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]));
}

describe("tema definido por tokens CSS", () => {
  it("las utilidades de color se resuelven desde las variables", () => {
    const css = build(["bg-primary", "text-muted-foreground", "bg-brand-cta"]);
    expect(css).toMatch(/\.bg-primary\s*\{[^}]*var\(--primary\)/);
    expect(css).toMatch(/\.text-muted-foreground\s*\{[^}]*var\(--muted-foreground\)/);
    expect(css).toMatch(/\.bg-brand-cta\s*\{[^}]*var\(--brand-cta\)/);
  });

  it("el modificador de opacidad sigue usando la variable", () => {
    expect(build(["bg-primary/10"])).toMatch(/bg-primary\\\/10[\s\S]*var\(--primary\)/);
  });

  it("dark: depende de un ancestro con la clase dark, no de prefers-color-scheme", () => {
    const css = build(["dark:hidden"]);
    expect(css).toContain(".dark");
    expect(css).not.toContain("prefers-color-scheme");
  });

  it("cada token de color está definido en claro y en oscuro", () => {
    const light = declarations(":root");
    const dark = declarations(".dark");
    const colorTokens = [...light.keys()].filter((name) => name !== "radius");
    expect(colorTokens.length).toBeGreaterThan(20);
    expect(colorTokens.filter((name) => !dark.has(name))).toEqual([]);
    expect([...dark.keys()].filter((name) => !light.has(name))).toEqual([]);
  });

  it("los tokens son colores completos, no canales sueltos", () => {
    for (const [name, value] of [...declarations(":root"), ...declarations(".dark")]) {
      if (name === "radius") continue;
      expect(value, name).toMatch(/^(hsl|rgb)\(|^#/);
    }
  });
});
