import { BrandingResponseBrandPreset } from "@/modules/core/services/generated/model";

export interface BrandPresetOption {
  key: BrandingResponseBrandPreset;
  label: string;
  brandPrimary: string;
  brandSecondary: string;
}

/**
 * Catálogo curado de marcas (update-organization-branding design.md D2): el backend solo valida
 * la **clave** (enum `BrandPreset`, `CHECK` en BD); el color real y el nombre de presentación
 * viven aquí, para poder afinarlos sin migración. Cada preset está pensado para pasar contraste
 * AA como color primario (verificado en `presets.test.ts`, no a ojo) — el catálogo es la garantía
 * de legibilidad, no una advertencia reactiva ante cualquier hex.
 */
export const BRAND_PRESETS: BrandPresetOption[] = [
  { key: BrandingResponseBrandPreset.TEAL, label: "Teal", brandPrimary: "#0F9B8E", brandSecondary: "#0B7285" },
  { key: BrandingResponseBrandPreset.VIOLET, label: "Violeta", brandPrimary: "#7C3AED", brandSecondary: "#5B21B6" },
  { key: BrandingResponseBrandPreset.INDIGO, label: "Índigo", brandPrimary: "#4F46E5", brandSecondary: "#3730A3" },
  { key: BrandingResponseBrandPreset.EMERALD, label: "Esmeralda", brandPrimary: "#065F46", brandSecondary: "#064E3B" },
  { key: BrandingResponseBrandPreset.AMBER, label: "Ámbar", brandPrimary: "#B45309", brandSecondary: "#92400E" },
  { key: BrandingResponseBrandPreset.ROSE, label: "Rosa", brandPrimary: "#BE123C", brandSecondary: "#9F1239" },
  { key: BrandingResponseBrandPreset.SLATE, label: "Pizarra", brandPrimary: "#334155", brandSecondary: "#1E293B" },
];

/** Busca un preset por clave (undefined = sin personalización, defaults del producto). */
export function findPreset(key: string | null | undefined): BrandPresetOption | undefined {
  return BRAND_PRESETS.find((p) => p.key === key);
}
