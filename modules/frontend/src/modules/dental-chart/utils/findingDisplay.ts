import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";

/** Textos de un hallazgo para la lista (tabla o bloques): sigla del catálogo y localización legible. */
export function siglaOf(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): string {
  const entry = catalog?.find((e) => e.type === f.type && (e.subtype ?? "") === (f.subtype ?? ""));
  return entry?.acronym ?? "";
}

export function locationSummary(f: DentalChartFindingResponse): string {
  const l = f.location;
  if (!l) return "";
  switch (l.locationKind) {
    case "SURFACE":
      return `Pieza ${l.toothA}, superficie ${l.surface}`;
    case "TOOTH":
      return `Pieza ${l.toothA}`;
    case "BETWEEN_TEETH":
      return `Entre las piezas ${l.toothA} y ${l.toothB}`;
    case "TOOTH_RANGE":
      return `Rango: piezas ${(l.teeth ?? []).join(", ")}`;
    case "ARCH":
      return `Arco ${l.arch?.toLowerCase()}`;
    default:
      return "";
  }
}
