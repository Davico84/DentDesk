import type { StoreApi, UseBoundStore } from "zustand";

type WithSelectors<S> = S extends { getState: () => infer T }
  ? S & { use: { [K in keyof T]: () => T[K] } }
  : never;

/**
 * Envuelve un store de Zustand con selectores atómicos `store.use.<campo>()`.
 * Consumir por selector explícito o por este helper evita suscribirse al
 * store completo (docs/frontend.md §3.3): un cambio en cualquier campo ya
 * no re-renderiza a quien solo lee otro.
 */
export function createSelectors<S extends UseBoundStore<StoreApi<object>>>(_store: S) {
  const store = _store as WithSelectors<S>;
  const state = store.getState();
  const use: Record<string, () => unknown> = {};

  Object.keys(state).forEach((key) => {
    use[key] = () => _store((s) => (s as Record<string, unknown>)[key]);
  });

  store.use = use as WithSelectors<S>["use"];
  return store;
}
