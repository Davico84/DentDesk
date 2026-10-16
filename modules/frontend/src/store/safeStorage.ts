import type { StateStorage } from "zustand/middleware";

/**
 * `localStorage` con caída a memoria (`add-app-shell` design.md D5): en modo privado estricto, o
 * si el navegador bloquea el almacenamiento, la preferencia vale igual durante la visita. Compartido por los stores
 * persistidos (barra lateral, guías).
 */
const memory = new Map<string, string>();
export const safeStorage: StateStorage = {
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
