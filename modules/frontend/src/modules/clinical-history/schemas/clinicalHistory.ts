import { z } from "zod";
import type { ClinicalHistoryRequest, ClinicalHistoryResponse } from "@/modules/core/services/generated/model";

/**
 * Documento inicial de la Historia Clínica, en DRAFT. Todo opcional a propósito (design.md D1/D2):
 * el guardado incremental acepta secciones vacías; el checklist mínimo solo se exige al sellar
 * (ver {@link missingChecklistFields}), nunca aquí — paridad con `ClinicalHistoryRequest` del
 * backend, que tampoco lleva `@NotBlank`.
 */
export const clinicalHistorySchema = z.object({
  motivoConsulta: z.string(),
  enfermedadActual: z.string(),
  antecedentesPersonales: z.string(),
  examenClinico: z.string(),
  pronostico: z.string(),
  // "Campo vacío ≠ revisado" (design.md D3): revisado=true + detalle vacío ⇒ "sin alergias
  // conocidas"; revisado=false ⇒ no revisado, sin importar el detalle.
  alergiasRevisado: z.boolean(),
  alergiasDetalle: z.string(),
  medicacionRevisado: z.boolean(),
  medicacionDetalle: z.string(),
  antecedentesFamiliaresRevisado: z.boolean(),
  antecedentesFamiliaresDetalle: z.string(),
});

export type ClinicalHistoryValues = z.infer<typeof clinicalHistorySchema>;

export function toDefaults(h?: ClinicalHistoryResponse): ClinicalHistoryValues {
  return {
    motivoConsulta: h?.motivoConsulta ?? "",
    enfermedadActual: h?.enfermedadActual ?? "",
    antecedentesPersonales: h?.antecedentesPersonales ?? "",
    examenClinico: h?.examenClinico ?? "",
    pronostico: h?.pronostico ?? "",
    alergiasRevisado: h?.alergiasRevisado ?? false,
    alergiasDetalle: h?.alergiasDetalle ?? "",
    medicacionRevisado: h?.medicacionRevisado ?? false,
    medicacionDetalle: h?.medicacionDetalle ?? "",
    antecedentesFamiliaresRevisado: h?.antecedentesFamiliaresRevisado ?? false,
    antecedentesFamiliaresDetalle: h?.antecedentesFamiliaresDetalle ?? "",
  };
}

export function toRequest(values: ClinicalHistoryValues): ClinicalHistoryRequest {
  const clean = (v: string) => (v.trim() === "" ? undefined : v.trim());
  return {
    motivoConsulta: clean(values.motivoConsulta),
    enfermedadActual: clean(values.enfermedadActual),
    antecedentesPersonales: clean(values.antecedentesPersonales),
    examenClinico: clean(values.examenClinico),
    pronostico: clean(values.pronostico),
    alergiasRevisado: values.alergiasRevisado,
    alergiasDetalle: clean(values.alergiasDetalle),
    medicacionRevisado: values.medicacionRevisado,
    medicacionDetalle: clean(values.medicacionDetalle),
    antecedentesFamiliaresRevisado: values.antecedentesFamiliaresRevisado,
    antecedentesFamiliaresDetalle: clean(values.antecedentesFamiliaresDetalle),
  };
}

/**
 * Los mismos nombres que devuelve el backend en `missingFields` (design.md D4,
 * `ClinicalHistoryChecklist`) — se recalcula en el cliente solo para **guiar** al odontólogo
 * (deshabilitar "Completar" y mostrar qué falta); el servidor sigue siendo la autoridad que de
 * verdad bloquea el sellado. `hasInitialDentalChart` gana con `add-dental-chart` (design.md D4/D5
 * de ese change): la Historia ya no se sella sola, necesita un odontograma inicial creado.
 */
export function missingChecklistFields(
  values: ClinicalHistoryValues,
  diagnosisCount: number,
  hasInitialDentalChart: boolean,
): string[] {
  const missing: string[] = [];
  if (!values.motivoConsulta.trim()) missing.push("motivoConsulta");
  if (!values.antecedentesPersonales.trim()) missing.push("antecedentesPersonales");
  if (!values.alergiasRevisado) missing.push("alergiasRevisado");
  if (!values.medicacionRevisado) missing.push("medicacionRevisado");
  if (!values.antecedentesFamiliaresRevisado) missing.push("antecedentesFamiliaresRevisado");
  if (!values.examenClinico.trim()) missing.push("examenClinico");
  if (diagnosisCount < 1) missing.push("diagnosticos");
  if (!values.pronostico.trim()) missing.push("pronostico");
  if (!hasInitialDentalChart) missing.push("dentalChart");
  return missing;
}

/** Etiqueta legible de cada campo del checklist, para mostrarlo en la UI. */
export const CHECKLIST_LABELS: Record<string, string> = {
  motivoConsulta: "Motivo de consulta",
  antecedentesPersonales: "Antecedentes personales",
  alergiasRevisado: "Alergias (marcar revisado)",
  medicacionRevisado: "Medicación actual (marcar revisado)",
  antecedentesFamiliaresRevisado: "Antecedentes familiares (marcar revisado)",
  examenClinico: "Examen clínico",
  diagnosticos: "Al menos un diagnóstico",
  pronostico: "Pronóstico",
  dentalChart: "Odontograma inicial",
};
