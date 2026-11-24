import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/modules/core/ui/dialog";
import { FormField, SelectField, TextareaField, TimeField } from "@/modules/core/ui/form-field";
import type { SchedulableDentistResponse } from "@/modules/core/services/generated/model";
import { clinicLocalToInstant } from "@/modules/core/utils/clinicTime";
import { useScheduleAppointment } from "../hooks/useAppointmentsApi";
import {
  DURATION_OPTIONS,
  scheduleAppointmentSchema,
  type ScheduleAppointmentValues,
} from "../schemas/appointment";
import { useDayAvailability } from "../hooks/useDayAvailability";
import { applyAgendaServerError } from "../utils/serverErrors";
import { AgendaFormError } from "./AgendaFormError";
import { ClinicTimeNotice } from "./ClinicTimeNotice";
import { PatientPicker } from "./PatientPicker";
import { AvailabilityHint } from "./AvailabilityHint";

export interface ScheduleInitialValues {
  patientId?: string;
  date?: string;
  time?: string;
}

const FIELDS = ["patientId", "dentistUserId", "time", "durationMinutes", "reason"] as const;

/**
 * Agendar una cita: paciente, odontólogo agendable, fecha y hora de la clínica, duración y motivo. Se abre desde un
 * hueco libre (día y hora precargados), desde "Agendar" o desde la próxima cita de una nota (paciente y fecha).
 */
export function ScheduleAppointmentDialog({
  open,
  onOpenChange,
  timeZone,
  dentists,
  initial,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  timeZone: string;
  dentists: SchedulableDentistResponse[];
  initial: ScheduleInitialValues;
}) {
  const scheduleMutation = useScheduleAppointment();
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setValue,
    control,
    setError,
    formState: { errors },
  } = useForm<ScheduleAppointmentValues>({
    resolver: zodResolver(scheduleAppointmentSchema),
    mode: "onTouched",
    defaultValues: {
      patientId: initial.patientId ?? "",
      dentistUserId: dentists.length === 1 ? String(dentists[0].userId) : "",
      date: initial.date ?? "",
      time: initial.time ?? "",
      durationMinutes: "30",
      reason: "",
    },
  });
  const [patientId, date, time, dentistUserId] = useWatch({
    control,
    name: ["patientId", "date", "time", "dentistUserId"],
  });
  const availability = useDayAvailability(date, dentistUserId, timeZone);

  const submit = (values: ScheduleAppointmentValues) => {
    setServerError(null);
    scheduleMutation.mutate(
      {
        patientId: Number(values.patientId),
        dentistUserId: Number(values.dentistUserId),
        startsAt: clinicLocalToInstant(values.date, values.time, timeZone).instant.toISOString(),
        durationMinutes: Number(values.durationMinutes),
        reason: values.reason.trim() || undefined,
      },
      {
        onSuccess: () => onOpenChange(false),
        onError: (error) => setServerError(applyAgendaServerError(error, setError, FIELDS)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Agendar cita</DialogTitle>
            <DialogDescription>La fecha y la hora son las de la clínica.</DialogDescription>
          </DialogHeader>
          <PatientPicker
            value={patientId}
            onChange={(id) => setValue("patientId", id, { shouldValidate: true })}
            error={errors.patientId?.message}
          />
          <SelectField
            label="Odontólogo"
            name="dentistUserId"
            register={register}
            error={errors.dentistUserId}
            placeholder="Elige un odontólogo"
            options={dentists.map((d) => ({ value: String(d.userId), label: d.fullName ?? `Odontólogo ${d.userId}` }))}
            required
          />
          <div className="grid grid-cols-2 gap-3">
            <FormField label="Fecha" name="date" type="date" register={register} error={errors.date} required />
            <TimeField
              label="Hora"
              name="time"
              register={register}
              error={errors.time}
              step={300}
              min={availability.min}
              max={availability.max}
              required
            />
          </div>
          <AvailabilityHint availability={availability} withDentist={Boolean(dentistUserId)} />
          <ClinicTimeNotice date={date} time={time} timeZone={timeZone} />
          <SelectField
            label="Duración"
            name="durationMinutes"
            register={register}
            error={errors.durationMinutes}
            placeholder="Elige la duración"
            options={DURATION_OPTIONS.map((m) => ({ value: String(m), label: `${m} minutos` }))}
            required
          />
          <TextareaField label="Motivo" name="reason" register={register} error={errors.reason} rows={2} />
          <AgendaFormError message={serverError} />
          <DialogFooter className="gap-2 sm:gap-2">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)} disabled={scheduleMutation.isPending}>
              Cancelar
            </Button>
            <Button type="submit" disabled={scheduleMutation.isPending}>
              {scheduleMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
              Agendar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
