import { create } from "zustand";
import { createSelectors } from "./createSelectors";

/**
 * Estado de la marca de la organización activa que consume la UI (el logo y el aviso de
 * contraste del editor). Los colores no viven aquí: se aplican como variables CSS vía
 * {@link import("@/modules/core/theme/ThemeManager").ThemeManager}.
 */
interface BrandingState {
  logoUrl: string | null;
  contrastWarning: boolean;
  setBranding: (logoUrl: string | null, contrastWarning: boolean) => void;
  clear: () => void;
}

const useBrandingStoreBase = create<BrandingState>((set) => ({
  logoUrl: null,
  contrastWarning: false,
  setBranding: (logoUrl, contrastWarning) => set({ logoUrl, contrastWarning }),
  clear: () => set({ logoUrl: null, contrastWarning: false }),
}));

export const useBrandingStore = createSelectors(useBrandingStoreBase);
