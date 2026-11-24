import { Link } from "react-router-dom";
import { CalendarPlus } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { cn } from "@/modules/core/utils/cn";
import { scheduleAppointmentPath } from "@/routes/paths";

/**
 * "Agendar próxima cita" desde una nota que la propone (spec "Agendar la próxima cita propuesta en una nota"): lleva a
 * la Agenda con el formulario abierto, paciente y fecha precargados. No crea nada hasta que el usuario confirme. Sin
 * próxima cita, no se muestra.
 */
export function ScheduleNextAppointmentLink({ patientId, date }: { patientId: number; date: string | undefined }) {
  if (!date) {
    return null;
  }
  return (
    <Link to={scheduleAppointmentPath(patientId, date)} className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
      <CalendarPlus className="mr-1.5 size-4" aria-hidden="true" />
      Agendar próxima cita
    </Link>
  );
}
