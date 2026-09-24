import { z } from "zod";
import type {
  DentalChartCreateRequest,
  DentalChartFindingRequest,
  DentalChartResponse,
  DentalChartTextRequest,
  DentalFindingCatalogEntryResponse,
} from "@/modules/core/services/generated/model";
import {
  DentalChartFindingRequestCondition,
  DentalChartLocationRequestArch,
  DentalChartLocationRequestLocationKind,
  DentalChartLocationRequestSurface,
} from "@/modules/core/services/generated/model";
import { isFdiValid } from "@/modules/core/utils/fdiTeeth";

// --- crear el odontograma inicial ---

export const dentalChartCreateSchema = z.object({
  examinedOn: z.string().trim().min(1, "La fecha del examen es obligatoria."),
});
export type DentalChartCreateValues = z.infer<typeof dentalChartCreateSchema>;

export function toCreateRequest(values: DentalChartCreateValues): DentalChartCreateRequest {
  return { examinedOn: values.examinedOn };
}

export function createDefaults(): DentalChartCreateValues {
  return { examinedOn: new Date().toISOString().slice(0, 10) };
}

// --- especificaciones / observaciones (design.md D5: "revisado" no vuelve a false al editar) ---

export const dentalChartTextSchema = z.object({ text: z.string() });
export type DentalChartTextValues = z.infer<typeof dentalChartTextSchema>;

export function toTextRequest(values: DentalChartTextValues): DentalChartTextRequest {
  return { text: values.text.trim() === "" ? undefined : values.text.trim() };
}

export function textDefaults(text?: string | null): DentalChartTextValues {
  return { text: text ?? "" };
}

// --- hallazgos ---

const EMPTY = "" as const;

export const findingFormSchema = z.object({
  type: z.union([z.string(), z.literal(EMPTY)]),
  subtype: z.string(),
  condition: z.union([z.enum(DentalChartFindingRequestCondition), z.literal(EMPTY)]),
  grade: z.union([z.number(), z.literal(EMPTY)]),
  location: z.object({
    locationKind: z.union([z.enum(DentalChartLocationRequestLocationKind), z.literal(EMPTY)]),
    toothA: z.union([z.number(), z.literal(EMPTY)]),
    toothB: z.union([z.number(), z.literal(EMPTY)]),
    surface: z.union([z.enum(DentalChartLocationRequestSurface), z.literal(EMPTY)]),
    arch: z.union([z.enum(DentalChartLocationRequestArch), z.literal(EMPTY)]),
    teeth: z.array(z.number()),
    /** Solo UI: el texto crudo "15,14,13" del que se derivan `teeth`. Nunca viaja al backend. */
    teethInput: z.string(),
  }),
});
export type FindingFormValues = z.infer<typeof findingFormSchema>;

export function findingFormDefaults(prefill?: Partial<FindingFormValues["location"]>): FindingFormValues {
  return {
    type: "",
    subtype: "",
    condition: "",
    grade: "",
    location: {
      locationKind: "",
      toothA: "",
      toothB: "",
      surface: "",
      arch: "",
      teeth: [],
      teethInput: "",
      ...prefill,
    },
  };
}

export function catalogEntryFor(
  catalog: DentalFindingCatalogEntryResponse[] | undefined,
  type: string,
  subtype: string,
): DentalFindingCatalogEntryResponse | undefined {
  if (!catalog || !type) return undefined;
  return catalog.find((e) => e.type === type && (e.subtype ?? "") === subtype);
}

/**
 * Subtipos disponibles para un tipo — el catálogo viaja por contrato, no se reinventa (design.md
 * D6): esto solo agrupa las filas que el backend ya mandó, no conoce reglas propias.
 */
export function subtypesFor(catalog: DentalFindingCatalogEntryResponse[] | undefined, type: string) {
  if (!catalog || !type) return [];
  return catalog.filter((e) => e.type === type && e.subtype !== "");
}

/**
 * Reglas cruzadas del hallazgo (design.md D9, espejo de `@ValidFindingLocation`): alcance↔forma de
 * localización, FDI real, superficie aplicable a la pieza, rango con al menos dos piezas sin
 * repetir, grado/subtipo/estado exigidos o prohibidos según el **tipo elegido del catálogo** — el
 * catálogo es la fuente, este schema no hardcodea qué tipo exige qué (design.md D6). El servidor
 * sigue siendo la autoridad final; esto solo evita el viaje redondo con un dato claramente inválido.
 */
export function buildFindingSchema(catalog: DentalFindingCatalogEntryResponse[] | undefined) {
  return findingFormSchema.superRefine((values, ctx) => {
    if (!values.type) {
      ctx.addIssue({ code: "custom", path: ["type"], message: "Selecciona el tipo de hallazgo." });
      return;
    }
    const entry = catalogEntryFor(catalog, values.type, values.subtype);
    const candidates = catalog?.filter((e) => e.type === values.type) ?? [];
    const hasSubtypes = candidates.some((e) => e.subtype !== "");

    if (hasSubtypes && !values.subtype) {
      ctx.addIssue({ code: "custom", path: ["subtype"], message: "Este tipo exige elegir un subtipo." });
    }
    if (!hasSubtypes && values.subtype) {
      ctx.addIssue({ code: "custom", path: ["subtype"], message: "Este tipo no admite subtipo." });
    }

    if (entry?.requiresGrade) {
      if (values.grade === "" || values.grade < 1 || values.grade > 3) {
        ctx.addIssue({ code: "custom", path: ["grade"], message: "El grado (1-3) es obligatorio para este tipo." });
      }
    } else if (values.grade !== "") {
      ctx.addIssue({ code: "custom", path: ["grade"], message: "Este tipo no admite grado." });
    }

    const requiresCondition = entry?.colorBehavior === "SEGUN_ESTADO";
    if (requiresCondition && values.condition === "") {
      ctx.addIssue({ code: "custom", path: ["condition"], message: "Este tipo exige el estado de conservación." });
    } else if (!requiresCondition && values.condition !== "") {
      ctx.addIssue({ code: "custom", path: ["condition"], message: "Este tipo no admite estado de conservación." });
    }

    validateLocation(values.location, entry?.scope, ctx);
  });
}

function validateLocation(
  location: FindingFormValues["location"],
  scope: string | undefined,
  ctx: z.RefinementCtx,
) {
  if (!scope) return;
  if (location.locationKind !== scope) {
    ctx.addIssue({ code: "custom", path: ["location", "locationKind"], message: "La localización no corresponde a este tipo." });
    return;
  }
  switch (scope) {
    case "SURFACE":
      requireValidTooth(location.toothA, ["location", "toothA"], ctx);
      if (location.surface === "") {
        ctx.addIssue({ code: "custom", path: ["location", "surface"], message: "La superficie es obligatoria." });
      }
      break;
    case "TOOTH":
      requireValidTooth(location.toothA, ["location", "toothA"], ctx);
      break;
    case "BETWEEN_TEETH":
      requireValidTooth(location.toothA, ["location", "toothA"], ctx);
      requireValidTooth(location.toothB, ["location", "toothB"], ctx);
      if (location.toothA !== "" && location.toothA === location.toothB) {
        ctx.addIssue({ code: "custom", path: ["location", "toothB"], message: "Las dos piezas no pueden ser la misma." });
      }
      break;
    case "TOOTH_RANGE": {
      const teeth = location.teeth;
      if (teeth.length < 2) {
        ctx.addIssue({ code: "custom", path: ["location", "teeth"], message: "Un rango exige al menos dos piezas." });
      } else if (new Set(teeth).size !== teeth.length) {
        ctx.addIssue({ code: "custom", path: ["location", "teeth"], message: "Hay una pieza repetida en el rango." });
      } else if (teeth.some((t) => !isFdiValid(t))) {
        ctx.addIssue({ code: "custom", path: ["location", "teeth"], message: "El rango incluye una pieza inválida." });
      }
      break;
    }
    case "ARCH":
      if (location.arch === "") {
        ctx.addIssue({ code: "custom", path: ["location", "arch"], message: "El arco es obligatorio." });
      }
      break;
    default:
      break;
  }
}

function requireValidTooth(tooth: number | "", path: (string | number)[], ctx: z.RefinementCtx) {
  if (tooth === "") {
    ctx.addIssue({ code: "custom", path, message: "La pieza es obligatoria." });
  } else if (!isFdiValid(tooth)) {
    ctx.addIssue({ code: "custom", path, message: "El número FDI no corresponde a ninguna pieza válida." });
  }
}

export function toFindingRequest(values: FindingFormValues): DentalChartFindingRequest {
  return {
    type: values.type as DentalChartFindingRequest["type"],
    subtype: values.subtype === "" ? undefined : values.subtype,
    condition: values.condition === "" ? undefined : values.condition,
    grade: values.grade === "" ? undefined : values.grade,
    location: {
      locationKind: values.location.locationKind === "" ? undefined : values.location.locationKind,
      toothA: values.location.toothA === "" ? undefined : values.location.toothA,
      toothB: values.location.toothB === "" ? undefined : values.location.toothB,
      surface: values.location.surface === "" ? undefined : values.location.surface,
      arch: values.location.arch === "" ? undefined : values.location.arch,
      teeth: values.location.teeth.length > 0 ? values.location.teeth : undefined,
    },
  };
}

/**
 * El checklist mínimo de sellado del odontograma (design.md D5, `DentalChartChecklist`), en el
 * cliente solo para **guiar** — el servidor sigue siendo la autoridad. Especificaciones y
 * observaciones son dos campos distintos, ninguno cubre al otro (ADR-0017 D6).
 */
export function missingDentalChartFields(block: DentalChartResponse | undefined): string[] {
  const missing: string[] = [];
  if (!block?.specificationsReviewed) missing.push("specificationsReviewed");
  if (!block?.observationsReviewed) missing.push("observationsReviewed");
  return missing;
}

export const DENTAL_CHART_CHECKLIST_LABELS: Record<string, string> = {
  specificationsReviewed: "Especificaciones (marcar revisado)",
  observationsReviewed: "Observaciones (marcar revisado)",
};
