import type { DayAvailability } from "../hooks/useDayAvailability";

/**
 * Una línea bajo la hora: cuándo hay lugar ese día (con el odontólogo elegido, sus citas ya descontadas). Reemplaza a
 * la grilla de horas, que ocupaba media pantalla del diálogo.
 */
export function AvailabilityHint({ availability, withDentist }: { availability: DayAvailability; withDentist: boolean }) {
  if (!availability.ready) {
    return null;
  }
  if (availability.closed) {
    return <p className="text-xs text-warning-foreground">La clínica no atiende ese día.</p>;
  }
  if (availability.free.length === 0) {
    return <p className="text-xs text-warning-foreground">No queda lugar ese día{withDentist ? " con este odontólogo" : ""}.</p>;
  }
  return (
    <p className="text-xs text-muted-foreground">
      {withDentist ? "Libre ese día: " : "Horario ese día: "}
      <span className="tabular-nums text-foreground">
        {availability.free.map((range) => `${range.from}–${range.to}`).join(" · ")}
      </span>
    </p>
  );
}
