import type { SelectOption } from "@/modules/core/ui/form-field";
import { DiagnosisRequestUbicacion, DiagnosisRequestTipoDiagnostico } from "@/modules/core/services/generated/model";

export const ubicacionOptions: SelectOption[] = [
  { value: DiagnosisRequestUbicacion.GENERAL, label: "General" },
  { value: DiagnosisRequestUbicacion.PIEZA, label: "Pieza específica" },
];

/** Norma MINSA HIS (design.md D11): P / D / R. */
export const tipoDiagnosticoOptions: SelectOption[] = [
  { value: DiagnosisRequestTipoDiagnostico.PRESUNTIVO, label: "Presuntivo" },
  { value: DiagnosisRequestTipoDiagnostico.DEFINITIVO, label: "Definitivo" },
  { value: DiagnosisRequestTipoDiagnostico.REPETIDO, label: "Repetido" },
];

export function labelOf(options: SelectOption[], value?: string): string {
  return options.find((o) => o.value === value)?.label ?? "";
}
