import { addDays, mondayOf } from "@/modules/core/utils/clinicTime";
import { freeRanges, openRanges, placeAppointments, timeOf } from "../utils/agendaLayout";
import { useBusinessHours, useWeekAppointments } from "./useAppointmentsApi";

export interface DayAvailability {
  /** La fecha es válida y se puede evaluar. */
  ready: boolean;
  /** La clínica no atiende ese día. */
  closed: boolean;
  /** Tramos libres del odontólogo (o del horario, sin odontólogo elegido), en `HH:mm`. */
  free: Array<{ from: string; to: string }>;
  /** Límites para el campo de hora: la apertura más temprana y el último inicio que cabe antes del cierre. */
  min?: string;
  max?: string;
}

/**
 * Disponibilidad de un día para elegir la hora de una cita: el horario de atención de ese día menos las citas
 * vigentes del odontólogo elegido (sin contar `excludeAppointmentId`, la que se reprograma). Solo informa: el servidor
 * decide igual.
 */
export function useDayAvailability(
  date: string,
  dentistUserId: string,
  timeZone: string,
  excludeAppointmentId?: number,
): DayAvailability {
  const ready = /^\d{4}-\d{2}-\d{2}$/.test(date);
  const hours = useBusinessHours();
  const monday = ready ? mondayOf(date) : "";
  const week = useWeekAppointments(monday, monday ? addDays(monday, 6) : "", dentistUserId ? Number(dentistUserId) : undefined, {
    enabled: ready && Boolean(dentistUserId),
  });
  if (!ready || !hours.data) {
    return { ready: false, closed: false, free: [] };
  }
  const ranges = openRanges(hours.data, date);
  if (ranges.length === 0) {
    return { ready: true, closed: true, free: [] };
  }
  const busy = dentistUserId
    ? placeAppointments(
        (week.data ?? []).filter(
          (a) => (a.status === "AGENDADA" || a.status === "ATENDIDA") && a.id !== excludeAppointmentId,
        ),
        timeZone,
      )
        .filter((p) => p.date === date)
        .map((p): [number, number] => [p.startMinute, p.endMinute])
    : [];
  return {
    ready: true,
    closed: false,
    free: freeRanges(ranges, busy).map(([from, to]) => ({ from: timeOf(from), to: timeOf(to) })),
    min: timeOf(Math.min(...ranges.map(([opens]) => opens))),
    max: timeOf(Math.max(...ranges.map(([, closes]) => closes)) - 10),
  };
}
