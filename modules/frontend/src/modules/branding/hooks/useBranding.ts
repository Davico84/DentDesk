import { useEffect } from "react";
import { getBranding } from "@/modules/core/services/generated/organization-branding";
import { deriveTheme } from "@/modules/core/theme/deriveTheme";
import { ThemeManager } from "@/modules/core/theme/ThemeManager";
import {
  cacheBranding,
  cachedActiveBranding,
  clearBrandingCache,
} from "@/modules/core/theme/brandingCache";
import { useSessionStore } from "@/store/useSessionStore";
import { useBrandingStore } from "@/store/useBrandingStore";

/**
 * Aplica la marca de la **organización activa** al app shell (design.md D5). Reacciona al
 * `organizationId` de la sesión (no al login): cuando cambia —login o, a futuro, cambio de
 * organización— re-tematiza; al cerrar sesión (sin organización) resetea al tema del producto.
 * La pintura inmediata desde cache la hace `applyCachedThemeOnBoot` en el arranque.
 */
export function useBranding(): void {
  const organizationId = useSessionStore((s) => s.user?.organizationId ?? null);
  const setBranding = useBrandingStore((s) => s.setBranding);
  const clear = useBrandingStore((s) => s.clear);

  useEffect(() => {
    if (organizationId == null) {
      ThemeManager.reset();
      clearBrandingCache();
      clear();
      return;
    }

    // Anti-leak: si la cache es de otra organización, resetea antes de aplicar la nueva.
    const cached = cachedActiveBranding();
    if (cached && cached.organizationId !== organizationId) {
      ThemeManager.reset();
    }

    let cancelled = false;
    getBranding()
      .then((branding) => {
        if (cancelled) return;
        const theme = deriveTheme({
          brandPrimary: branding.brandPrimary,
          brandSecondary: branding.brandSecondary,
        });
        if (theme) ThemeManager.apply(theme);
        else ThemeManager.reset();
        setBranding(branding.logoUrl ?? null, theme?.contrastWarning ?? false);
        cacheBranding(organizationId, {
          brandPrimary: branding.brandPrimary,
          brandSecondary: branding.brandSecondary,
          logoUrl: branding.logoUrl,
        });
      })
      .catch(() => {
        /* Si el fetch falla, se conserva lo que haya pintado la cache; la UI no se rompe. */
      });

    return () => {
      cancelled = true;
    };
  }, [organizationId, setBranding, clear]);
}
