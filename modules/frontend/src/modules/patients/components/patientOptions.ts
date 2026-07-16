import type { SelectOption } from "@/modules/core/ui/form-field";
import {
  PatientRequestDocumentType,
  PatientRequestGuardianRelationship,
  PatientRequestSex,
} from "@/modules/core/services/generated/model";

/**
 * Etiquetas en español de los <b>conjuntos cerrados</b> del contrato. Los valores salen de los
 * enums generados (no se teclean a mano): si el backend añade uno, el typecheck lo delata aquí.
 * Viven en el módulo porque son de <b>dominio</b>; el `SelectField` de `core/ui` es agnóstico.
 */
export const documentTypeOptions: SelectOption[] = [
  { value: PatientRequestDocumentType.DNI, label: "DNI" },
  { value: PatientRequestDocumentType.CE, label: "Carné de extranjería" },
  { value: PatientRequestDocumentType.PASSPORT, label: "Pasaporte" },
];

export const sexOptions: SelectOption[] = [
  { value: PatientRequestSex.MALE, label: "Masculino" },
  { value: PatientRequestSex.FEMALE, label: "Femenino" },
  { value: PatientRequestSex.OTHER, label: "Otro" },
];

export const guardianRelationshipOptions: SelectOption[] = [
  { value: PatientRequestGuardianRelationship.MOTHER, label: "Madre" },
  { value: PatientRequestGuardianRelationship.FATHER, label: "Padre" },
  { value: PatientRequestGuardianRelationship.GUARDIAN, label: "Tutor" },
  { value: PatientRequestGuardianRelationship.OTHER, label: "Otro" },
];

/** Etiqueta legible de un valor de enum (para la vista de detalle). */
export function labelOf(options: SelectOption[], value?: string): string {
  return options.find((o) => o.value === value)?.label ?? "";
}
