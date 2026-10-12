import { create } from "zustand";
import { createSelectors } from "./createSelectors";

/**
 * Peticiones a la API que llevan más de unos segundos sin responder (`update-slow-request-notice`
 * D1). Las marca el cliente HTTP; las lee el aviso "Despertando el servidor…". Se cuentan por id:
 * con varias en vuelo, el aviso sigue hasta que responde la última lenta.
 */
interface SlowRequestState {
  slowIds: ReadonlySet<number>;
  /** Instante (ms) en que empezó la espera lenta más antigua; `null` si no hay ninguna. */
  slowSince: number | null;
  markSlow: (id: number, since: number) => void;
  done: (id: number) => void;
  reset: () => void;
}

const useSlowRequestStoreBase = create<SlowRequestState>((set) => ({
  slowIds: new Set(),
  slowSince: null,
  markSlow: (id, since) =>
    set((state) => {
      if (state.slowIds.has(id)) return state;
      const slowIds = new Set(state.slowIds).add(id);
      return { slowIds, slowSince: state.slowSince ?? since };
    }),
  done: (id) =>
    set((state) => {
      if (!state.slowIds.has(id)) return state;
      const slowIds = new Set(state.slowIds);
      slowIds.delete(id);
      return { slowIds, slowSince: slowIds.size === 0 ? null : state.slowSince };
    }),
  reset: () => set({ slowIds: new Set(), slowSince: null }),
}));

export const useSlowRequestStore = createSelectors(useSlowRequestStoreBase);
