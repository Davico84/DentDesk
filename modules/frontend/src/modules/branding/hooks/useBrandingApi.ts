import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { getBranding, updateBranding } from "@/modules/core/services/generated/organization-branding";
import type { UpdateBrandingRequest } from "@/modules/core/services/generated/model";
import { deriveTheme } from "@/modules/core/theme/deriveTheme";
import { ThemeManager } from "@/modules/core/theme/ThemeManager";
import { cacheBranding } from "@/modules/core/theme/brandingCache";
import { findPreset } from "@/modules/branding/presets";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";
import { useSessionStore } from "@/store/useSessionStore";
import { useBrandingStore } from "@/store/useBrandingStore";

const BRANDING_KEY = ["branding", "me"] as const;

/** Marca actual de la organización (para precargar el formulario de ajustes). */
export function useBrandingQuery() {
  return useQuery({ queryKey: BRANDING_KEY, queryFn: () => getBranding() });
}

/** Guarda la marca y la aplica de inmediato (tema + logo + cache). */
export function useUpdateBranding() {
  const organizationId = useSessionStore((s) => s.user?.organizationId ?? null);
  const setBranding = useBrandingStore.use.setBranding();

  return useMutation({
    mutationFn: (data: UpdateBrandingRequest) => updateBranding(data),
    onSuccess: (branding) => {
      const preset = findPreset(branding.brandPreset);
      const theme = deriveTheme({
        brandPrimary: preset?.brandPrimary,
        brandSecondary: preset?.brandSecondary,
      });
      if (theme) ThemeManager.apply(theme);
      else ThemeManager.reset();
      setBranding(branding.logoUrl ?? null, theme?.contrastWarning ?? false);
      if (organizationId != null) {
        cacheBranding(organizationId, {
          brandPrimary: preset?.brandPrimary,
          brandSecondary: preset?.brandSecondary,
          logoUrl: branding.logoUrl,
        });
      }
      toast.success("Marca actualizada");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}
