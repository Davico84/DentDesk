import { describe, expect, it } from "vitest";
import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { buildChartGuideSteps, examplesFor } from "./chartGuideSteps";
import { CHART_GUIDE_ANCHOR } from "./chartGuideAnchors";

const entry = (type: string, scope: DentalFindingCatalogEntryResponse["scope"], subtype = ""): DentalFindingCatalogEntryResponse =>
  ({ type, subtype, scope }) as DentalFindingCatalogEntryResponse;

const CATALOG = [
  entry("LESION_CARIES", "SURFACE", "MB"),
  entry("LESION_CARIES", "SURFACE", "CE"),
  entry("RESTAURACION_DEFINITIVA", "SURFACE"),
  entry("SELLANTE", "SURFACE"),
  entry("SUPERFICIE_DESGASTADA", "SURFACE"),
  entry("CORONA", "TOOTH"),
  entry("TRATAMIENTO_CONDUCTO", "TOOTH"),
  entry("DIASTEMA", "BETWEEN_TEETH"),
  entry("PROTESIS_COMPLETA", "ARCH"),
  entry("PROTESIS_PARCIAL_FIJA", "TOOTH_RANGE"),
];

describe("chartGuideSteps", () => {
  it("toma hasta 3 tipos por alcance, en orden y sin repetir los que tienen subtipos", () => {
    const examples = examplesFor(CATALOG, "SURFACE");
    expect(examples).toHaveLength(3);
    expect(examples[0]).toMatch(/caries/i);
    expect(new Set(examples).size).toBe(3);
  });

  it("arma introducción, las cinco localizaciones y el cierre, en ese orden", () => {
    const steps = buildChartGuideSteps(CATALOG);
    expect(steps.map((s) => s.anchor)).toEqual([
      undefined,
      CHART_GUIDE_ANCHOR.toothSurface,
      CHART_GUIDE_ANCHOR.toothNumber,
      CHART_GUIDE_ANCHOR.betweenTeeth,
      CHART_GUIDE_ANCHOR.arch,
      CHART_GUIDE_ANCHOR.range,
      CHART_GUIDE_ANCHOR.search,
    ]);
  });

  it("el paso de pieza completa nombra ejemplos del catálogo como corona", () => {
    const tooth = buildChartGuideSteps(CATALOG).find((s) => s.anchor === CHART_GUIDE_ANCHOR.toothNumber);
    expect(tooth?.examples?.join(" ")).toMatch(/corona/i);
  });

  it("omite la localización cuyo alcance no tiene tipos en el catálogo", () => {
    const steps = buildChartGuideSteps(CATALOG.filter((e) => e.scope !== "BETWEEN_TEETH"));
    expect(steps.some((s) => s.anchor === CHART_GUIDE_ANCHOR.betweenTeeth)).toBe(false);
    expect(steps).toHaveLength(6);
  });
});
