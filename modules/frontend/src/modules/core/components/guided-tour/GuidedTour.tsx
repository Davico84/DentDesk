import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { TourCard } from "./TourCard";
import { TourSpotlight } from "./TourSpotlight";
import type { TourCloseReason, TourStep } from "./tourTypes";
import { useAnchorRect } from "./useAnchorRect";

/**
 * Recorrido guiado sobre la pantalla real: oscurece, resalta el ancla de cada paso y explica qué hacer ahí
 * (update-dental-chart-guidance design D1). Primitivo genérico de `core`: recibe los pasos, no conoce el dominio.
 *
 * <p>Teclado: ← / → cambian de paso y Esc cierra. Al cerrar o terminar, el foco vuelve a quien lo tenía antes de
 * abrir (o a `returnFocusTo`). El paso actual se anuncia en una región `aria-live`.
 */
export function GuidedTour({
  open,
  steps,
  onClose,
  returnFocusTo,
}: {
  open: boolean;
  steps: TourStep[];
  onClose: (reason: TourCloseReason) => void;
  returnFocusTo?: () => HTMLElement | null;
}) {
  const [index, setIndex] = useState(0);
  const [prevOpen, setPrevOpen] = useState(open);
  const previousFocus = useRef<HTMLElement | null>(null);

  // Cada apertura empieza en el primer paso (setState en render, no en efecto: docs de React "adjusting state").
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) setIndex(0);
  }

  const step = steps[Math.min(index, steps.length - 1)];
  const rect = useAnchorRect(step?.anchor, open);

  // Layout effect a propósito: corre ANTES que el useEffect de la tarjeta, que mueve el foco a su botón. Con un
  // efecto normal (los del hijo corren antes que los del padre) se guardaba el botón de la propia guía.
  useLayoutEffect(() => {
    if (!open) return;
    previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    return () => {
      (returnFocusTo?.() ?? previousFocus.current)?.focus?.();
    };
  }, [open, returnFocusTo]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose("closed");
      } else if (e.key === "ArrowRight") {
        setIndex((i) => Math.min(i + 1, steps.length - 1));
      } else if (e.key === "ArrowLeft") {
        setIndex((i) => Math.max(i - 1, 0));
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose, steps.length]);

  if (!open || !step) return null;

  const last = index === steps.length - 1;
  return createPortal(
    <>
      <TourSpotlight rect={rect} />
      <TourCard
        step={step}
        index={index}
        total={steps.length}
        rect={rect}
        onPrevious={() => setIndex((i) => Math.max(i - 1, 0))}
        onNext={() => setIndex((i) => Math.min(i + 1, steps.length - 1))}
        onClose={() => onClose(last ? "done" : "closed")}
      />
      <p className="sr-only" aria-live="polite">
        {`Paso ${index + 1} de ${steps.length}. ${step.title}. ${step.body}`}
      </p>
    </>,
    document.body,
  );
}
