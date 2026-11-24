import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { CalendarClock, CheckCircle2, FileText, Loader2, UserX, XCircle } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { AppointmentResponse } from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";
import { consultationNotesPath } from "@/routes/paths";
import { useMyClinicalCapacity } from "@/modules/staff/hooks/useProfessionalCredential";
import { useAttendWithNote, useMarkAttended, useMarkNoShow } from "../hooks/useAppointmentsApi";
import { AgendaFormError } from "./AgendaFormError";

/**
 * Solo las acciones que el estado y la hora de la cita permiten (spec "Detalle de una cita"):
 * - `AGENDADA`: reprogramar y cancelar; desde su hora de inicio, además marcar atendida, no asistió y atender con nota.
 * - `ATENDIDA` sin nota: atender con nota; con nota: ir a ella.
 * "Atender con nota" se ofrece solo con capacidad clínica vigente (sin ella queda "marcar atendida"); el servidor decide
 * de todos modos.
 */
export function AppointmentActions({
  appointment,
  now,
  onReschedule,
  onCancel,
  onDone,
}: {
  appointment: AppointmentResponse;
  now: Date;
  onReschedule: () => void;
  onCancel: () => void;
  onDone: () => void;
}) {
  const navigate = useNavigate();
  const { capacity } = useMyClinicalCapacity();
  const attended = useMarkAttended();
  const noShow = useMarkNoShow();
  const attendWithNote = useAttendWithNote();
  const [error, setError] = useState<string | null>(null);

  const scheduled = appointment.status === "AGENDADA";
  const started = Date.parse(appointment.startsAt) <= now.getTime();
  const canAttendWithNote =
    capacity?.state === "ENABLED" &&
    !appointment.consultationNoteId &&
    ((scheduled && started) || appointment.status === "ATENDIDA");
  const pending = attended.isPending || noShow.isPending || attendWithNote.isPending;
  const notesPath = consultationNotesPath(appointment.patientId);
  const onError = (e: unknown) => setError(getUserFriendlyError(e));

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        {canAttendWithNote ? (
          <Button
            type="button"
            disabled={pending}
            onClick={() => attendWithNote.mutate(appointment.id, { onSuccess: () => navigate(notesPath), onError })}
          >
            {attendWithNote.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : <FileText className="mr-2 size-4" />}
            Atender con nota
          </Button>
        ) : null}
        {appointment.consultationNoteId ? (
          <Button type="button" variant="outline" onClick={() => navigate(notesPath)}>
            <FileText className="mr-2 size-4" />
            Ir a la nota
          </Button>
        ) : null}
        {scheduled && started ? (
          <>
            <Button type="button" variant="outline" disabled={pending} onClick={() => attended.mutate(appointment.id, { onSuccess: onDone, onError })}>
              <CheckCircle2 className="mr-2 size-4" />
              Marcar atendida
            </Button>
            <Button type="button" variant="outline" disabled={pending} onClick={() => noShow.mutate(appointment.id, { onSuccess: onDone, onError })}>
              <UserX className="mr-2 size-4" />
              No asistió
            </Button>
          </>
        ) : null}
        {scheduled ? (
          <>
            <Button type="button" variant="outline" disabled={pending} onClick={onReschedule}>
              <CalendarClock className="mr-2 size-4" />
              Reprogramar
            </Button>
            <Button type="button" variant="ghost" disabled={pending} onClick={onCancel} className="text-destructive">
              <XCircle className="mr-2 size-4" />
              Cancelar cita
            </Button>
          </>
        ) : null}
      </div>
      <AgendaFormError message={error} />
    </div>
  );
}
