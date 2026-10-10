import { describe, it, expect } from "vitest";
import { BRAND_PRESETS, findPreset } from "./presets";
import { deriveTheme } from "@/modules/core/theme/deriveTheme";

describe("BRAND_PRESETS", () => {
  it.each(BRAND_PRESETS)(
    "el preset $key no tiene contraste insuficiente (garantía del catálogo, no del editor)",
    (preset) => {
      const theme = deriveTheme({ brandPrimary: preset.brandPrimary, brandSecondary: preset.brandSecondary });
      expect(theme).not.toBeNull();
      expect(theme?.contrastWarning).toBe(false);
    },
  );

  it("findPreset devuelve undefined para una clave vacía o inexistente (sin personalización)", () => {
    expect(findPreset("")).toBeUndefined();
    expect(findPreset(undefined)).toBeUndefined();
    expect(findPreset("NOT_A_PRESET")).toBeUndefined();
  });

  it("findPreset resuelve una clave real del catálogo", () => {
    expect(findPreset("TEAL")?.label).toBe("Teal");
  });
});
