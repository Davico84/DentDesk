import { z } from "zod";
import { clinicToday } from "@/modules/core/utils/clinicDate";

/**
 * Paridad con `ProfessionalCredentialRequest` del backend (`docs/coding-style.md` §7): COP `@NotBlank`
 * `@Pattern(\d{1,10})`, fecha `@NotNull` y declaración jurada `@AssertTrue`. "No anterior a hoy" lo decide
 * el servidor en la hora de Perú; acá se compara con el mismo día (`clinicToday`, America/Lima), así que
 * coinciden aunque el navegador esté en otra zona.
 */
export const professionalCredentialSchema = z.object({
  copNumber: z
    .string()
    .trim()
    .min(1, "El número de colegiatura es obligatorio")
    .regex(/^\d{1,10}$/, "Solo dígitos, hasta 10"),
  licensedUntil: z
    .string()
    .min(1, "La fecha de habilitación es obligatoria")
    .refine((date) => date >= clinicToday(), "La fecha debe ser hoy o posterior"),
  sworn: z.boolean().refine((accepted) => accepted, "Debes aceptar la declaración jurada"),
});

export type ProfessionalCredentialValues = z.infer<typeof professionalCredentialSchema>;
