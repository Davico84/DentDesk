import { useEffect, useState, type RefObject } from "react";

export interface HorizontalOverflow {
  /** Hay contenido oculto a la izquierda (ya se deslizó). */
  left: boolean;
  /** Hay contenido oculto a la derecha. */
  right: boolean;
}

const NONE: HorizontalOverflow = { left: false, right: false };

/**
 * ¿El contenido de un contenedor con `overflow-x-auto` sobresale a los lados? Se recalcula al
 * cambiar el tamaño (del contenedor o de su contenido) y al deslizar. Sirve para indicar que hay
 * más a los lados. Hook genérico de `core`: no conoce ningún dominio. Sin `ResizeObserver` (jsdom)
 * informa "no sobresale".
 */
export function useHorizontalOverflow(ref: RefObject<HTMLElement | null>): HorizontalOverflow {
  const [overflow, setOverflow] = useState<HorizontalOverflow>(NONE);

  useEffect(() => {
    const el = ref.current;
    if (!el || typeof ResizeObserver === "undefined") return;

    const update = () => {
      const hidden = el.scrollWidth - el.clientWidth;
      // 1 px de tolerancia: el redondeo de subpíxeles no cuenta como "hay más".
      const next = { left: el.scrollLeft > 1, right: hidden - el.scrollLeft > 1 };
      setOverflow((prev) => (prev.left === next.left && prev.right === next.right ? prev : next));
    };

    update();
    const observer = new ResizeObserver(update);
    observer.observe(el);
    if (el.firstElementChild) observer.observe(el.firstElementChild);
    el.addEventListener("scroll", update, { passive: true });
    return () => {
      observer.disconnect();
      el.removeEventListener("scroll", update);
    };
  }, [ref]);

  return overflow;
}
