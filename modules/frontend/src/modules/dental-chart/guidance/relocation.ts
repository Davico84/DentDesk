import { isUpperArchTooth } from "@/modules/core/utils/fdiTeeth";
import type { ClickedLocation } from "@/modules/dental-chart/components/FindingDialog";
import type { FindingScope } from "./chartGuideSteps";

/** Dónde se registra cada alcance, para decirlo en una frase. */
export const SCOPE_PLACE: Record<FindingScope, string> = {
  SURFACE: "sobre una cara de la pieza",
  TOOTH: "sobre la pieza completa",
  BETWEEN_TEETH: "en el espacio entre dos piezas",
  ARCH: "sobre un arco completo",
  TOOTH_RANGE: "sobre varias piezas seguidas",
};

export type Relocation =
  | { kind: "select" }
  | { kind: "goTo"; location: ClickedLocation; action: string }
  | { kind: "startRange"; from: number; action: string }
  | { kind: "place"; scope: FindingScope; fromTooth?: number; action: string };

/** Lo que el usuario acepta para ir a otra localización (las filas "ir a…" y "elegir en el odontograma" de D5). */
export type AcceptedRelocation = { type: string; relocation: Exclude<Relocation, { kind: "select" }> };

const PLACE_ACTION = "Elegir en el odontograma";

/**
 * Qué hacer cuando, en el registro de `touched`, el usuario elige un tipo de alcance `scope` (design D5). Mismo
 * alcance → se elige. Si no, se ofrece ir a la localización deducible de lo tocado (la pieza completa desde una de sus
 * caras, el arco de esa pieza, un rango que empieza en ella) y, si no se deduce, elegirla en el odontograma (modo
 * ubicar). Ningún caso queda sin salida. Nunca decide por el usuario: el llamador ejecuta la acción solo si la acepta.
 */
export function relocate(touched: ClickedLocation, scope: FindingScope): Relocation {
  if (touched.locationKind === scope) return { kind: "select" };

  const tooth = touched.locationKind === "SURFACE" || touched.locationKind === "TOOTH" ? touched.toothA : undefined;
  if (tooth != null) {
    if (scope === "TOOTH") {
      return { kind: "goTo", location: { locationKind: "TOOTH", toothA: tooth }, action: `Registrarlo en la pieza ${tooth}` };
    }
    if (scope === "ARCH") {
      const arch = isUpperArchTooth(tooth) ? "SUPERIOR" : "INFERIOR";
      return {
        kind: "goTo",
        location: { locationKind: "ARCH", arch },
        action: `Registrarlo en el arco ${arch === "SUPERIOR" ? "superior" : "inferior"}`,
      };
    }
    if (scope === "TOOTH_RANGE") {
      return { kind: "startRange", from: tooth, action: `Empezar un rango desde la pieza ${tooth}` };
    }
  }
  return { kind: "place", scope, fromTooth: tooth, action: PLACE_ACTION };
}
