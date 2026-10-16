import { describe, expect, it } from "vitest";
import type { ClickedLocation } from "@/modules/dental-chart/components/FindingDialog";
import { relocate } from "./relocation";

const surface16: ClickedLocation = { locationKind: "SURFACE", toothA: 16, surface: "OCLUSAL" };
const tooth36: ClickedLocation = { locationKind: "TOOTH", toothA: 36 };
const tooth14: ClickedLocation = { locationKind: "TOOTH", toothA: 14 };
const gap: ClickedLocation = { locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 };
const upperArch: ClickedLocation = { locationKind: "ARCH", arch: "SUPERIOR" };

describe("relocate (design D5)", () => {
  it("el mismo alcance se elige, también entre piezas (precedencia sobre las demás reglas)", () => {
    expect(relocate(surface16, "SURFACE")).toEqual({ kind: "select" });
    expect(relocate(gap, "BETWEEN_TEETH")).toEqual({ kind: "select" });
  });

  it("de una cara a la pieza completa de esa misma pieza", () => {
    expect(relocate(surface16, "TOOTH")).toEqual({
      kind: "goTo", location: { locationKind: "TOOTH", toothA: 16 }, action: "Registrarlo en la pieza 16",
    });
  });

  it("de una pieza al arco de esa pieza: la 36 es inferior y la 16 superior", () => {
    expect(relocate(tooth36, "ARCH")).toMatchObject({ kind: "goTo", location: { locationKind: "ARCH", arch: "INFERIOR" } });
    expect(relocate(surface16, "ARCH")).toMatchObject({ kind: "goTo", location: { locationKind: "ARCH", arch: "SUPERIOR" } });
  });

  it("una pieza temporal también resuelve su arco (55 superior, 75 inferior)", () => {
    expect(relocate({ locationKind: "TOOTH", toothA: 55 }, "ARCH")).toMatchObject({ location: { arch: "SUPERIOR" } });
    expect(relocate({ locationKind: "TOOTH", toothA: 75 }, "ARCH")).toMatchObject({ location: { arch: "INFERIOR" } });
  });

  it("de una pieza o una cara, un rango que empieza en ella", () => {
    expect(relocate(tooth14, "TOOTH_RANGE")).toEqual({ kind: "startRange", from: 14, action: "Empezar un rango desde la pieza 14" });
    expect(relocate(surface16, "TOOTH_RANGE")).toMatchObject({ kind: "startRange", from: 16 });
  });

  it("de una pieza completa a una cara: no se deduce cuál, se elige en el odontograma nombrando la pieza", () => {
    expect(relocate(tooth36, "SURFACE")).toEqual({ kind: "place", scope: "SURFACE", fromTooth: 36, action: "Elegir en el odontograma" });
  });

  it("hacia entre piezas siempre se elige en el odontograma", () => {
    expect(relocate(tooth36, "BETWEEN_TEETH")).toEqual({ kind: "place", scope: "BETWEEN_TEETH", fromTooth: 36, action: "Elegir en el odontograma" });
    expect(relocate(surface16, "BETWEEN_TEETH")).toMatchObject({ kind: "place", fromTooth: 16 });
    expect(relocate(upperArch, "BETWEEN_TEETH")).toMatchObject({ kind: "place", scope: "BETWEEN_TEETH" });
  });

  it("desde un espacio, un arco o un rango no hay pieza de la que deducir: se elige en el odontograma", () => {
    expect(relocate(gap, "TOOTH")).toEqual({ kind: "place", scope: "TOOTH", fromTooth: undefined, action: "Elegir en el odontograma" });
    expect(relocate(upperArch, "TOOTH_RANGE")).toMatchObject({ kind: "place", scope: "TOOTH_RANGE" });
    expect(relocate({ locationKind: "TOOTH_RANGE", teeth: [14, 15, 16] }, "ARCH")).toMatchObject({ kind: "place", scope: "ARCH" });
  });
});
