import type { ToothSurfaceCode } from "@/modules/core/utils/fdiTeeth";

export type Slot = { surface: ToothSurfaceCode; points: string };

/** Nombre de cada cara, en minúscula, para frases y nombres accesibles ("superficie oclusal"). */
export const SURFACE_LABEL: Record<ToothSurfaceCode, string> = {
  MESIAL: "mesial",
  DISTAL: "distal",
  VESTIBULAR: "vestibular",
  PALATINO: "palatino",
  LINGUAL: "lingual",
  OCLUSAL: "oclusal",
  INCISAL: "incisal",
};

/**
 * La corona: 5 polígonos reales (no un simple cuadrado con cruz), M siempre hacia la línea media
 * y V siempre hacia afuera — mismo cálculo que la referencia de diseño (Celda.dc.html).
 *
 * `minCentralRatio` (update-dental-chart-touch D3): alto mínimo de la cara central respecto de la
 * corona. El dibujo deja la franja incisal de las anteriores en 16 % (la anatomía); la pieza ampliada
 * del celular pide 24 % para que también esa cara mida ≥ 44 px. No cambia qué cara es cuál.
 */
export function crownSlots(
  fdi: number,
  size: number,
  { minCentralRatio = 0 }: { minCentralRatio?: number } = {},
): { slots: Slot[]; ring: { x: number; y: number; wh: number } } {
  const q = Math.floor(fdi / 10);
  const upper = q === 1 || q === 2 || q === 5 || q === 6;
  const leftHalf = q === 1 || q === 4 || q === 5 || q === 8;
  const anterior = fdi % 10 <= 3;

  const pad = 4;
  const S = size - pad * 2;
  const a = S * 0.28;
  const halfBand = Math.max(0.08, minCentralRatio / 2) * S;
  const y1 = anterior ? S / 2 - halfBand : a;
  const y2 = anterior ? S / 2 + halfBand : S - a;

  const raw: [number, number][][] = [
    [[0, 0], [S, 0], [S - a, y1], [a, y1]],
    [[S, 0], [S, S], [S - a, y2], [S - a, y1]],
    [[a, y2], [S - a, y2], [S, S], [0, S]],
    [[0, 0], [a, y1], [a, y2], [0, S]],
    [[a, y1], [S - a, y1], [S - a, y2], [a, y2]],
  ];
  const surfaces: ToothSurfaceCode[] = [
    upper ? "VESTIBULAR" : "LINGUAL",
    leftHalf ? "MESIAL" : "DISTAL",
    upper ? "PALATINO" : "VESTIBULAR",
    leftHalf ? "DISTAL" : "MESIAL",
    anterior ? "INCISAL" : "OCLUSAL",
  ];
  const slots = raw.map((shape, i) => ({
    surface: surfaces[i],
    points: shape.map(([x, y]) => `${(x + pad).toFixed(1)},${(y + pad).toFixed(1)}`).join(" "),
  }));
  return { slots, ring: { x: 1, y: 1, wh: size - 2 } };
}

/** Caja de un polígono de `crownSlots` (sus puntos "x,y x,y …"). */
export function slotBox(points: string): { x: number; y: number; width: number; height: number } {
  const pts = points.split(" ").map((p) => p.split(",").map(Number));
  const xs = pts.map(([x]) => x);
  const ys = pts.map(([, y]) => y);
  const x = Math.min(...xs);
  const y = Math.min(...ys);
  return { x, y, width: Math.max(...xs) - x, height: Math.max(...ys) - y };
}
