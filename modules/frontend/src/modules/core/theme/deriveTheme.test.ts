import { describe, it, expect } from "vitest";
import { deriveTheme } from "./deriveTheme";

/** Extrae {h,s,l} de un triplete "H S% L%". */
function parse(triplet: string) {
  const [h, s, l] = triplet.replace(/%/g, "").split(/\s+/).map(Number);
  return { h, s, l };
}

describe("deriveTheme", () => {
  it("devuelve null cuando no hay color primario (usa defaults del producto)", () => {
    expect(deriveTheme({})).toBeNull();
    expect(deriveTheme({ brandPrimary: null })).toBeNull();
  });

  it("color de marca oscuro → texto claro (contraste)", () => {
    const theme = deriveTheme({ brandPrimary: "#1a1a1a" })!;
    expect(theme.light["primary-foreground"]).toBe("0 0% 100%"); // blanco
  });

  it("color de marca claro → texto oscuro (contraste)", () => {
    const theme = deriveTheme({ brandPrimary: "#ffe600" })!; // amarillo
    expect(parse(theme.light["primary-foreground"]).l).toBeLessThan(20); // oscuro
  });

  it("acota la luminosidad del primario por modo", () => {
    const theme = deriveTheme({ brandPrimary: "#fafafa" })!; // casi blanco (L~98%)
    expect(parse(theme.light.primary).l).toBe(80); // clamped en modo claro

    const darkBrand = deriveTheme({ brandPrimary: "#0a0a0a" })!; // casi negro
    expect(parse(darkBrand.dark.primary).l).toBeGreaterThanOrEqual(55); // iluminado en dark
    expect(parse(darkBrand.dark.primary).l).toBeLessThanOrEqual(65);
  });

  it("deriva el secundario del primario cuando es null", () => {
    const primary = deriveTheme({ brandPrimary: "#7f1aa6" })!; // púrpura, hue ~292
    const secHue = parse(primary.light.secondary).h;
    const priHue = parse(primary.light.primary).h;
    expect(secHue).toBe(priHue);
  });

  it("usa el secundario definido cuando existe", () => {
    const theme = deriveTheme({ brandPrimary: "#7f1aa6", brandSecondary: "#16c3b2" })!; // teal ~174
    expect(parse(theme.light.secondary).h).toBeGreaterThan(160);
    expect(parse(theme.light.secondary).h).toBeLessThan(190);
  });

  it("no toca los tokens semánticos (no los sobreescribe)", () => {
    const theme = deriveTheme({ brandPrimary: "#7f1aa6" })!;
    for (const semantic of ["destructive", "success", "warning"]) {
      expect(theme.light[semantic]).toBeUndefined();
      expect(theme.dark[semantic]).toBeUndefined();
    }
  });

  it("los neutros conservan S y L del producto (solo cambia el hue)", () => {
    const theme = deriveTheme({ brandPrimary: "#7f1aa6" })!;
    const muted = parse(theme.light.muted);
    expect(muted.s).toBe(20); // S del producto intacta
    expect(muted.l).toBe(96); // L del producto intacta
    expect(muted.h).toBeGreaterThan(280); // hue de la marca (púrpura)
  });

  it("marca contrastWarning cuando el color no alcanza AA como primario", () => {
    const theme = deriveTheme({ brandPrimary: "#808080" })!; // gris medio: mal contraste
    expect(theme.contrastWarning).toBe(true);
  });

  it("no marca warning con un color de buen contraste", () => {
    const theme = deriveTheme({ brandPrimary: "#7f1aa6" })!; // púrpura oscuro: buen contraste
    expect(theme.contrastWarning).toBe(false);
  });
});
