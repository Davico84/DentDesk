import type { AppointmentResponseStatus } from "@/modules/core/services/generated/model";
import { cn } from "@/modules/core/utils/cn";
import { STATUS_LABEL } from "./appointmentDisplay";

const TONE: Record<AppointmentResponseStatus, string> = {
  AGENDADA: "bg-primary/10 text-primary",
  ATENDIDA: "bg-success/15 text-success-foreground",
  NO_ASISTIO: "bg-warning/20 text-warning-foreground",
  CANCELADA: "bg-muted text-muted-foreground",
};

/** Estado de una cita. */
export function AppointmentStatusBadge({ status }: { status: AppointmentResponseStatus }) {
  return (
    <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium", TONE[status])}>
      {STATUS_LABEL[status]}
    </span>
  );
}
