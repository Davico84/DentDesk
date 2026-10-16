import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { createSelectors } from "./createSelectors";
import { safeStorage } from "./safeStorage";

interface SidebarState {
  /** Barra lateral contraída a solo íconos (solo aplica en escritorio, ≥ 1024 px). */
  collapsed: boolean;
  toggle: () => void;
}

/** Preferencia del usuario, igual en todas las pantallas; nunca cambia sola por ruta ni por ancho. */
const useSidebarStoreBase = create<SidebarState>()(
  persist(
    (set) => ({
      collapsed: false,
      toggle: () => set((state) => ({ collapsed: !state.collapsed })),
    }),
    {
      name: "odr-sidebar",
      storage: createJSONStorage(() => safeStorage),
      partialize: (state) => ({ collapsed: state.collapsed }),
    },
  ),
);

export const useSidebarStore = createSelectors(useSidebarStoreBase);
