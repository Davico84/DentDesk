import { useEffect, useId, useRef, type CSSProperties, type KeyboardEvent } from "react";
import { X } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { SpotRect, TourStep } from "./tourTypes";

const GAP = 12;
const GUTTER = 16;
const MAX_WIDTH = 360;
/** Alto estimado de la tarjeta para decidir si va debajo o encima del recorte. */
const ESTIMATED_HEIGHT = 260;

/** Debajo del recorte si cabe, si no encima; centrada en el ancla sin salirse de la pantalla. Sin recorte, al centro. */
function placement(rect: SpotRect | null): CSSProperties {
  const width = Math.min(MAX_WIDTH, window.innerWidth - GUTTER * 2);
  if (!rect) return { width, left: "50%", top: "50%", transform: "translate(-50%, -50%)" };
  const left = Math.min(Math.max(rect.left + rect.width / 2 - width / 2, GUTTER), window.innerWidth - width - GUTTER);
  const below = rect.top + rect.height + GAP;
  return below + ESTIMATED_HEIGHT <= window.innerHeight
    ? { width, left, top: below }
    : { width, left, top: Math.max(rect.top - GAP, GUTTER), transform: "translateY(-100%)" };
}

/**
 * Tarjeta de un paso: título, explicación, ejemplos y navegación. `role="dialog"` modal; el foco entra a la tarjeta
 * al cambiar de paso y Tab no sale de ella. Primitivo genérico de `core`.
 */
export function TourCard({
  step,
  index,
  total,
  rect,
  onPrevious,
  onNext,
  onClose,
}: {
  step: TourStep;
  index: number;
  total: number;
  rect: SpotRect | null;
  onPrevious: () => void;
  onNext: () => void;
  onClose: () => void;
}) {
  const titleId = useId();
  const bodyId = useId();
  const cardRef = useRef<HTMLDivElement>(null);
  const primaryRef = useRef<HTMLButtonElement>(null);
  const last = index === total - 1;

  useEffect(() => {
    primaryRef.current?.focus();
  }, [index]);

  /** Tab y Shift+Tab giran dentro de la tarjeta: lo de atrás está tapado y no debe recibir el foco. */
  const trapFocus = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== "Tab" || !cardRef.current) return;
    const focusable = [...cardRef.current.querySelectorAll<HTMLElement>("button:not([disabled])")];
    if (focusable.length === 0) return;
    const first = focusable[0];
    const lastItem = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      lastItem.focus();
    } else if (!e.shiftKey && document.activeElement === lastItem) {
      e.preventDefault();
      first.focus();
    }
  };

  return (
    <div
      ref={cardRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={bodyId}
      onKeyDown={trapFocus}
      style={placement(rect)}
      className="fixed z-[61] flex flex-col gap-3 rounded-2xl border bg-card p-4 text-card-foreground shadow-lg"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium text-muted-foreground">
            Paso {index + 1} de {total}
          </p>
          <h2 id={titleId} className="text-base font-semibold">
            {step.title}
          </h2>
        </div>
        <Button variant="ghost" size="icon" className="-mr-2 -mt-1 size-9" onClick={onClose} aria-label="Cerrar la guía">
          <X className="size-4" />
        </Button>
      </div>
      <p id={bodyId} className="text-sm text-muted-foreground">
        {step.body}
      </p>
      {step.examples?.length ? (
        <ul className="flex flex-wrap gap-1.5" aria-label="Ejemplos">
          {step.examples.map((example) => (
            <li key={example} className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium">
              {example}
            </li>
          ))}
        </ul>
      ) : null}
      <div className="flex items-center justify-between gap-2 pt-1">
        <Button variant="ghost" onClick={onPrevious} disabled={index === 0}>
          Anterior
        </Button>
        <Button ref={primaryRef} onClick={last ? onClose : onNext}>
          {last ? "Terminar" : "Siguiente"}
        </Button>
      </div>
    </div>
  );
}
