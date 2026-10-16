/**
 * Anclas que la guía del odontograma resalta (update-dental-chart-guidance design D2). El odontograma las pone como
 * `data-guide` sobre los elementos que de verdad reciben el click; la guía las busca por el mismo nombre.
 */
export const GUIDE_TOOTH = 16;
/** Espacio entre piezas de referencia: el contiguo a la pieza de referencia. */
export const GUIDE_GAP: readonly [number, number] = [15, 16];

export const CHART_GUIDE_ANCHOR = {
  toothNumber: "tooth-number",
  toothSurface: "tooth-surface",
  betweenTeeth: "between-teeth",
  arch: "arch",
  range: "range",
  search: "search",
} as const;

export type ChartGuideAnchor = (typeof CHART_GUIDE_ANCHOR)[keyof typeof CHART_GUIDE_ANCHOR];

export function findChartAnchor(anchor: ChartGuideAnchor): Element | null {
  return document.querySelector(`[data-guide="${anchor}"]`);
}
