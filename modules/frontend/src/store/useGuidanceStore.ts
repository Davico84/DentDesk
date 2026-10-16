import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { createSelectors } from "./createSelectors";
import { safeStorage } from "./safeStorage";

/** Guías que se abren solas una sola vez. Hoy, la del odontograma (`update-dental-chart-guidance`). */
export type GuideId = "dental-chart";

interface GuidanceState {
  /** Guía → ids de usuario que ya la vieron (terminada o cerrada). Por usuario: dos personas comparten equipo. */
  seen: Partial<Record<GuideId, number[]>>;
  hasSeen: (guide: GuideId, userId: number | undefined) => boolean;
  markSeen: (guide: GuideId, userId: number | undefined) => void;
}

/**
 * Qué guías ya vio cada usuario, en este navegador (design D4): una comodidad, no un dato del servidor. Si el
 * almacenamiento no está disponible, `safeStorage` cae a memoria y la guía vale durante la visita.
 */
const useGuidanceStoreBase = create<GuidanceState>()(
  persist(
    (set, get) => ({
      seen: {},
      hasSeen: (guide, userId) => userId != null && (get().seen[guide] ?? []).includes(userId),
      markSeen: (guide, userId) => {
        if (userId == null || get().hasSeen(guide, userId)) return;
        set((state) => ({ seen: { ...state.seen, [guide]: [...(state.seen[guide] ?? []), userId] } }));
      },
    }),
    {
      name: "odr-guidance",
      storage: createJSONStorage(() => safeStorage),
      partialize: (state) => ({ seen: state.seen }),
    },
  ),
);

export const useGuidanceStore = createSelectors(useGuidanceStoreBase);
