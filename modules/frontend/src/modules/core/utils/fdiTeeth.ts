/**
 * El conjunto real de números FDI (notación universal) — paridad con `FdiTooth.java` (design.md
 * D5/D7 de `add-clinical-history`/`add-dental-chart`): permanentes 11–18/21–28/31–38/41–48,
 * temporales 51–55/61–65/71–75/81–85. Nunca un rango numérico laxo: aceptaría piezas que no
 * existen (19, 20, 29...). Fuente única compartida por `clinical-history` (diagnóstico por pieza)
 * y `dental-chart` (odontograma): no se duplica.
 */
function quadrant(quadrantNumber: number, teethInQuadrant: number): number[] {
  return Array.from({ length: teethInQuadrant }, (_, i) => quadrantNumber * 10 + (i + 1));
}

export const PERMANENT_QUADRANTS = [1, 2, 3, 4] as const;
export const TEMPORARY_QUADRANTS = [5, 6, 7, 8] as const;

export const VALID_FDI_TEETH = new Set<number>([
  ...quadrant(1, 8), ...quadrant(2, 8), ...quadrant(3, 8), ...quadrant(4, 8),
  ...quadrant(5, 5), ...quadrant(6, 5), ...quadrant(7, 5), ...quadrant(8, 5),
]);

/** Posición dentro del cuadrante que separa anteriores (incisivos/caninos) de posteriores — igual en permanente y temporal. */
const LAST_ANTERIOR_POSITION = 3;

export function isFdiValid(fdiNumber: number): boolean {
  return VALID_FDI_TEETH.has(fdiNumber);
}

/** `true` si la pieza es anterior (incisivo o canino): posición 1-3 del cuadrante. */
export function isAnteriorTooth(fdiNumber: number): boolean {
  return (fdiNumber % 10) <= LAST_ANTERIOR_POSITION;
}

/** `true` si la pieza es de dentición temporal (cuadrantes 5-8). */
export function isTemporaryTooth(fdiNumber: number): boolean {
  return Math.floor(fdiNumber / 10) >= 5;
}

/** `true` si la pieza pertenece al arco superior (cuadrantes 1, 2, 5, 6). */
export function isUpperArchTooth(fdiNumber: number): boolean {
  const q = Math.floor(fdiNumber / 10);
  return q === 1 || q === 2 || q === 5 || q === 6;
}

export function teethInQuadrant(q: number): number[] {
  return quadrant(q, q <= 4 ? 8 : 5);
}

/** Las siete superficies dentales (M/D/V/P/L/O/I) — mismos códigos que `ToothSurface.java`. */
export type ToothSurfaceCode = "MESIAL" | "DISTAL" | "VESTIBULAR" | "PALATINO" | "LINGUAL" | "OCLUSAL" | "INCISAL";

const COMMON_SURFACES: ToothSurfaceCode[] = ["MESIAL", "DISTAL", "VESTIBULAR", "PALATINO", "LINGUAL"];

/**
 * Las superficies que aplican a una pieza (`FdiTooth.applicableSurfaces`, design.md D7): las
 * cinco comunes siempre, más incisal en anteriores u oclusal en posteriores.
 */
export function applicableSurfaces(fdiNumber: number): ToothSurfaceCode[] {
  return [...COMMON_SURFACES, isAnteriorTooth(fdiNumber) ? "INCISAL" : "OCLUSAL"];
}
