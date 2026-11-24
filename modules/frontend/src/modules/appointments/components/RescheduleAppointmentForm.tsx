import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField, SelectField } from "@/modules/core/ui/form-field";
import type { AppointmentResponse, SchedulableDentistResponse } from "@/modules/core/services/generated/model";
import { clinicLocalToInstant, instantToClinicLocal } from "@/modules/core/utils/clinicTime";
import { useRescheduleAppointment } from "../hooks/useAppointmentsApi";
import {
  DURATION_OPTIONS,
  rescheduleAppointmentSchema,
  type RescheduleAppointmentValues,
} from "../schemas/appointment";
import { applyAgendaServerError } from "../utils/serverErrors";
import { AgendaFormError } from "./AgendaFormError";
import { ClinicTimeNotice } from "./ClinicTimeNotice";
import { TimeSlotPicker } from "./TimeSlotPicker";

const FIELDS = ["dentistUserId", "time", "durationMinutes"] as const;

/** Reprogramar una cita agendada: nueva fecha, hora, duración u odontólogo; la cita y su paciente no cambian. */
export function RescheduleAppointmentForm({
  appointment,
  timeZone,
  dentists,
  onDone,
  onBack,
}: {
  appointment: AppointmentResponse;
  timeZone: string;
  dentists: SchedulableDentistResponse[];
  onDone: () => void;
  onBack: () => void;
}) {
  const rescheduleMutation = useRescheduleAppointment();
  const [serverError, setServerError] = useState<string | null>(null);
  const start = instantToClinicLocal(appointment.startsAt, timeZone);
  const {
    register,
    handleSubmit,
    control,
    setValue,
    setError,
    formState: { errors },
  } = useForm<RescheduleAppointmentValues>({
    resolver: zodResolver(rescheduleAppointmentSchema),
    mode: "onTouched",
    defaultValues: {
      dentistUserId: String(appointment.dentistUserId),
      date: start.date,
      time: start.time,
      durationMinutes: String(appointment.durationMinutes),
    },
  });
  const [date, time, durationMinutes, dentistUserId] = useWatch({
    control,
    name: ["date", "time", "durationMinutes", "dentistUserId"],
  });
  const durations = DURATION_OPTIONS.includes(appointment.durationMinutes as (typeof DURATION_OPTIONS)[number])
    ? DURATION_OPTIONS
    : [...DURATION_OPTIONS, appointment.durationMinutes].sort((a, b) => a - b);

  const submit = (values: RescheduleAppointmentValues) => {
    setServerError(null);
    rescheduleMutation.mutate(
      {
        id: appointment.id,
        data: {
          dentistUserId: Number(values.dentistUserId),
          startsAt: clinicLocalToInstant(values.date, values.time, timeZone).instant.toISOString(),
          durationMinutes: Number(values.durationMinutes),
        },
      },
      {
        onSuccess: onDone,
        onError: (error) => setServerError(applyAgendaServerError(error, setError, FIELDS)),
      },
    );
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
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
        <SelectField
          label="Duración"
          name="durationMinutes"
          register={register}
          error={errors.durationMinutes}
          placeholder="Elige la duración"
          options={durations.map((m) => ({ value: String(m), label: `${m} minutos` }))}
          required
        />
      </div>
      <TimeSlotPicker
        date={date}
        durationMinutes={durationMinutes}
        dentistUserId={dentistUserId}
        excludeAppointmentId={appointment.id}
        timeZone={timeZone}
        value={time}
        onChange={(next) => setValue("time", next, { shouldValidate: true })}
        error={errors.time?.message}
      />
      <ClinicTimeNotice date={date} time={time} timeZone={timeZone} />
      <AgendaFormError message={serverError} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onBack} disabled={rescheduleMutation.isPending}>
          Volver
        </Button>
        <Button type="submit" disabled={rescheduleMutation.isPending}>
          {rescheduleMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          Reprogramar
        </Button>
      </div>
    </form>
  );
}
