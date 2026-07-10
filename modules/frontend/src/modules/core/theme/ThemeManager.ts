import type { DerivedTheme, TokenMap } from "./deriveTheme";

/**
 * Aplica/retira el tema de la organización sobre el DOM, aislado de React (design.md D5.1).
 * Inyecta un único `<style>` con las variables CSS para `:root` (claro) y `.dark` (oscuro),
 * de modo que el `ThemeToggle` siga alternando entre ambos. `reset()` lo retira → vuelven
 * los valores por defecto del producto (los de `globals.css`).
 */
const STYLE_ID = "odr-org-theme";

function toCssBlock(selector: string, tokens: TokenMap): string {
  const body = Object.entries(tokens)
    .map(([name, value]) => `--${name}: ${value};`)
    .join(" ");
  return `${selector} { ${body} }`;
}

export const ThemeManager = {
  /** Escribe las variables de marca (claro + oscuro) en un `<style>` dedicado. */
  apply(theme: DerivedTheme): void {
    const css = `${toCssBlock(":root", theme.light)}\n${toCssBlock(".dark", theme.dark)}`;
    let style = document.getElementById(STYLE_ID) as HTMLStyleElement | null;
    if (!style) {
      style = document.createElement("style");
      style.id = STYLE_ID;
      document.head.appendChild(style);
    }
    style.textContent = css;
  },

  /** Retira el tema de la organización → valores por defecto del producto. */
  reset(): void {
    document.getElementById(STYLE_ID)?.remove();
  },
};
