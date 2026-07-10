import { z } from "zod";

const HEX = /^#[0-9a-fA-F]{6}$/;
const HTTPS = /^https:\/\/[^\s]+$/;

/**
 * Formulario de marca. Todos los campos son opcionales (vacío = valor por defecto del
 * producto). En paridad con la validación del backend: color hex `#RRGGBB`, logo solo `https`.
 */
export const brandingSchema = z.object({
  brandPrimary: z.union([z.string().regex(HEX, "Color hex inválido (#RRGGBB)"), z.literal("")]),
  brandSecondary: z.union([z.string().regex(HEX, "Color hex inválido (#RRGGBB)"), z.literal("")]),
  logoUrl: z.union([z.string().regex(HTTPS, "La URL del logo debe usar https"), z.literal("")]),
});

export type BrandingValues = z.infer<typeof brandingSchema>;

/**
 * Convierte el formulario al request: los vacíos se omiten (`undefined`), lo que en el JSON
 * equivale a enviar el campo nulo → el backend restaura ese valor por defecto.
 */
export function toRequest(values: BrandingValues) {
  const clean = (v: string) => (v.trim() === "" ? undefined : v.trim());
  return {
    brandPrimary: clean(values.brandPrimary),
    brandSecondary: clean(values.brandSecondary),
    logoUrl: clean(values.logoUrl),
  };
}
