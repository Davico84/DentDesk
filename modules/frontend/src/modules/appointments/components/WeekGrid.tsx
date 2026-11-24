import type { AppointmentResponse, BusinessHoursResponse } from "@/modules/core/services/generated/model";
import { cn } from "@/modules/core/utils/cn";
import {
  dentistColor,
  isOpenAt,
  openRanges,
  ROW_HEIGHT,
  ROW_MINUTES,
  timeOf,
  type PlacedAppointment,
} from "../utils/agendaLayout";
import { AppointmentBlock } from "./AppointmentBlock";
import { longDayLabel, shortDayLabel } from "./appointmentDisplay";

/**
 * La grilla de la agenda (design.md D9): una columna por día, filas de 15 minutos en hora de la clínica, horas fuera
 * del horario sombreadas. Un hueco libre dentro del horario se puede elegir para agendar; una cita, para ver su
 * detalle. Con un solo día (celular) es la misma grilla con una columna.
 */
export function WeekGrid({
  dates,
  today,
  hours,
  bounds,
  placed,
  dentistIds,
  onSlot,
  onAppointment,
}: {
  dates: string[];
  today: string;
  hours: BusinessHoursResponse | undefined;
  bounds: { start: number; end: number };
  placed: PlacedAppointment[];
  dentistIds: number[];
  onSlot: (date: string, time: string) => void;
  onAppointment: (appointment: AppointmentResponse) => void;
}) {
  const rows = Array.from({ length: (bounds.end - bounds.start) / ROW_MINUTES }, (_, i) => bounds.start + i * ROW_MINUTES);
  const columns = `3.5rem repeat(${dates.length}, minmax(0, 1fr))`;

  return (
    <div className="overflow-x-auto rounded-2xl border border-border bg-card">
      <div className={cn(dates.length > 1 && "min-w-176")}>
        <div className="grid border-b border-border" style={{ gridTemplateColumns: columns }}>
          <span aria-hidden="true" />
          {dates.map((date) => (
            <div
              key={date}
              className={cn(
                "px-2 py-2 text-center text-xs font-medium capitalize text-muted-foreground",
                date === today && "text-primary",
              )}
            >
              <span aria-hidden="true">{shortDayLabel(date)}</span>
              <span className="sr-only">{longDayLabel(date)}</span>
            </div>
          ))}
        </div>
        <div className="grid" style={{ gridTemplateColumns: columns }}>
          <div aria-hidden="true">
            {rows.map((minute) => (
              <div key={minute} className="pr-2 text-right text-xs leading-none text-muted-foreground" style={{ height: ROW_HEIGHT }}>
                {minute % 60 === 0 ? timeOf(minute) : ""}
              </div>
            ))}
          </div>
          {dates.map((date) => {
            const ranges = openRanges(hours, date);
            const dayLabel = longDayLabel(date);
            return (
              <div
                key={date}
                role="group"
                aria-label={dayLabel}
                className="relative border-l border-border"
                data-testid={`day-${date}`}
              >
                {rows.map((minute) => {
                  const open = isOpenAt(ranges, minute);
                  const rowClass = cn(
                    "block w-full border-b",
                    minute % 60 === 45 ? "border-border" : "border-border/40",
                  );
                  return open ? (
                    <button
                      key={minute}
                      type="button"
                      aria-label={`Agendar el ${dayLabel} a las ${timeOf(minute)}`}
                      onClick={() => onSlot(date, timeOf(minute))}
                      className={cn(rowClass, "hover:bg-accent/60 focus-visible:bg-accent focus-visible:outline-hidden")}
                      style={{ height: ROW_HEIGHT }}
                    />
                  ) : (
                    <div
                      key={minute}
                      className={cn(rowClass, "bg-muted/70")}
                      style={{ height: ROW_HEIGHT }}
                      data-closed="true"
                    />
                  );
                })}
                {placed
                  .filter((p) => p.date === date)
                  .map((p) => (
                    <AppointmentBlock
                      key={p.appointment.id}
                      placed={p}
                      gridStart={bounds.start}
                      colorClass={dentistColor(dentistIds, p.appointment.dentistUserId)}
                      onSelect={() => onAppointment(p.appointment)}
                    />
                  ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
