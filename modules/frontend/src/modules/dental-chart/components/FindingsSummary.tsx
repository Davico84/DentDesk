import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { RED, BLUE, findingColor } from "@/modules/dental-chart/utils/findingColor";

/** Resumen del odontograma: total + desglose por hacer/realizado, mismo color normativo que el
 *  gráfico y la lista — del artefacto de diseño (Main.dc.html, bloque del header). */
export function FindingsSummary({
  findings,
  catalog,
}: {
  findings: DentalChartFindingResponse[];
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
}) {
  const total = findings.length;
  const nTodo = findings.filter((f) => findingColor(f, catalog) === RED).length;
  const nOk = total - nTodo;

  return (
    <div className="flex items-end gap-6">
      <div className="flex flex-col items-end">
        <span className="font-mono text-3xl font-bold leading-none">{total}</span>
        <span className="text-xs text-muted-foreground">{total === 1 ? "hallazgo" : "hallazgos"}</span>
      </div>
      <div className="flex flex-col gap-1 text-sm">
        <span className="flex items-center gap-2">
          <span aria-hidden="true" className="inline-block size-3" style={{ background: RED }} />
          <span className="min-w-4 font-mono font-bold">{nTodo}</span>
          Por hacer / patológico
        </span>
        <span className="flex items-center gap-2">
          <span aria-hidden="true" className="inline-block size-3" style={{ background: BLUE }} />
          <span className="min-w-4 font-mono font-bold">{nOk}</span>
          Realizado / buen estado
        </span>
      </div>
    </div>
  );
}
