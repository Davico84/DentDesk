import { z } from "zod";
import type {
  BackgroundEntryRequest,
  ConsultationNoteRequest,
  ConsultationNoteResponse,
  CorrectionRequest,
} from "@/modules/core/services/generated/model";
import { BackgroundEntryRequestCategory, CorrectionRequestField } from "@/modules/core/services/generated/model";

/**
 * Nota de consulta (`update-clinical-history` design.md D3/D4) — paridad con
 * `ConsultationNoteRequest` y las reglas del servicio:
 * - la fecha y hora de la atención es obligatoria y no puede ser futura;
 * - la próxima cita es una fecha posterior al **día** de la atención.
 *
 * El contenido es opcional en el borrador: lo obligatorio (motivo, examen, tratamiento y al menos un
 * diagnóstico) se exige al **sellar** — {@link missingNoteFields}, espejo del checklist del servidor.
 *
 * La hora de atención se escribe en la hora local del navegador (`datetime-local`) y viaja como
 * instante. El **día** de la atención, contra el que se compara la próxima cita, se calcula en la
 * zona de la clínica — igual que el servidor —, no en la del navegador.
 */
const text = z.string();

/**
 * Zona horaria de la clínica. Espejo de `OrganizationSettings.DEFAULT_TIMEZONE`: hoy ninguna clínica
 * puede cambiarla (no hay endpoint) y la API no la expone. Si eso cambia, debe llegar del servidor.
 */
export const CLINIC_TIMEZONE = "America/Lima";

/** Día calendario (`YYYY-MM-DD`) de un instante en la zona de la clínica. */
export function clinicDay(instant: Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: CLINIC_TIMEZONE }).format(instant);
}

/** Margen para el reloj del equipo: una atención "de ahora" puede llegar unos segundos adelantada. */
export const CLOCK_SKEW_MS = 60_000;

export const consultationNoteSchema = z
  .object({
    attendedAt: z.string().min(1, "Indica la fecha y hora de la atención."),
    reason: text,
    illnessDuration: text,
    symptomsAndSigns: text,
    previousTreatment: text,
    biologicalFunctions: text,
    examination: text,
    treatment: text,
    auxiliaryExams: text,
    referral: text,
    nextAppointmentOn: text,
  })
  .superRefine((values, ctx) => {
    if (!values.attendedAt) return;
    const attended = new Date(values.attendedAt);
    if (Number.isNaN(attended.getTime())) {
      ctx.addIssue({ code: "custom", path: ["attendedAt"], message: "Fecha y hora inválidas." });
      return;
    }
    if (attended.getTime() > Date.now() + CLOCK_SKEW_MS) {
      ctx.addIssue({ code: "custom", path: ["attendedAt"], message: "La atención no puede ser futura." });
    }
    if (values.nextAppointmentOn && values.nextAppointmentOn <= clinicDay(attended)) {
      ctx.addIssue({
        code: "custom",
        path: ["nextAppointmentOn"],
        message: "La próxima cita debe ser posterior a la fecha de la atención.",
      });
    }
  });

export type ConsultationNoteValues = z.infer<typeof consultationNoteSchema>;

/** `2026-10-09T15:30:00Z` → `2026-10-09T10:30` (hora local, formato de `<input type="datetime-local">`). */
export function toLocalDateTimeInput(iso: string | undefined): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Hora local de `<input type="datetime-local">` → ISO-8601 en UTC, que es lo que espera la API. */
export function toIsoInstant(localDateTime: string): string {
  return new Date(localDateTime).toISOString();
}

export function noteDefaults(note: ConsultationNoteResponse | undefined): ConsultationNoteValues {
  return {
    attendedAt: toLocalDateTimeInput(note?.attendedAt),
    reason: note?.reason ?? "",
    illnessDuration: note?.illnessDuration ?? "",
    symptomsAndSigns: note?.symptomsAndSigns ?? "",
    previousTreatment: note?.previousTreatment ?? "",
    biologicalFunctions: note?.biologicalFunctions ?? "",
    examination: note?.examination ?? "",
    treatment: note?.treatment ?? "",
    auxiliaryExams: note?.auxiliaryExams ?? "",
    referral: note?.referral ?? "",
    nextAppointmentOn: note?.nextAppointmentOn ?? "",
  };
}

const orUndefined = (value: string) => (value.trim() ? value : undefined);

export function toNoteRequest(values: ConsultationNoteValues): ConsultationNoteRequest {
  return {
    attendedAt: toIsoInstant(values.attendedAt),
    reason: orUndefined(values.reason),
    illnessDuration: orUndefined(values.illnessDuration),
    symptomsAndSigns: orUndefined(values.symptomsAndSigns),
    previousTreatment: orUndefined(values.previousTreatment),
    biologicalFunctions: orUndefined(values.biologicalFunctions),
    examination: orUndefined(values.examination),
    treatment: orUndefined(values.treatment),
    auxiliaryExams: orUndefined(values.auxiliaryExams),
    referral: orUndefined(values.referral),
    nextAppointmentOn: orUndefined(values.nextAppointmentOn),
  };
}

/** Nombres que devuelve el servidor en `missingFields` al rechazar un sellado (design.md D3). */
export type NoteChecklistField = "reason" | "examination" | "treatment" | "diagnoses";

/** Espejo del checklist de sellado del servidor: lo que falta para sellar lo YA guardado. */
export function missingNoteFields(note: ConsultationNoteResponse): NoteChecklistField[] {
  const missing: NoteChecklistField[] = [];
  if (!note.reason?.trim()) missing.push("reason");
  if (!note.examination?.trim()) missing.push("examination");
  if (!note.treatment?.trim()) missing.push("treatment");
  if ((note.diagnoses?.length ?? 0) === 0) missing.push("diagnoses");
  return missing;
}

/**
 * Corrección de un error material (DS 009-2017-SA art. 47 c) — paridad con `CorrectionRequest`:
 * campo, texto corregido y motivo obligatorios. Los diagnósticos no figuran entre los campos.
 */
export const correctionSchema = z
  .object({
    field: z.union([z.enum(CorrectionRequestField), z.literal("")]),
    correctedText: z.string().trim().min(1, "Escribe el texto correcto."),
    reason: z.string().trim().min(1, "Indica el motivo de la corrección."),
  })
  .superRefine((values, ctx) => {
    if (!values.field) ctx.addIssue({ code: "custom", path: ["field"], message: "Elige el campo a corregir." });
  });

export type CorrectionValues = z.infer<typeof correctionSchema>;

export function toCorrectionRequest(values: CorrectionValues): CorrectionRequest {
  return { field: values.field as CorrectionRequestField, correctedText: values.correctedText, reason: values.reason };
}

/**
 * Alta de un antecedente nuevo — paridad con `BackgroundEntryRequest` en su forma ALTA: categoría y
 * descripción obligatorias. La BAJA no tiene formulario: se elige un vigente de la lista.
 */
export const backgroundAltaSchema = z
  .object({
    category: z.union([z.enum(BackgroundEntryRequestCategory), z.literal("")]),
    description: z.string().trim().min(1, "Describe el antecedente."),
  })
  .superRefine((values, ctx) => {
    if (!values.category) ctx.addIssue({ code: "custom", path: ["category"], message: "Elige la categoría." });
  });

export type BackgroundAltaValues = z.infer<typeof backgroundAltaSchema>;

export function toAltaRequest(values: BackgroundAltaValues): BackgroundEntryRequest {
  return { kind: "ALTA", category: values.category as BackgroundEntryRequestCategory, description: values.description };
}

export function toBajaRequest(closesEntryId: number, description?: string): BackgroundEntryRequest {
  return { kind: "BAJA", closesEntryId, description: description?.trim() ? description : undefined };
}
