import { z } from "zod";
import type { DiagnosisRequest } from "@/modules/core/services/generated/model";
import { DiagnosisRequestUbicacion, DiagnosisRequestTipoDiagnostico } from "@/modules/core/services/generated/model";

/**
 * El conjunto real de números FDI (notación universal) — paridad con `FdiTooth.java`
 * (design.md D5): permanentes 11–18/21–28/31–38/41–48, temporales 51–55/61–65/71–75/81–85. Nunca
 * un rango numérico laxo: aceptaría piezas que no existen (19, 20, 29...).
 */
function quadrant(quadrantNumber: number, teethInQuadrant: number): number[] {
  return Array.from({ length: teethInQuadrant }, (_, i) => quadrantNumber * 10 + (i + 1));
}

export const VALID_FDI_TEETH = new Set<number>([
  ...quadrant(1, 8), ...quadrant(2, 8), ...quadrant(3, 8), ...quadrant(4, 8),
  ...quadrant(5, 5), ...quadrant(6, 5), ...quadrant(7, 5), ...quadrant(8, 5),
]);

const FDI_REQUIRED = "La pieza (número FDI) es obligatoria para esta ubicación.";
const FDI_INVALID = "El número FDI no corresponde a ninguna pieza válida.";
const FDI_NOT_ALLOWED = "Un diagnóstico general no admite número de pieza.";

/**
 * Un diagnóstico a agregar/editar — paridad con `DiagnosisRequest` + `@ValidDiagnosisLocation`
 * del backend (design.md D5/D11): `PIEZA` exige un FDI del conjunto real; `GENERAL` lo rechaza;
 * el tipo (P/D/R, norma MINSA HIS) es obligatorio.
 */
export const diagnosisSchema = z
  .object({
    cie10Code: z.string().trim().min(1, "Selecciona un código CIE10"),
    ubicacion: z.enum(DiagnosisRequestUbicacion),
    fdiNumber: z.union([z.number(), z.literal("")]),
    tipoDiagnostico: z.union([z.enum(DiagnosisRequestTipoDiagnostico), z.literal("")]),
  })
  .superRefine((values, ctx) => {
    if (!values.tipoDiagnostico) {
      ctx.addIssue({ code: "custom", path: ["tipoDiagnostico"], message: "Indica el tipo de diagnóstico." });
    }
    if (values.ubicacion === DiagnosisRequestUbicacion.PIEZA) {
      if (values.fdiNumber === "") {
        ctx.addIssue({ code: "custom", path: ["fdiNumber"], message: FDI_REQUIRED });
      } else if (!VALID_FDI_TEETH.has(values.fdiNumber)) {
        ctx.addIssue({ code: "custom", path: ["fdiNumber"], message: FDI_INVALID });
      }
    } else if (values.fdiNumber !== "") {
      ctx.addIssue({ code: "custom", path: ["fdiNumber"], message: FDI_NOT_ALLOWED });
    }
  });

export type DiagnosisValues = z.infer<typeof diagnosisSchema>;

export function diagnosisDefaults(): DiagnosisValues {
  return { cie10Code: "", ubicacion: DiagnosisRequestUbicacion.GENERAL, fdiNumber: "", tipoDiagnostico: "" };
}

export function toDiagnosisRequest(values: DiagnosisValues): DiagnosisRequest {
  return {
    cie10Code: values.cie10Code,
    ubicacion: values.ubicacion,
    fdiNumber: values.fdiNumber === "" ? undefined : values.fdiNumber,
    tipoDiagnostico: values.tipoDiagnostico as DiagnosisRequestTipoDiagnostico,
  };
}
