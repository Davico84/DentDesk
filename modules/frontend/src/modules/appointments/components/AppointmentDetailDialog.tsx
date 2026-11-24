import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/modules/core/ui/dialog";
import type { AppointmentResponse, SchedulableDentistResponse } from "@/modules/core/services/generated/model";
import { instantToClinicLocal } from "@/modules/core/utils/clinicTime";
import { AppointmentActions } from "./AppointmentActions";
import { AppointmentStatusBadge } from "./AppointmentStatusBadge";
import { CancelAppointmentForm } from "./CancelAppointmentForm";
import { RescheduleAppointmentForm } from "./RescheduleAppointmentForm";
import { longDayLabel } from "./appointmentDisplay";

type Mode = "detail" | "reschedule" | "cancel";

/** Detalle de una cita y, según su estado y su hora, sus acciones; reprogramar y cancelar se hacen en el mismo diálogo. */
export function AppointmentDetailDialog({
  appointment,
  timeZone,
  dentists,
  onClose,
}: {
  appointment: AppointmentResponse;
  timeZone: string;
  dentists: SchedulableDentistResponse[];
  onClose: () => void;
}) {
  const [mode, setMode] = useState<Mode>("detail");
  // Las acciones dependen de la hora (desde el inicio: atendida, no asistió, atender con nota): el reloj avanza
  // mientras el detalle está abierto, así aparecen al llegar la hora sin cerrar y volver a abrir.
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 15_000);
    return () => window.clearInterval(timer);
  }, []);
  const start = instantToClinicLocal(appointment.startsAt, timeZone);
  const end = instantToClinicLocal(appointment.endsAt, timeZone);
  const title = mode === "reschedule" ? "Reprogramar cita" : mode === "cancel" ? "Cancelar cita" : "Cita";

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription className="capitalize">
            {longDayLabel(start.date)}, {start.time}–{end.time}
          </DialogDescription>
        </DialogHeader>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
          <dt className="text-muted-foreground">Paciente</dt>
          <dd>{appointment.patientName}</dd>
          <dt className="text-muted-foreground">Odontólogo</dt>
          <dd>{appointment.dentistName}</dd>
          <dt className="text-muted-foreground">Estado</dt>
          <dd>
            <AppointmentStatusBadge status={appointment.status} />
          </dd>
          {appointment.reason ? (
            <>
              <dt className="text-muted-foreground">Motivo</dt>
              <dd>{appointment.reason}</dd>
            </>
          ) : null}
          {appointment.cancellationReason ? (
            <>
              <dt className="text-muted-foreground">Motivo de la cancelación</dt>
              <dd>{appointment.cancellationReason}</dd>
            </>
          ) : null}
          {appointment.statusChangedByName ? (
            <>
              <dt className="text-muted-foreground">Registró</dt>
              <dd>{appointment.statusChangedByName}</dd>
            </>
          ) : null}
        </dl>
        {mode === "detail" ? (
          <AppointmentActions
            appointment={appointment}
            now={now}
            onReschedule={() => setMode("reschedule")}
            onCancel={() => setMode("cancel")}
            onDone={onClose}
          />
        ) : null}
        {mode === "reschedule" ? (
          <RescheduleAppointmentForm
            appointment={appointment}
            timeZone={timeZone}
            dentists={dentists}
            onDone={onClose}
            onBack={() => setMode("detail")}
          />
        ) : null}
        {mode === "cancel" ? (
          <CancelAppointmentForm appointmentId={appointment.id} onDone={onClose} onBack={() => setMode("detail")} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
