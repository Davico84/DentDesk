import { useRef, type KeyboardEvent } from "react";
import { Button } from "@/modules/core/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/modules/core/ui/sheet";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import type { ToothSurfaceCode } from "@/modules/core/utils/fdiTeeth";
import { findingColor } from "@/modules/dental-chart/utils/findingColor";
import { describeFinding } from "@/modules/dental-chart/utils/findingDisplay";
import { crownSlots, slotBox, SURFACE_LABEL } from "@/modules/dental-chart/utils/toothGeometry";
import { INK, OUTLINE, PAPER } from "@/modules/dental-chart/utils/chartPalette";
import type { ClickedLocation } from "./FindingDialog";

/** Lado de la pieza ampliada: con `minCentralRatio` 0,24 toda cara mide ≥ 44 px (update-dental-chart-touch D3). */
const SIZE = 240;
const MIN_CENTRAL_RATIO = 0.24;

/** Qué puede elegirse según el modo ubicar (design D4); fuera del modo, todo. */
export type MagnifierAllowed = { surface: boolean; tooth: boolean; between: boolean };

/**
 * La pieza ampliada para elegir dónde registrar con el dedo (spec dental-chart, <i>Elegir la localización en pantallas
 * táctiles</i>): sus cinco caras, la pieza completa y los espacios con las vecinas de la fila, cada uno ≥ 44 px, y lo
 * ya registrado. Elegir llama a `onSelect` con la misma localización que el click directo; cerrar no registra nada.
 * Es una lupa: la franja incisal es más ancha que en el dibujo para que se pueda tocar, la cara sigue siendo la misma.
 */
export function ToothMagnifier({
  fdi,
  neighbors,
  findings,
  catalog,
  allowed,
  onSelect,
  onClose,
}: {
  fdi: number;
  /** Las piezas vecinas en la misma fila (una sola en los extremos). */
  neighbors: number[];
  findings: DentalChartFindingResponse[];
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  allowed: MagnifierAllowed;
  onSelect: (location: ClickedLocation) => void;
  onClose: () => void;
}) {
  const titleRef = useRef<HTMLHeadingElement>(null);
  const { slots } = crownSlots(fdi, SIZE, { minCentralRatio: MIN_CENTRAL_RATIO });

  const onTooth = findings.filter((f) => {
    const l = f.location;
    if (!l) return false;
    if (l.locationKind === "TOOTH" || l.locationKind === "SURFACE") return l.toothA === fdi;
    if (l.locationKind === "BETWEEN_TEETH") return l.toothA === fdi || l.toothB === fdi;
    if (l.locationKind === "TOOTH_RANGE") return (l.teeth ?? []).includes(fdi);
    return false;
  });
  const onSurface = (surface: ToothSurfaceCode) =>
    onTooth.filter((f) => f.location?.locationKind === "SURFACE" && f.location.surface === surface);

  const choose = (location: ClickedLocation) => {
    onSelect(location);
    onClose();
  };
  const onFaceKey = (e: KeyboardEvent<SVGPolygonElement>, surface: ToothSurfaceCode) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      choose({ locationKind: "SURFACE", toothA: fdi, surface });
    }
  };

  return (
    <Sheet open onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        side="bottom"
        className="mx-auto flex max-h-[85vh] max-w-md flex-col gap-4 overflow-y-auto rounded-t-2xl pb-[max(1.5rem,env(safe-area-inset-bottom))]"
        onOpenAutoFocus={(e) => {
          // El foco va al título y no al ✕: el lector anuncia de qué pieza se trata antes que "Cerrar".
          e.preventDefault();
          titleRef.current?.focus();
        }}
      >
        <SheetHeader>
          <SheetTitle ref={titleRef} tabIndex={-1} className="outline-none">
            Pieza {fdi}
          </SheetTitle>
          <SheetDescription>Elige dónde registrar el hallazgo.</SheetDescription>
        </SheetHeader>

        <div className="mx-auto w-fit max-w-full rounded-xl p-2" style={{ background: PAPER }}>
          <svg
            role="group"
            aria-label={`Caras de la pieza ${fdi}`}
            viewBox={`0 0 ${SIZE} ${SIZE}`}
            width={SIZE}
            height={SIZE}
            className="block max-w-full"
          >
            {slots.map((slot) => {
              const list = onSurface(slot.surface);
              const enabled = allowed.surface;
              const box = slotBox(slot.points);
              return (
                <g key={slot.surface} opacity={enabled ? undefined : 0.3}>
                  <polygon
                    role="button"
                    data-testid={`magnifier-face-${slot.surface}`}
                    tabIndex={enabled ? 0 : -1}
                    aria-disabled={enabled ? undefined : true}
                    aria-label={`Cara ${SURFACE_LABEL[slot.surface]} de la ${fdi}${list.length > 0 ? `: ${list.length} hallazgo(s)` : ", sin hallazgos"}`}
                    points={slot.points}
                    fill={list.length > 0 ? findingColor(list[list.length - 1], catalog) : PAPER}
                    fillOpacity={list.length > 0 ? 0.35 : 1}
                    stroke={OUTLINE}
                    strokeWidth={1.5}
                    className={enabled ? "cursor-pointer focus:outline-hidden focus-visible:stroke-[3]" : undefined}
                    onClick={enabled ? () => choose({ locationKind: "SURFACE", toothA: fdi, surface: slot.surface }) : undefined}
                    onKeyDown={enabled ? (e) => onFaceKey(e, slot.surface) : undefined}
                  />
                  <text
                    x={box.x + box.width / 2}
                    y={box.y + box.height / 2 + 4}
                    textAnchor="middle"
                    fontSize={12}
                    fill={INK}
                    pointerEvents="none"
                  >
                    {SURFACE_LABEL[slot.surface]}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>

        <div className="flex flex-col gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-11"
            disabled={!allowed.tooth}
            onClick={() => choose({ locationKind: "TOOTH", toothA: fdi })}
          >
            Toda la pieza {fdi}
          </Button>
          {neighbors.map((n) => (
            <Button
              key={n}
              type="button"
              variant="outline"
              className="h-11"
              disabled={!allowed.between}
              onClick={() => choose({ locationKind: "BETWEEN_TEETH", toothA: fdi, toothB: n })}
            >
              Entre la {fdi} y la {n}
            </Button>
          ))}
        </div>

        {onTooth.length > 0 ? (
          <div>
            <h3 className="mb-1 text-xs font-medium text-muted-foreground">Ya registrado</h3>
            <ul className="flex flex-col gap-1 text-sm" aria-label="Hallazgos ya registrados en la pieza">
              {onTooth.map((f) => (
                <li key={f.id}>{describeFinding(f, catalog)}</li>
              ))}
            </ul>
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
