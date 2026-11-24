import { useState } from "react";
import { Label } from "@/modules/core/ui/label";
import { Button } from "@/modules/core/ui/button";
import { addDays, instantToClinicLocal, mondayOf } from "@/modules/core/utils/clinicTime";
import { useBusinessHours, useWeekAppointments } from "../hooks/useAppointmentsApi";
import { minutesOf, openRanges, placeAppointments, startOptions } from "../utils/agendaLayout";
import { SlotButton } from "./SlotButton";

const SLOT_STEP = 15;
const GRID_STEP = 5;
const DAY_PARTS = [
  { label: "Mañana", from: 0, to: 12 * 60 },
  { label: "Tarde", from: 12 * 60, to: 18 * 60 },
  { label: "Noche", from: 18 * 60, to: 24 * 60 },
] as const;

/**
 * La hora de inicio de una cita, elegida entre las posibles ese día (add-appointments, revisión de la prueba manual):
 * cada 15 minutos dentro del horario de atención y donde la duración entera cabe en el tramo. Las que se pisan con otra
 * cita vigente del odontólogo elegido, o que ya pasaron, se ven deshabilitadas. "Otra hora…" abre los minutos de 5 en 5.
 * El servidor decide igual: esto solo evita pedir lo que se sabe imposible.
 */
export function TimeSlotPicker({
  date,
  durationMinutes,
  dentistUserId,
  excludeAppointmentId,
  timeZone,
  value,
  onChange,
  error,
}: {
  date: string;
  durationMinutes: string;
  dentistUserId: string;
  excludeAppointmentId?: number;
  timeZone: string;
  value: string;
  onChange: (time: string) => void;
  error?: string;
}) {
  const validDate = /^\d{4}-\d{2}-\d{2}$/.test(date);
  const duration = Number(durationMinutes) || 30;
  const hours = useBusinessHours();
  const monday = validDate ? mondayOf(date) : "";
  const week = useWeekAppointments(monday, monday ? addDays(monday, 6) : "", dentistUserId ? Number(dentistUserId) : undefined, {
    enabled: validDate && Boolean(dentistUserId),
  });

  const ranges = validDate ? openRanges(hours.data, date) : [];
  const busy = placeAppointments(
    (week.data ?? []).filter(
      (a) => (a.status === "AGENDADA" || a.status === "ATENDIDA") && a.id !== excludeAppointmentId,
    ),
    timeZone,
  )
    .filter((p) => p.date === date)
    .map((p): [number, number] => [p.startMinute, p.endMinute]);
  const now = instantToClinicLocal(new Date(), timeZone);
  const nowMinute = !validDate || date > now.date ? null : date < now.date ? 24 * 60 : minutesOf(now.time);

  const slots = startOptions(ranges, duration, SLOT_STEP, busy, nowMinute);
  const fine = startOptions(ranges, duration, GRID_STEP, busy, nowMinute);
  const [showFine, setShowFine] = useState(() => Boolean(value) && !slots.some((s) => s.time === value));
  const errorId = "time-error";

  return (
    <fieldset className="flex flex-col gap-2" aria-describedby={error ? errorId : undefined}>
      <legend className="mb-1 text-sm font-medium">
        Hora de inicio <span className="text-destructive">*</span>
      </legend>
      {!validDate ? <p className="text-sm text-muted-foreground">Elige primero la fecha.</p> : null}
      {validDate && ranges.length === 0 ? (
        <p className="text-sm text-muted-foreground">La clínica no atiende ese día.</p>
      ) : null}
      {validDate && ranges.length > 0 && slots.length === 0 ? (
        <p className="text-sm text-muted-foreground">Con esa duración no entra ninguna cita en el horario de ese día.</p>
      ) : null}
      {DAY_PARTS.map((part) => {
        const partSlots = slots.filter((s) => minutesOf(s.time) >= part.from && minutesOf(s.time) < part.to);
        if (partSlots.length === 0) {
          return null;
        }
        return (
          <div key={part.label} className="flex flex-col gap-1.5">
            <span className="text-xs text-muted-foreground">{part.label}</span>
            <div className="grid grid-cols-4 gap-1.5 sm:grid-cols-6" role="group" aria-label={part.label}>
              {partSlots.map((slot) => (
                <SlotButton key={slot.time} slot={slot} selected={slot.time === value} onSelect={onChange} />
              ))}
            </div>
          </div>
        );
      })}
      {validDate && fine.length > 0 ? (
        showFine ? (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="time-fine">Otra hora (de 5 en 5 minutos)</Label>
            <select
              id="time-fine"
              value={value}
              onChange={(event) => onChange(event.target.value)}
              className="h-11 w-40 rounded-xl border border-input bg-card px-3 text-sm focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="">Elegir…</option>
              {fine.map((option) => (
                <option key={option.time} value={option.time} disabled={option.busy || option.past}>
                  {option.time}
                  {option.busy ? " (ocupado)" : ""}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <Button type="button" variant="link" size="sm" className="self-start px-0" onClick={() => setShowFine(true)}>
            Otra hora…
          </Button>
        )
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </fieldset>
  );
}
