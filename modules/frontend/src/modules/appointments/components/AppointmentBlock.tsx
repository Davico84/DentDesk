import type { PlacedAppointment } from "../utils/agendaLayout";
import { ROW_HEIGHT, ROW_MINUTES, timeOf } from "../utils/agendaLayout";
import { STATUS_LABEL } from "./appointmentDisplay";
import { cn } from "@/modules/core/utils/cn";

/**
 * Una cita en la grilla: posicionada por su inicio y duración dentro de la columna de su día, con el color de su
 * odontólogo. Si comparte hueco con otras, ocupa su parte del ancho. Cancelada o no asistió: atenuada.
 */
export function AppointmentBlock({
  placed,
  gridStart,
  colorClass,
  onSelect,
}: {
  placed: PlacedAppointment;
  gridStart: number;
  colorClass: string;
  onSelect: () => void;
}) {
  const { appointment, startMinute, endMinute, column, columns } = placed;
  const top = ((startMinute - gridStart) / ROW_MINUTES) * ROW_HEIGHT;
  const height = Math.max(((endMinute - startMinute) / ROW_MINUTES) * ROW_HEIGHT - 2, ROW_HEIGHT - 2);
  const released = appointment.status === "CANCELADA" || appointment.status === "NO_ASISTIO";
  const label = `${timeOf(startMinute)} ${appointment.patientName ?? "Paciente"}, ${appointment.dentistName ?? "odontólogo"}, ${STATUS_LABEL[appointment.status]}`;
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={label}
      className={cn(
        "absolute z-10 overflow-hidden rounded-md border border-background px-1.5 py-0.5 text-left text-xs leading-tight shadow-sm transition-[filter] hover:brightness-95 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
        colorClass,
        released && "opacity-50 line-through",
      )}
      style={{
        top,
        height,
        left: `calc(${(column / columns) * 100}% + 2px)`,
        width: `calc(${100 / columns}% - 4px)`,
      }}
    >
      <span className="block truncate font-medium">{appointment.patientName ?? "Paciente"}</span>
      <span className="block truncate opacity-80">
        {timeOf(startMinute)} · {appointment.dentistName ?? ""}
      </span>
      {appointment.status !== "AGENDADA" ? (
        <span className="block truncate opacity-80">{STATUS_LABEL[appointment.status]}</span>
      ) : null}
    </button>
  );
}
