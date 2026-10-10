import { create } from "zustand";
import { createJSONStorage, persist, type StateStorage } from "zustand/middleware";
import { createSelectors } from "./createSelectors";

interface SidebarState {
  /** Barra lateral contraída a solo íconos (solo aplica en escritorio, ≥ 1024 px). */
  collapsed: boolean;
  toggle: () => void;
}

/**
 * `localStorage` con caída a memoria (`add-app-shell` design.md D5): en modo privado estricto, o
 * si el navegador bloquea el almacenamiento, la barra se contrae y expande igual durante la visita.
 */
const memory = new Map<string, string>();
const safeStorage: StateStorage = {
  getItem: (key) => {
    try {
      return localStorage.getItem(key);
    } catch {
      return memory.get(key) ?? null;
    }
  },
  setItem: (key, value) => {
    try {
      localStorage.setItem(key, value);
    } catch {
      memory.set(key, value);
    }
  },
  removeItem: (key) => {
    try {
      localStorage.removeItem(key);
    } catch {
      memory.delete(key);
    }
  },
};

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
