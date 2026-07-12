import { deriveTheme, type BrandColors } from "./deriveTheme";
import { ThemeManager } from "./ThemeManager";

/**
 * Cache local de la marca por organización (design.md D8), para pintar el tema al instante
 * en el arranque y evitar el splash con el tema por defecto. Indexada por organización, con
 * la última activa, para no filtrar la marca entre tenants en estaciones compartidas.
 *
 * Límite honesto: en un arranque en frío no se conoce la organización real hasta que
 * responde `/auth/refresh` (token en memoria + refresh httpOnly), así que la pintura es
 * OPTIMISTA (última organización activa) y se corrige tras el refresh.
 */
const KEY = "odr-branding";

export interface CachedBranding extends BrandColors {
  logoUrl?: string | null;
}

interface CacheShape {
  activeOrgId: number | null;
  branding: Record<string, CachedBranding>;
}

function read(): CacheShape {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) ?? "") as CacheShape;
    if (parsed && typeof parsed === "object" && parsed.branding) return parsed;
  } catch {
    /* cache ausente o corrupta */
  }
  return { activeOrgId: null, branding: {} };
}

/** Guarda la marca de una organización y la marca como activa. */
export function cacheBranding(organizationId: number, branding: CachedBranding): void {
  const cache = read();
  cache.activeOrgId = organizationId;
  cache.branding[String(organizationId)] = branding;
  localStorage.setItem(KEY, JSON.stringify(cache));
}

export function clearBrandingCache(): void {
  localStorage.removeItem(KEY);
}

/** Marca de la última organización activa cacheada, si la hay. */
export function cachedActiveBranding(): { organizationId: number; branding: CachedBranding } | null {
  const cache = read();
  if (cache.activeOrgId == null) return null;
  const branding = cache.branding[String(cache.activeOrgId)];
  return branding ? { organizationId: cache.activeOrgId, branding } : null;
}

/**
 * Pintura inmediata en el arranque desde la cache (llamar antes de montar React). Aplica el
 * tema de la última organización activa; se corregirá cuando `useBranding` reciba la real.
 */
export function applyCachedThemeOnBoot(): void {
  const cached = cachedActiveBranding();
  if (!cached) return;
  const theme = deriveTheme({
    brandPrimary: cached.branding.brandPrimary,
    brandSecondary: cached.branding.brandSecondary,
  });
  if (theme) ThemeManager.apply(theme);
}
