import type { AppointmentResponse, BusinessHoursResponse } from "@/modules/core/services/generated/model";
import { addDays, instantToClinicLocal, isoWeekday } from "@/modules/core/utils/clinicTime";

/** Altura de una fila de 15 minutos, en píxeles. */
export const ROW_MINUTES = 15;
export const ROW_HEIGHT = 24;

const WEEK_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;
export type WeekDay = (typeof WEEK_DAYS)[number];

/** Clases de color por odontólogo (tokens `--chart-1..5`, design.md D9). Estáticas para que Tailwind las vea. */
const DENTIST_COLORS = [
  "bg-chart-1 text-chart-foreground",
  "bg-chart-2 text-chart-foreground",
  "bg-chart-3 text-chart-foreground",
  "bg-chart-4 text-chart-foreground",
  "bg-chart-5 text-chart-foreground",
] as const;
const UNKNOWN_DENTIST_COLOR = "bg-muted text-foreground";

export function minutesOf(time: string): number {
  const [h, m] = time.split(":").map(Number);
  return h * 60 + m;
}

export function timeOf(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}

/** Los siete días (lunes a domingo) de la semana que empieza en `monday`. */
export function weekDates(monday: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

export function weekDayOf(date: string): WeekDay {
  return WEEK_DAYS[isoWeekday(date) - 1];
}

/** Tramos `[apertura, cierre)` en minutos de un día. */
export function openRanges(hours: BusinessHoursResponse | undefined, date: string): Array<[number, number]> {
  const day = hours?.days?.find((d) => d.day === weekDayOf(date));
  return (day?.segments ?? []).map((s) => [minutesOf(s.opens), minutesOf(s.closes)]);
}

/** Si la fila que empieza en `minute` cae dentro del horario de atención de ese día. */
export function isOpenAt(ranges: Array<[number, number]>, minute: number): boolean {
  return ranges.some(([opens, closes]) => minute >= opens && minute < closes);
}

/** Una cita ubicada en su día: minutos civiles de la clínica y su columna si comparte hueco con otras. */
export interface PlacedAppointment {
  appointment: AppointmentResponse;
  date: string;
  startMinute: number;
  endMinute: number;
  column: number;
  columns: number;
}

/**
 * Ubica las citas en la hora de la clínica y reparte el ancho entre las que se solapan (de distintos odontólogos):
 * cada grupo de citas encadenadas por solape comparte el ancho en tantas columnas como necesita.
 */
export function placeAppointments(appointments: AppointmentResponse[], timeZone: string): PlacedAppointment[] {
  const byDate = new Map<string, PlacedAppointment[]>();
  for (const appointment of appointments) {
    const start = instantToClinicLocal(appointment.startsAt, timeZone);
    const end = instantToClinicLocal(appointment.endsAt, timeZone);
    const startMinute = minutesOf(start.time);
    // Una cita que cruza la medianoche se corta al final del día (no hay horario que la admita, pero puede existir).
    const endMinute = end.date === start.date ? minutesOf(end.time) : 24 * 60;
    const placed: PlacedAppointment = { appointment, date: start.date, startMinute, endMinute, column: 0, columns: 1 };
    byDate.set(start.date, [...(byDate.get(start.date) ?? []), placed]);
  }
  for (const day of byDate.values()) {
    day.sort((a, b) => a.startMinute - b.startMinute || a.endMinute - b.endMinute);
    let group: PlacedAppointment[] = [];
    let groupEnd = -1;
    const closeGroup = () => {
      const columns = Math.max(1, ...group.map((p) => p.column + 1));
      group.forEach((p) => (p.columns = columns));
    };
    for (const placed of day) {
      if (placed.startMinute >= groupEnd) {
        closeGroup();
        group = [];
        groupEnd = -1;
      }
      const used = new Set(group.filter((p) => p.endMinute > placed.startMinute).map((p) => p.column));
      let column = 0;
      while (used.has(column)) column++;
      placed.column = column;
      group.push(placed);
      groupEnd = Math.max(groupEnd, placed.endMinute);
    }
    closeGroup();
  }
  return [...byDate.values()].flat();
}

/**
 * Rango de filas de la grilla: de la apertura más temprana al cierre más tardío de la semana, ampliado para que
 * entren las citas que quedaron fuera del horario. Sin horario ni citas, 08:00–18:00.
 */
export function gridBounds(
  hours: BusinessHoursResponse | undefined,
  dates: string[],
  placed: PlacedAppointment[],
): { start: number; end: number } {
  const edges = dates.flatMap((date) => openRanges(hours, date)).flat();
  placed.forEach((p) => edges.push(p.startMinute, p.endMinute));
  if (edges.length === 0) {
    return { start: 8 * 60, end: 18 * 60 };
  }
  const start = Math.floor(Math.min(...edges) / 60) * 60;
  const end = Math.min(24 * 60, Math.ceil(Math.max(...edges) / 60) * 60);
  return { start, end };
}

/** Un inicio posible para una cita: su hora y si está libre para el odontólogo elegido. */
export interface StartOption {
  time: string;
  busy: boolean;
  past: boolean;
}

/**
 * Inicios posibles de una cita de `duration` minutos, cada `step` minutos, dentro de cada tramo del horario (la cita
 * entera tiene que caber en el tramo). `busy` = se pisa con una de las citas vigentes dadas (rango semiabierto, como el
 * backend); `past` = ese inicio ya pasó (solo el día de hoy).
 */
export function startOptions(
  ranges: Array<[number, number]>,
  duration: number,
  step: number,
  busyRanges: Array<[number, number]>,
  nowMinute: number | null,
): StartOption[] {
  const options: StartOption[] = [];
  for (const [opens, closes] of ranges) {
    for (let start = Math.ceil(opens / step) * step; start + duration <= closes; start += step) {
      const end = start + duration;
      options.push({
        time: timeOf(start),
        busy: busyRanges.some(([from, to]) => start < to && end > from),
        past: nowMinute !== null && start <= nowMinute,
      });
    }
  }
  return options;
}

/** Color del odontólogo por su posición en la lista de agendables; fuera de ella, neutro. */
export function dentistColor(dentistIds: number[], dentistId: number): string {
  const index = dentistIds.indexOf(dentistId);
  return index < 0 ? UNKNOWN_DENTIST_COLOR : DENTIST_COLORS[index % DENTIST_COLORS.length];
}
