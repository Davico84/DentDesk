import { describe, it, expect, afterEach } from "vitest";
import { ThemeManager } from "./ThemeManager";
import type { DerivedTheme } from "./deriveTheme";

const theme: DerivedTheme = {
  light: { primary: "hsl(292 72% 36%)" },
  dark: { primary: "hsl(292 65% 66%)" },
  contrastWarning: false,
};

afterEach(() => ThemeManager.reset());

describe("ThemeManager", () => {
  it("inyecta un <style> con :root (claro) y .dark (oscuro)", () => {
    ThemeManager.apply(theme);
    const el = document.getElementById("odr-org-theme");
    expect(el).not.toBeNull();
    expect(el!.textContent).toContain(":root {");
    expect(el!.textContent).toContain("--primary: hsl(292 72% 36%);");
    expect(el!.textContent).toContain(".dark {");
    expect(el!.textContent).toContain("--primary: hsl(292 65% 66%);");
  });

  // Cascada (revisión externa de update-tailwind-v4): el <style> de la clínica tiene la misma
  // especificidad que :root/.dark de globals.css, así que gana SOLO por ir después en el documento.
  // Se prueba el orden y no el valor calculado: jsdom no resuelve bien la cascada de variables CSS
  // (una prueba de getComputedStyle pasaba igual con el <style> insertado al principio).
  it("el <style> de la clínica queda después de cualquier hoja ya cargada", () => {
    const late = document.createElement("style");
    document.head.appendChild(late);
    try {
      ThemeManager.apply(theme);
      expect(document.head.lastElementChild?.id).toBe("odr-org-theme");
    } finally {
      late.remove();
    }
  });

  it("reset retira el <style>", () => {
    ThemeManager.apply(theme);
    ThemeManager.reset();
    expect(document.getElementById("odr-org-theme")).toBeNull();
  });

  it("aplicar dos veces reutiliza el mismo <style>", () => {
    ThemeManager.apply(theme);
    ThemeManager.apply(theme);
    expect(document.querySelectorAll("#odr-org-theme").length).toBe(1);
  });
});
