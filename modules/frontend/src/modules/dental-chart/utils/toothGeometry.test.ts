import { describe, expect, it } from "vitest";
import { crownSlots, slotBox } from "./toothGeometry";

/** El lado más corto de la caja de cada cara: lo que el dedo tiene para acertar. */
const thinnest = (fdi: number, size: number, opts?: { minCentralRatio?: number }) =>
  Math.min(...crownSlots(fdi, size, opts).slots.map((s) => {
    const b = slotBox(s.points);
    return Math.min(b.width, b.height);
  }));

describe("crownSlots", () => {
  it("el dibujo no cambia: la franja incisal de una anterior sigue en 16 % de la corona", () => {
    const incisal = crownSlots(11, 56).slots.find((s) => s.surface === "INCISAL")!;
    // Los puntos se redondean a un decimal: tolerancia de 0,15.
    expect(Math.abs(slotBox(incisal.points).height - 48 * 0.16)).toBeLessThan(0.15);
  });

  it("en la pieza ampliada (240 px, update-dental-chart-touch D3) toda cara mide al menos 44 px, también la incisal", () => {
    for (const fdi of [16, 11, 13, 36, 41, 55, 51, 84]) {
      expect(thinnest(fdi, 240, { minCentralRatio: 0.24 }), `pieza ${fdi}`).toBeGreaterThanOrEqual(44);
    }
  });

  it("sin la proporción mínima, la incisal ampliada no alcanzaría los 44 px", () => {
    expect(thinnest(11, 240)).toBeLessThan(44);
  });
});
