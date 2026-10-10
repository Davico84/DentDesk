import { describe, it, expect } from "vitest";
import type { DentalChartResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { buildFindingSchema, findingFormDefaults, missingDentalChartFields, type FindingFormValues } from "./dentalChart";

const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
  { type: "CORONA", subtype: "CM", acronym: "CM", scope: "TOOTH", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: true },
  { type: "MOVILIDAD_PATOLOGICA", subtype: "", acronym: "M", scope: "TOOTH", colorBehavior: "SIEMPRE_ROJO", requiresGrade: true, requiresSpecification: false },
];

function reviewedBlock(specifications: string | undefined, findingTypes: [string, string][]): DentalChartResponse {
  return {
    status: "OPEN",
    specifications,
    specificationsReviewed: true,
    observationsReviewed: true,
    findings: findingTypes.map(([type, subtype], i) => ({ id: i + 1, blockId: 1, type, subtype, location: { locationKind: "TOOTH", toothA: 16 } })),
  } as DentalChartResponse;
}

describe("buildFindingSchema — campos que no corresponden a la localización (paridad con ValidFindingLocationValidator)", () => {
  const TOOTH_CATALOG: DentalFindingCatalogEntryResponse[] = [
    ...CATALOG,
    { type: "GIROVERSION", subtype: "", acronym: undefined, scope: "TOOTH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
    { type: "PROTESIS_PARCIAL_FIJA", subtype: "", acronym: undefined, scope: "TOOTH_RANGE", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: false },
  ];

  function locationErrors(values: FindingFormValues): string[] {
    const result = buildFindingSchema(TOOTH_CATALOG).safeParse(values);
    return result.success ? [] : result.error.issues.map((i) => i.path.join("."));
  }

  it("rechaza piezas de rango en un hallazgo que no es de rango (spec: Rechazar piezas de rango…)", () => {
    const values = { ...findingFormDefaults({ locationKind: "TOOTH", toothA: 21, teeth: [21, 22] }), type: "GIROVERSION" };
    expect(locationErrors(values)).toEqual(["location.teeth"]);
  });

  it("rechaza una pieza suelta en un rango", () => {
    const values = {
      ...findingFormDefaults({ locationKind: "TOOTH_RANGE", toothA: 14, teeth: [14, 13] }),
      type: "PROTESIS_PARCIAL_FIJA",
      condition: "GOOD" as const,
    };
    expect(locationErrors(values)).toEqual(["location.toothA"]);
  });

  it("rechaza una superficie en un hallazgo de pieza completa", () => {
    const values = { ...findingFormDefaults({ locationKind: "TOOTH", toothA: 21, surface: "MESIAL" }), type: "GIROVERSION" };
    expect(locationErrors(values)).toEqual(["location.surface"]);
  });

  it("acepta la localización con solo sus campos", () => {
    const values = { ...findingFormDefaults({ locationKind: "TOOTH", toothA: 21 }), type: "GIROVERSION" };
    expect(locationErrors(values)).toEqual([]);
  });
});

describe("missingDentalChartFields — especificación exigida por el tipo (espejo de DentalChartChecklist)", () => {
  it("con una corona registrada, especificaciones revisadas pero vacías no alcanzan", () => {
    expect(missingDentalChartFields(reviewedBlock(undefined, [["CORONA", "CM"]]), CATALOG)).toEqual(["specifications"]);
    expect(missingDentalChartFields(reviewedBlock("  ", [["CORONA", "CM"]]), CATALOG)).toEqual(["specifications"]);
  });

  it("con el detalle escrito, está completo", () => {
    expect(missingDentalChartFields(reviewedBlock("Corona dorada en 16", [["CORONA", "CM"]]), CATALOG)).toEqual([]);
  });

  it("la movilidad no exige especificación: su grado va en la sigla (6.1.19)", () => {
    expect(missingDentalChartFields(reviewedBlock(undefined, [["MOVILIDAD_PATOLOGICA", ""]]), CATALOG)).toEqual([]);
  });
});

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
