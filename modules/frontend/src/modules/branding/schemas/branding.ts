import { z } from "zod";
import { UpdateBrandingRequestBrandPreset } from "@/modules/core/services/generated/model";

const HTTPS = /^https:\/\/[^\s]+$/;

/** Campo opcional de conjunto cerrado: el valor del enum, o "" cuando no se ha elegido. */
const optionalEnum = <T extends Record<string, string>>(values: T) =>
  z.union([z.enum(values), z.literal("")]);

/**
 * Formulario de marca. El color ya no es un hex libre: es una clave del catálogo curado de
 * presets (update-organization-branding) — "" = sin personalización (default del producto). El
 * logo sigue siendo una URL `https` opcional, en paridad con el backend.
 */
export const brandingSchema = z.object({
  brandPreset: optionalEnum(UpdateBrandingRequestBrandPreset),
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
    brandPreset: values.brandPreset === "" ? undefined : values.brandPreset,
    logoUrl: clean(values.logoUrl),
  };
}
