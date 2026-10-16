import { useId } from "react";
import type { SpotRect } from "./tourTypes";

/**
 * Capa que oscurece la pantalla y deja un recorte sobre el ancla. **Intercepta todo el puntero**, también en el
 * recorte (que es solo visual): un click en lo resaltado no llega a lo que hay debajo (design D1). Primitivo genérico.
 */
export function TourSpotlight({ rect }: { rect: SpotRect | null }) {
  const maskId = useId();
  return (
    <div
      data-testid="tour-shield"
      className="fixed inset-0 z-[60]"
      aria-hidden="true"
      onPointerDown={(e) => e.stopPropagation()}
      onClick={(e) => e.stopPropagation()}
    >
      <svg className="pointer-events-none absolute inset-0 size-full" width="100%" height="100%">
        <defs>
          <mask id={maskId}>
            <rect width="100%" height="100%" className="fill-white" />
            {rect ? (
              <rect
                x={rect.left}
                y={rect.top}
                width={rect.width}
                height={rect.height}
                rx={10}
                className="fill-black transition-all duration-300 motion-reduce:transition-none"
              />
            ) : null}
          </mask>
        </defs>
        <rect width="100%" height="100%" className="fill-black/55" mask={`url(#${maskId})`} />
        {rect ? (
          <rect
            x={rect.left}
            y={rect.top}
            width={rect.width}
            height={rect.height}
            rx={10}
            className="fill-none stroke-primary transition-all duration-300 motion-reduce:transition-none"
            strokeWidth={3}
          />
        ) : null}
      </svg>
    </div>
  );
}
