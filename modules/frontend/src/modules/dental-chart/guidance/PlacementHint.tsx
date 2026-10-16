import { Info } from "lucide-react";
import { INK, INK_2, PAPER, SELECTION, SELECTION_SOFT } from "@/modules/dental-chart/utils/chartPalette";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { RangeModeStep } from "@/modules/dental-chart/components/RangeModeStep";
import type { Placement } from "./placement";

/** Qué tocar para cada alcance, nombrando la pieza de la que se partió cuando la hay (design D5). */
function whereToTouch({ scope, fromTooth }: Placement): string {
  switch (scope) {
    case "SURFACE":
      return fromTooth != null ? `Toca una cara de la pieza ${fromTooth}.` : "Toca la cara afectada de la pieza.";
    case "TOOTH":
      return "Toca el número de la pieza.";
    case "BETWEEN_TEETH":
      return fromTooth != null ? `Toca el espacio entre la pieza ${fromTooth} y su vecina.` : "Toca el espacio entre las dos piezas.";
    case "ARCH":
      return 'Usa el botón "Arco superior" o "Arco inferior".';
    default:
      return "";
  }
}

/**
 * Aviso visible del modo ubicar (incluido el rango): qué se ubica, qué tocar y cómo salir. Vive dentro del papel del
 * odontograma, así que usa su paleta fija (no tokens del tema, que en modo oscuro no se leerían sobre el papel), con el
 * mismo violeta que resalta la pieza ancla. El anuncio para lectores de pantalla lo hace la región `aria-live` del
 * odontograma.
 */
export function PlacementHint({ placement, onCancel }: { placement: Placement; onCancel: () => void }) {
  const isRange = placement.scope === "TOOTH_RANGE";
  const onSecondStep = placement.rangeStart != null;
  const title = placement.type
    ? `Ubicando: ${findingTypeLabel(placement.type)}`
    : isRange
      ? "Registrando un rango de piezas"
      : "Ubicando un hallazgo";
  return (
    <div
      className="mb-3 flex gap-3 rounded-lg border px-4 py-3 text-sm"
      style={{ background: SELECTION_SOFT, borderColor: SELECTION, color: INK }}
    >
      <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" style={{ color: SELECTION }} />
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <p className="font-semibold">{title}</p>
        {isRange ? (
          <>
            {placement.type ? null : (
              <p className="text-xs" style={{ color: INK_2 }}>
                Para hallazgos sobre varias piezas contiguas de la misma fila, como una prótesis parcial fija o un aparato
                ortodóntico fijo.
              </p>
            )}
            <ol className="flex flex-col gap-1">
              <RangeModeStep number={1} done={onSecondStep} active={!onSecondStep}>
                {onSecondStep ? (
                  <>
                    Primera pieza: <strong>{placement.rangeStart}</strong>
                  </>
                ) : (
                  "Haz click en la primera pieza del tramo."
                )}
              </RangeModeStep>
              <RangeModeStep number={2} done={false} active={onSecondStep}>
                Haz click en la última pieza, en la misma fila.
              </RangeModeStep>
            </ol>
          </>
        ) : (
          <p>{whereToTouch(placement)} Solo responden esos lugares.</p>
        )}
      </div>
      <button
        type="button"
        onClick={onCancel}
        className="h-8 shrink-0 self-start rounded-md border px-3 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-offset-2"
        style={{ background: PAPER, borderColor: SELECTION, color: SELECTION, outlineColor: SELECTION }}
      >
        Cancelar
      </button>
    </div>
  );
}
