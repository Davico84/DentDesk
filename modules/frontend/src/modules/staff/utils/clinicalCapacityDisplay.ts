import type { ClinicalCapacityResponse, ClinicalCapacityResponseState } from "@/modules/core/services/generated/model";

/**
 * "26 oct 2026" a partir de una fecha calendario (`YYYY-MM-DD`). Se formatea en UTC a propósito: la fecha
 * no tiene hora, y leerla en la zona del navegador la correría un día hacia atrás en América.
 */
export function calendarDateLabel(date: string | null | undefined): string {
  if (!date) return "";
  const parsed = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return "";
  return new Intl.DateTimeFormat("es-PE", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" }).format(
    parsed,
  );
}

/** Etiqueta corta del estado, para una píldora (Personal, la propia pantalla). */
export const CAPACITY_LABEL: Record<ClinicalCapacityResponseState, string> = {
  ENABLED: "Habilitado",
  EXPIRED: "Habilitación vencida",
  NO_CREDENTIAL: "Sin credencial",
  REVOKED: "Retirada por la clínica",
  INACTIVE_MEMBERSHIP: "Cuenta inactiva",
};

/** Tono de la píldora: tokens del tema, nunca colores fijos. */
export function capacityTone(state: ClinicalCapacityResponseState | undefined): "success" | "warning" | "muted" {
  if (state === "ENABLED") return "success";
  if (state === "EXPIRED" || state === "REVOKED") return "warning";
  return "muted";
}

/** El estado con su fecha, en una línea: "Habilitado hasta 31 dic 2026", "Habilitación vencida el …". */
export function capacityDetail(capacity: ClinicalCapacityResponse | undefined): string {
  if (!capacity?.state) return "";
  if (capacity.state === "ENABLED") return `Habilitado hasta ${calendarDateLabel(capacity.licensedUntil)}`;
  if (capacity.state === "EXPIRED") return `Habilitación vencida el ${calendarDateLabel(capacity.expiredOn)}`;
  return CAPACITY_LABEL[capacity.state];
}

/**
 * Por qué no se puede firmar, dicho a quien firma (los mismos motivos que el `403` del servidor). Vacío si
 * puede firmar.
 */
export function signingBlockedReason(capacity: ClinicalCapacityResponse | undefined): string {
  switch (capacity?.state) {
    case "NO_CREDENTIAL":
      return "Para firmar actos clínicos, declara tu credencial profesional.";
    case "EXPIRED":
      return `Tu habilitación venció el ${calendarDateLabel(capacity.expiredOn)}. Renueva tu credencial para firmar.`;
    case "REVOKED":
      return "La clínica retiró tu capacidad clínica: no puedes firmar actos clínicos.";
    case "INACTIVE_MEMBERSHIP":
      return "Tu cuenta está inactiva en esta clínica: no puedes firmar actos clínicos.";
    default:
      return "";
  }
}
