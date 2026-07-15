import { z } from "zod";
import type { PatientRequest, PatientResponse } from "@/modules/core/services/generated/model";

/** Mayoría de edad, en paridad con el backend (`common/Ages`, design.md D2). */
const ADULT_AGE = 18;

/** Minoría derivada de la fecha de nacimiento (`YYYY-MM-DD`). Cadena vacía o inválida → false. */
export function isMinor(birthDate: string): boolean {
  if (!birthDate) return false;
  const born = new Date(birthDate);
  if (Number.isNaN(born.getTime())) return false;
  const now = new Date();
  let age = now.getFullYear() - born.getFullYear();
  const m = now.getMonth() - born.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < born.getDate())) age--;
  return age < ADULT_AGE;
}

/**
 * Formulario de la ficha del paciente. Solo `fullName` y `birthDate` son obligatorios (design.md
 * OQ1). La **validación condicional del menor** (design.md D2, `docs/coding-style.md §7`) exige el
 * responsable cuando la fecha indica menor de edad, en paridad con `@GuardianRequiredForMinor` del
 * servidor. El servidor sigue siendo la autoridad: si el cliente se despista, su `400` trae el
 * detalle por campo y se pinta junto al input.
 */
export const patientSchema = z
  .object({
    fullName: z.string().trim().min(1, "El nombre es obligatorio").max(200),
    document: z.string().trim().max(50),
    birthDate: z.string().min(1, "La fecha de nacimiento es obligatoria"),
    sex: z.string().trim().max(20),
    phone: z.string().trim().max(40),
    email: z.union([z.string().email("Correo inválido"), z.literal("")]),
    address: z.string().trim().max(500),
    guardianName: z.string().trim().max(200),
    guardianRelationship: z.string().trim().max(50),
    guardianDocument: z.string().trim().max(50),
    guardianPhone: z.string().trim().max(40),
    emergencyContactName: z.string().trim().max(200),
    emergencyContactPhone: z.string().trim().max(40),
    emergencyContactRelationship: z.string().trim().max(50),
    // Anamnesis: en el formulario se editan como texto separado por comas; se convierten a lista.
    allergies: z.string(),
    conditions: z.string(),
    currentMedication: z.string(),
  })
  .superRefine((values, ctx) => {
    if (!isMinor(values.birthDate)) return;
    const required: Array<keyof PatientValues> = ["guardianName", "guardianRelationship", "guardianPhone"];
    for (const field of required) {
      if (!values[field] || values[field].trim() === "") {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: [field],
          message: "Obligatorio para un paciente menor de edad.",
        });
      }
    }
  });

export type PatientValues = z.infer<typeof patientSchema>;

/** Lista separada por comas → array de valores limpios (sin vacíos ni duplicados de espacio). */
function toList(text: string): string[] {
  return text
    .split(",")
    .map((v) => v.trim())
    .filter((v) => v.length > 0);
}

function joinList(values: string[] | undefined): string {
  return (values ?? []).join(", ");
}

/** Convierte el formulario al request del contrato. Los vacíos se omiten (`undefined`). */
export function toRequest(values: PatientValues): PatientRequest {
  const clean = (v: string) => (v.trim() === "" ? undefined : v.trim());
  return {
    fullName: values.fullName.trim(),
    document: clean(values.document),
    birthDate: values.birthDate,
    sex: clean(values.sex),
    phone: clean(values.phone),
    email: clean(values.email),
    address: clean(values.address),
    guardianName: clean(values.guardianName),
    guardianRelationship: clean(values.guardianRelationship),
    guardianDocument: clean(values.guardianDocument),
    guardianPhone: clean(values.guardianPhone),
    emergencyContactName: clean(values.emergencyContactName),
    emergencyContactPhone: clean(values.emergencyContactPhone),
    emergencyContactRelationship: clean(values.emergencyContactRelationship),
    allergies: toList(values.allergies),
    conditions: toList(values.conditions),
    currentMedication: toList(values.currentMedication),
  };
}

/** Valores por defecto del formulario a partir de una ficha existente (edición). */
export function toDefaults(patient?: PatientResponse): PatientValues {
  return {
    fullName: patient?.fullName ?? "",
    document: patient?.document ?? "",
    birthDate: patient?.birthDate ?? "",
    sex: patient?.sex ?? "",
    phone: patient?.phone ?? "",
    email: patient?.email ?? "",
    address: patient?.address ?? "",
    guardianName: patient?.guardianName ?? "",
    guardianRelationship: patient?.guardianRelationship ?? "",
    guardianDocument: patient?.guardianDocument ?? "",
    guardianPhone: patient?.guardianPhone ?? "",
    emergencyContactName: patient?.emergencyContactName ?? "",
    emergencyContactPhone: patient?.emergencyContactPhone ?? "",
    emergencyContactRelationship: patient?.emergencyContactRelationship ?? "",
    allergies: joinList(patient?.allergies),
    conditions: joinList(patient?.conditions),
    currentMedication: joinList(patient?.currentMedication),
  };
}
