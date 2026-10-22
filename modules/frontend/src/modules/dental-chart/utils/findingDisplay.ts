import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel, subtypeOptionLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";

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

const SURFACE_TEXT: Record<string, string> = {
  MESIAL: "mesial",
  DISTAL: "distal",
  VESTIBULAR: "vestibular",
  PALATINO: "palatina",
  LINGUAL: "lingual",
  OCLUSAL: "oclusal",
  INCISAL: "incisal",
};

/** Dónde está el hallazgo, en una frase para un texto clínico (la nota de consulta). */
function locationText(f: DentalChartFindingResponse): string {
  const l = f.location;
  if (!l) return "";
  switch (l.locationKind) {
    case "SURFACE":
      return `Pieza ${l.toothA}, cara ${SURFACE_TEXT[l.surface ?? ""] ?? l.surface?.toLowerCase()}`;
    case "TOOTH":
      return `Pieza ${l.toothA}`;
    case "BETWEEN_TEETH":
      return `Entre las piezas ${l.toothA} y ${l.toothB}`;
    case "TOOTH_RANGE": {
      const teeth = l.teeth ?? [];
      return teeth.length > 0 ? `Piezas ${teeth[0]} a ${teeth[teeth.length - 1]}` : "Rango de piezas";
    }
    case "ARCH":
      return `Arco ${l.arch === "SUPERIOR" ? "superior" : "inferior"}`;
    default:
      return "";
  }
}

/**
 * Descripción de un hallazgo para guardarla en la nota de consulta (update-consultation-note-findings D2): pieza,
 * cara, hallazgo con su subtipo, estado y grado. Queda fija en la nota sellada, que se lee sin el odontograma.
 */
export function describeFinding(
  f: DentalChartFindingResponse,
  catalog: DentalFindingCatalogEntryResponse[] | undefined,
): string {
  const type = f.type ?? "";
  const subtype = f.subtype ?? "";
  const what = subtype
    ? `${findingTypeLabel(type)} ${subtypeOptionLabel(type, subtype, siglaOf(f, catalog) || subtype)}`
    : findingTypeLabel(type);
  const condition = f.condition === "POOR" ? ", mal estado" : f.condition === "GOOD" ? ", buen estado" : "";
  const grade = f.grade != null ? `, grado ${f.grade}` : "";
  return `${locationText(f)}: ${what}${condition}${grade}`;
}

/**
 * Ubicación del diagnóstico que se propone desde un hallazgo (design D5): el diagnóstico admite una sola pieza, así
 * que solo una cara o una pieza completa la fijan; entre piezas, un rango o un arco proponen la ubicación general.
 */
export function diagnosisLocationOf(f: DentalChartFindingResponse): { ubicacion: "PIEZA"; fdiNumber: number } | { ubicacion: "GENERAL" } {
  const l = f.location;
  if ((l?.locationKind === "SURFACE" || l?.locationKind === "TOOTH") && l.toothA != null) {
    return { ubicacion: "PIEZA", fdiNumber: l.toothA };
  }
  return { ubicacion: "GENERAL" };
}
