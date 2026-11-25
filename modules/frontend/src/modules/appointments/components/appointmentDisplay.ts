import type { AppointmentResponseStatus } from "@/modules/core/services/generated/model";

/** Etiquetas visibles de los estados de una cita. */
export const STATUS_LABEL: Record<AppointmentResponseStatus, string> = {
  AGENDADA: "Agendada",
  ATENDIDA: "Atendida",
  NO_ASISTIO: "No asistió",
  CANCELADA: "Cancelada",
};

const DAY_FORMAT = new Intl.DateTimeFormat("es-PE", { weekday: "short", day: "numeric", timeZone: "UTC" });
const LONG_DAY_FORMAT = new Intl.DateTimeFormat("es-PE", {
  weekday: "long",
  day: "numeric",
  month: "long",
  timeZone: "UTC",
});
const MONTH_FORMAT = new Intl.DateTimeFormat("es-PE", { day: "numeric", month: "short", timeZone: "UTC" });

/** "lun 2" para la cabecera de una columna. La fecha es civil (`YYYY-MM-DD`): se formatea como UTC. */
export function shortDayLabel(date: string): string {
  return DAY_FORMAT.format(new Date(`${date}T00:00:00Z`));
}

/** "martes, 3 de diciembre". */
export function longDayLabel(date: string): string {
  return LONG_DAY_FORMAT.format(new Date(`${date}T00:00:00Z`));
}

/** "2 dic – 8 dic" para el rango de la semana. */
export function weekRangeLabel(first: string, last: string): string {
  return `${MONTH_FORMAT.format(new Date(`${first}T00:00:00Z`))} – ${MONTH_FORMAT.format(new Date(`${last}T00:00:00Z`))}`;
}
