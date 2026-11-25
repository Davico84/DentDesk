import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  attendAppointmentWithNote,
  cancelAppointment,
  listAppointments,
  listSchedulableDentists,
  markAppointmentAttended,
  markAppointmentNoShow,
  rescheduleAppointment,
  scheduleAppointment,
} from "@/modules/core/services/generated/appointments";
import { getBusinessHours, replaceBusinessHours } from "@/modules/core/services/generated/business-hours";
import type {
  RescheduleAppointmentRequest,
  ScheduleAppointmentRequest,
  UpdateBusinessHoursRequest,
} from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";
import { refreshCapacityIfDenied } from "@/modules/staff/hooks/useProfessionalCredential";

/** Claves de la agenda (`docs/frontend.md` §2.2): varias lecturas con parámetros, una sola raíz que invalidar. */
export const APPOINTMENT_KEYS = {
  all: ["appointments"] as const,
  week: (from: string, to: string, dentistId?: number) => [...APPOINTMENT_KEYS.all, "week", from, to, dentistId] as const,
  dentists: () => [...APPOINTMENT_KEYS.all, "dentists"] as const,
};

export const BUSINESS_HOURS_KEY = ["business-hours"] as const;

/** Citas que empiezan en los días `from..to` de la clínica (máximo 7). */
export function useWeekAppointments(from: string, to: string, dentistId?: number, { enabled = true } = {}) {
  return useQuery({
    queryKey: APPOINTMENT_KEYS.week(from, to, dentistId),
    queryFn: () => listAppointments({ from, to, dentistId }),
    enabled,
  });
}

/** Odontólogos agendables (capacidad clínica vigente hoy). Su orden fija el color de cada uno en la grilla. */
export function useSchedulableDentists() {
  return useQuery({ queryKey: APPOINTMENT_KEYS.dentists(), queryFn: listSchedulableDentists });
}

/** Horario de atención y zona horaria de la clínica: la agenda muestra y elige horas en esa zona. */
export function useBusinessHours() {
  return useQuery({ queryKey: BUSINESS_HOURS_KEY, queryFn: getBusinessHours });
}

/**
 * Mutaciones de la agenda: al terminar invalidan todas las semanas. El error lo muestra quien llama (en el formulario,
 * junto al campo o arriba del diálogo), no un toast que se pierde.
 */
function useAgendaMutation<TArgs>(mutationFn: (args: TArgs) => Promise<unknown>, success: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: APPOINTMENT_KEYS.all });
      toast.success(success);
    },
    onError: (error) => refreshCapacityIfDenied(queryClient, error),
  });
}

export function useScheduleAppointment() {
  return useAgendaMutation((data: ScheduleAppointmentRequest) => scheduleAppointment(data), "Cita agendada");
}

export function useRescheduleAppointment() {
  return useAgendaMutation(
    ({ id, data }: { id: number; data: RescheduleAppointmentRequest }) => rescheduleAppointment(id, data),
    "Cita reprogramada",
  );
}

export function useCancelAppointment() {
  return useAgendaMutation(
    ({ id, reason }: { id: number; reason?: string }) => cancelAppointment(id, { reason }),
    "Cita cancelada",
  );
}

export function useMarkAttended() {
  return useAgendaMutation((id: number) => markAppointmentAttended(id), "Cita marcada como atendida");
}

export function useMarkNoShow() {
  return useAgendaMutation((id: number) => markAppointmentNoShow(id), "Cita marcada como no asistió");
}

/** Abre la nota de consulta enlazada a la cita; quien llama navega a la nota con el resultado. */
export function useAttendWithNote() {
  return useAgendaMutation((id: number) => attendAppointmentWithNote(id), "Nota de consulta abierta");
}

export function useReplaceBusinessHours() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateBusinessHoursRequest) => replaceBusinessHours(data),
    onSuccess: (result) => {
      queryClient.setQueryData(BUSINESS_HOURS_KEY, result);
      toast.success("Horario de atención guardado");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}
