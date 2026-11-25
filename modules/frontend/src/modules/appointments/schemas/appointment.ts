import { z } from "zod";

/**
 * Paridad con `ScheduleAppointmentRequest` / `RescheduleAppointmentRequest` del backend (`docs/coding-style.md` §7):
 * paciente y odontólogo obligatorios, duración `@Min(10)` `@Max(240)` y múltiplo de 5, motivo `@Size(max = 500)`. La
 * grilla de 5 minutos de la hora de inicio el servidor la mide en la zona de la clínica; acá la hora ya es de la
 * clínica (el formulario la elige en esa zona), así que la misma regla se aplica sobre `HH:mm`.
 *
 * Que la cita sea futura, que el odontólogo esté habilitado ese día, el horario y los choques los decide el
 * servidor: dependen de datos que el formulario no tiene. Sus errores se muestran en el diálogo.
 */
const TIME_ON_GRID = /^([01]\d|2[0-3]):[0-5][05]$/;

export const DURATION_OPTIONS = [10, 15, 20, 30, 40, 45, 60, 90, 120, 180, 240] as const;

const slotFields = {
  dentistUserId: z.string().min(1, "Elige un odontólogo"),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Elige la fecha"),
  time: z
    .string()
    .min(1, "Elige la hora")
    .regex(TIME_ON_GRID, "La hora debe ser múltiplo de 5 minutos"),
  durationMinutes: z
    .string()
    .min(1, "Elige la duración")
    .refine((value) => {
      const minutes = Number(value);
      return Number.isInteger(minutes) && minutes >= 10 && minutes <= 240 && minutes % 5 === 0;
    }, "La duración debe ser múltiplo de 5 minutos, entre 10 y 240"),
};

export const scheduleAppointmentSchema = z.object({
  patientId: z.string().min(1, "Elige un paciente"),
  ...slotFields,
  reason: z.string().max(500, "El motivo admite hasta 500 caracteres"),
});

export type ScheduleAppointmentValues = z.infer<typeof scheduleAppointmentSchema>;

export const rescheduleAppointmentSchema = z.object(slotFields);

export type RescheduleAppointmentValues = z.infer<typeof rescheduleAppointmentSchema>;

export const cancelAppointmentSchema = z.object({
  reason: z.string().max(500, "El motivo admite hasta 500 caracteres"),
});

export type CancelAppointmentValues = z.infer<typeof cancelAppointmentSchema>;
