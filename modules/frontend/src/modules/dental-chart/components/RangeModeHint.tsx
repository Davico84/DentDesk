import { Info } from "lucide-react";
import { INK, INK_2, SELECTION, SELECTION_SOFT } from "@/modules/dental-chart/utils/chartPalette";
import { RangeModeStep } from "./RangeModeStep";

/**
 * Aviso visible del modo "Registrar rango": qué hacer y en qué paso va. Vive dentro del papel del
 * odontograma, así que usa su paleta fija (no tokens del tema), con el mismo violeta que resalta
 * la pieza ancla en el gráfico — el aviso y el resalte se leen como una sola cosa.
 * El anuncio para lectores de pantalla lo hace la región `aria-live` del odontograma.
 */
export function RangeModeHint({ rangeStart }: { rangeStart: number | null }) {
  const onSecondStep = rangeStart != null;
  return (
    <div
      className="mb-3 flex gap-3 rounded-lg border px-4 py-3 text-sm"
      style={{ background: SELECTION_SOFT, borderColor: SELECTION, color: INK }}
    >
      <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" style={{ color: SELECTION }} />
      <div className="flex flex-col gap-1.5">
        <p className="font-semibold">Registrando un rango de piezas</p>
        <p className="text-xs" style={{ color: INK_2 }}>
          Para hallazgos sobre varias piezas contiguas de la misma fila, como una prótesis parcial fija o un aparato
          ortodóntico fijo.
        </p>
        <ol className="flex flex-col gap-1">
          <RangeModeStep number={1} done={onSecondStep} active={!onSecondStep}>
            {onSecondStep ? (
              <>
                Primera pieza: <strong>{rangeStart}</strong>
              </>
            ) : (
              "Haz click en la primera pieza del tramo."
            )}
          </RangeModeStep>
          <RangeModeStep number={2} done={false} active={onSecondStep}>
            Haz click en la última pieza, en la misma fila.
          </RangeModeStep>
        </ol>
        {/* <p className="text-xs" style={{ color: INK_2 }}>
          {onSecondStep ? "Esc descarta la primera pieza. " : ""}«Cancelar rango» sale sin registrar nada.
        </p> */}
      </div>
    </div>
  );
}
