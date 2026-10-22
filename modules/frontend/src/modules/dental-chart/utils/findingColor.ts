import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";

// Rojo/azul normativos (NTS 188, RM 559-2022) — no tokens de tema: es el mismo color exacto
// tanto en el odontograma (Odontogram.tsx) como en la lista de hallazgos (FindingList.tsx),
// nunca el hex del diseño de turno. Único par de colores compartido entre ambos archivos.
export const RED = "#FF0000"; // por hacer / patológico
export const BLUE = "#0000FF"; // realizado / buen estado

export function findingColor(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): string {
  const entry = catalog?.find((e) => e.type === f.type && (e.subtype ?? "") === (f.subtype ?? ""));
  if (entry?.colorBehavior === "SIEMPRE_ROJO") return RED;
  if (entry?.colorBehavior === "SIEMPRE_AZUL") return BLUE;
  return f.condition === "POOR" ? RED : BLUE;
}

/** "Por hacer" (rojo) / "Realizado" (azul) — misma regla de color que {@link findingColor}. */
export function findingStatusLabel(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): "Por hacer" | "Realizado" {
  return findingColor(f, catalog) === RED ? "Por hacer" : "Realizado";
}

/** Se ve rojo en el odontograma: lo pendiente que una consulta puede atender (update-consultation-note-findings D6). */
export function isSeenRed(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): boolean {
  return findingColor(f, catalog) === RED;
}
