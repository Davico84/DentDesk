import { z } from "zod";
import type { DiagnosisRequest } from "@/modules/core/services/generated/model";
import { DiagnosisRequestUbicacion, DiagnosisRequestTipoDiagnostico } from "@/modules/core/services/generated/model";
import { VALID_FDI_TEETH } from "@/modules/core/utils/fdiTeeth";

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
