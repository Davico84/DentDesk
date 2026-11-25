import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { TextareaField } from "@/modules/core/ui/form-field";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";
import { useCancelAppointment } from "../hooks/useAppointmentsApi";
import { cancelAppointmentSchema, type CancelAppointmentValues } from "../schemas/appointment";
import { AgendaFormError } from "./AgendaFormError";

/** Cancelar una cita agendada, con motivo opcional. Libera el hueco del odontólogo. */
export function CancelAppointmentForm({
  appointmentId,
  onDone,
  onBack,
}: {
  appointmentId: number;
  onDone: () => void;
  onBack: () => void;
}) {
  const cancelMutation = useCancelAppointment();
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CancelAppointmentValues>({
    resolver: zodResolver(cancelAppointmentSchema),
    mode: "onTouched",
    defaultValues: { reason: "" },
  });

  const submit = (values: CancelAppointmentValues) => {
    setServerError(null);
    cancelMutation.mutate(
      { id: appointmentId, reason: values.reason.trim() || undefined },
      { onSuccess: onDone, onError: (error) => setServerError(getUserFriendlyError(error)) },
    );
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
      <TextareaField
        label="Motivo de la cancelación"
        name="reason"
        register={register}
        error={errors.reason}
        placeholder="Opcional"
        rows={2}
      />
      <AgendaFormError message={serverError} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onBack} disabled={cancelMutation.isPending}>
          Volver
        </Button>
        <Button type="submit" variant="destructive" disabled={cancelMutation.isPending}>
          {cancelMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          Cancelar cita
        </Button>
      </div>
    </form>
  );
}
