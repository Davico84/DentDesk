import { useSyncExternalStore } from "react";

/**
 * ¿Coincide la media query? Reactivo a cambios de tamaño. Sin `matchMedia` (jsdom, SSR) devuelve
 * `fallback`. Hook genérico de `core`: no conoce ningún dominio.
 */
export function useMediaQuery(query: string, fallback = false): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (typeof window === "undefined" || !window.matchMedia) return () => {};
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => (typeof window !== "undefined" && window.matchMedia ? window.matchMedia(query).matches : fallback),
    () => fallback,
  );
}

/** Escritorio según `docs/frontend.md` §4.3 (≥ 1024 px): la barra se puede contraer. */
export const DESKTOP_QUERY = "(min-width: 1024px)";
