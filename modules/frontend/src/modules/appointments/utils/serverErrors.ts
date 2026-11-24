import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { getFieldErrors, getUserFriendlyError } from "@/modules/core/utils/apiError";

/** Campos del contrato → campos del formulario de la agenda (la hora de inicio se elige en `time`). */
const FIELD_MAP: Record<string, string> = {
  startsAt: "time",
  durationMinutes: "durationMinutes",
  dentistUserId: "dentistUserId",
  patientId: "patientId",
  reason: "reason",
};

/**
 * Reparte un error del servidor: los `400` de validación van junto a su campo; el resto (choque, fuera de horario,
 * odontólogo no habilitado, cita cerrada) se devuelve para mostrarlo arriba del pie del formulario.
 */
export function applyAgendaServerError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly string[],
): string | null {
  const fieldErrors = getFieldErrors(error).filter((e) => fields.includes(FIELD_MAP[e.field ?? ""] ?? ""));
  if (fieldErrors.length > 0) {
    fieldErrors.forEach((e) =>
      setError(FIELD_MAP[e.field ?? ""] as Path<T>, { type: "server", message: e.message ?? "Valor inválido" }),
    );
    return null;
  }
  return getUserFriendlyError(error);
}
