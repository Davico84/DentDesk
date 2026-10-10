import { useEffect, type RefObject } from "react";

/**
 * Cierra un panel flotante (dropdown, popover) al hacer click afuera o presionar Escape.
 * Primitivo genérico de `core`: no conoce ningún dominio.
 */
export function useDismissOnOutsideOrEscape(ref: RefObject<HTMLElement | null>, active: boolean, onDismiss: () => void): void {
  useEffect(() => {
    if (!active) return;

    function handlePointerDown(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onDismiss();
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onDismiss();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [ref, active, onDismiss]);
}
