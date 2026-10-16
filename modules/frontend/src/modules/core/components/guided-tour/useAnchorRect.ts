import { useLayoutEffect, useState } from "react";
import type { SpotRect } from "./tourTypes";

const PADDING = 6;

type Measured = { anchor: () => Element | null; rect: SpotRect };

/**
 * Rectángulo del ancla del paso, recalculado al cambiar de paso, al redimensionar y al desplazar. El `scroll` se
 * escucha **en fase de captura**: el del contenedor del odontograma (que se desliza dentro de su tarjeta en celular)
 * no burbujea hasta `window` (update-dental-chart-guidance design D1). Antes de medir, el ancla se trae a la vista.
 *
 * <p>La medición se guarda junto con el ancla a la que pertenece y se descarta al leer si el paso cambió: así el
 * efecto solo escribe desde el callback de `requestAnimationFrame`, nunca de forma síncrona.
 */
export function useAnchorRect(anchor: (() => Element | null) | undefined, active: boolean): SpotRect | null {
  const [measured, setMeasured] = useState<Measured | null>(null);

  useLayoutEffect(() => {
    if (!active || !anchor) return;
    const element = anchor();
    if (!element) return;
    const reduced = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    element.scrollIntoView?.({ block: "nearest", inline: "center", behavior: reduced ? "auto" : "smooth" });

    let frame = 0;
    const measure = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const r = element.getBoundingClientRect();
        setMeasured({
          anchor,
          rect: { top: r.top - PADDING, left: r.left - PADDING, width: r.width + PADDING * 2, height: r.height + PADDING * 2 },
        });
      });
    };
    measure();
    window.addEventListener("resize", measure);
    window.addEventListener("scroll", measure, true);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", measure);
      window.removeEventListener("scroll", measure, true);
    };
  }, [anchor, active]);

  return active && anchor && measured?.anchor === anchor ? measured.rect : null;
}
