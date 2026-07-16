import type { PatientResponse } from "@/modules/core/services/generated/model";
import { documentTypeOptions, labelOf } from "./patientOptions";

/** Utilidades de presentación de pacientes (compartidas entre listado, ficha y detalle). */

/** "DNI 12345678" — el tipo va con el número, que es como se lee de verdad. */
export function documentLabel(patient: PatientResponse): string {
  if (!patient.document) return "";
  const type = labelOf(documentTypeOptions, patient.documentType);
  return type ? `${type} ${patient.document}` : patient.document;
}

/** Lista legible ("penicilina, látex"), o vacío para que la fila muestre su "—". */
export function joinOrDash(values: string[] | undefined): string {
  return values && values.length > 0 ? values.join(", ") : "";
}

/** Iniciales (una o dos) a partir del nombre, para el avatar. */
export function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** Edad legible ("34 años", "8 meses", "recién nacido") a partir de `YYYY-MM-DD`. */
export function ageLabel(birthDate: string): string {
  if (!birthDate) return "";
  const born = new Date(birthDate);
  if (Number.isNaN(born.getTime())) return "";
  const now = new Date();
  let years = now.getFullYear() - born.getFullYear();
  const m = now.getMonth() - born.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < born.getDate())) years--;
  if (years >= 1) return `${years} ${years === 1 ? "año" : "años"}`;

  let months = now.getMonth() - born.getMonth() + (now.getDate() < born.getDate() ? -1 : 0);
  if (months < 0) months += 12;
  if (months >= 1) return `${months} ${months === 1 ? "mes" : "meses"}`;
  return "recién nacido";
}
