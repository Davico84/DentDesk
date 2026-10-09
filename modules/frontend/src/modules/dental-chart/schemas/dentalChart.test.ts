import { describe, it, expect } from "vitest";
import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { buildFindingSchema, findingFormDefaults, type FindingFormValues } from "./dentalChart";

const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
];

function surfaceFinding(toothA: number, surface: FindingFormValues["location"]["surface"]): FindingFormValues {
  return {
    ...findingFormDefaults({ locationKind: "SURFACE", toothA, surface }),
    type: "LESION_CARIES",
    subtype: "CD",
  };
}

function surfaceErrors(values: FindingFormValues): string[] {
  const result = buildFindingSchema(CATALOG).safeParse(values);
  if (result.success) return [];
  return result.error.issues.filter((i) => i.path.join(".") === "location.surface").map((i) => i.message);
}

describe("buildFindingSchema — superficie aplicable a la pieza (spec: Rechazar una superficie no aplicable)", () => {
  it("rechaza oclusal en un incisivo", () => {
    expect(surfaceErrors(surfaceFinding(11, "OCLUSAL"))).toEqual(["La superficie oclusal no aplica a la pieza 11."]);
  });

  it("rechaza incisal en una molar", () => {
    expect(surfaceErrors(surfaceFinding(16, "INCISAL"))).toEqual(["La superficie incisal no aplica a la pieza 16."]);
  });

  it("acepta incisal en un incisivo y oclusal en una molar", () => {
    expect(surfaceErrors(surfaceFinding(11, "INCISAL"))).toEqual([]);
    expect(surfaceErrors(surfaceFinding(16, "OCLUSAL"))).toEqual([]);
  });

  it("con una pieza inexistente no suma un segundo error de superficie (ya falla la pieza)", () => {
    expect(surfaceErrors(surfaceFinding(19, "OCLUSAL"))).toEqual([]);
  });
});
