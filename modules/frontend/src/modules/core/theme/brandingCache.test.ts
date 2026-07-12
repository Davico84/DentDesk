import { describe, it, expect, beforeEach, afterEach } from "vitest";
import {
  cacheBranding,
  cachedActiveBranding,
  clearBrandingCache,
  applyCachedThemeOnBoot,
} from "./brandingCache";
import { ThemeManager } from "./ThemeManager";

beforeEach(() => localStorage.clear());
afterEach(() => {
  ThemeManager.reset();
  localStorage.clear();
});

describe("brandingCache", () => {
  it("guarda y lee la marca de la organización activa", () => {
    cacheBranding(5, { brandPrimary: "#7f1aa6", logoUrl: "https://x/logo.png" });
    const cached = cachedActiveBranding();
    expect(cached?.organizationId).toBe(5);
    expect(cached?.branding.brandPrimary).toBe("#7f1aa6");
    expect(cached?.branding.logoUrl).toBe("https://x/logo.png");
  });

  it("clear elimina la cache", () => {
    cacheBranding(5, { brandPrimary: "#7f1aa6" });
    clearBrandingCache();
    expect(cachedActiveBranding()).toBeNull();
  });

  it("applyCachedThemeOnBoot pinta el tema desde la cache", () => {
    cacheBranding(5, { brandPrimary: "#7f1aa6" });
    applyCachedThemeOnBoot();
    expect(document.getElementById("odr-org-theme")).not.toBeNull();
  });

  it("applyCachedThemeOnBoot no hace nada sin cache", () => {
    applyCachedThemeOnBoot();
    expect(document.getElementById("odr-org-theme")).toBeNull();
  });

  it("guardar otra organización cambia la activa", () => {
    cacheBranding(5, { brandPrimary: "#111111" });
    cacheBranding(9, { brandPrimary: "#222222" });
    expect(cachedActiveBranding()?.organizationId).toBe(9);
    expect(cachedActiveBranding()?.branding.brandPrimary).toBe("#222222");
  });
});
