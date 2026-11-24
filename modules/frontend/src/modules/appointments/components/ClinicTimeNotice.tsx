import { AlertTriangle, Clock } from "lucide-react";
import { clinicLocalToInstant } from "@/modules/core/utils/clinicTime";

/**
 * Debajo de fecha y hora: la hora que de verdad se agenda, en la zona de la clínica y con su desfase (design.md D3).
 * Si esa hora no existe ese día por el cambio de horario, avisa a qué hora se mueve antes de confirmar.
 */
export function ClinicTimeNotice({ date, time, timeZone }: { date: string; time: string; timeZone: string }) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) {
    return null;
  }
  const resolved = clinicLocalToInstant(date, time, timeZone);
  if (resolved.adjusted) {
    return (
      <p role="status" className="flex items-start gap-1.5 text-xs text-warning-foreground">
        <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
        Esa hora no existe ese día por el cambio de horario: se agendará a las {resolved.local.time} (UTC
        {resolved.local.offset}).
      </p>
    );
  }
  return (
    <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <Clock className="size-3.5" aria-hidden="true" />
      Hora de la clínica ({timeZone}): {resolved.local.time}, UTC{resolved.local.offset}
    </p>
  );
}
