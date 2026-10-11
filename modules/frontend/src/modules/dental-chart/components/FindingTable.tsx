import { Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { RED, findingColor, findingStatusLabel } from "@/modules/dental-chart/utils/findingColor";
import { locationSummary, siglaOf } from "@/modules/dental-chart/utils/findingDisplay";
import { PAPER_ALT, INK_2, DIVIDER, FONT_MONO } from "@/modules/dental-chart/utils/chartPalette";

/** Hallazgos en tabla (desde 640 px): sigla, hallazgo, localización, estado y eliminar. */
export function FindingTable({
  findings,
  catalog,
  onRemove,
}: {
  findings: DentalChartFindingResponse[];
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  /** Sin `onRemove` la lista es de solo lectura (odontograma sellado). */
  onRemove?: { run: (id: number) => void; pending: boolean };
}) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b text-left text-xs uppercase tracking-wide" style={{ background: PAPER_ALT, color: INK_2, borderColor: DIVIDER }}>
          <th className="px-3 py-1 font-medium">Sigla</th>
          <th className="px-3 py-1 font-medium">Hallazgo</th>
          <th className="px-3 py-1 font-medium">Localización</th>
          <th className="px-3 py-1 font-medium">Estado</th>
          {onRemove ? <th className="px-3 py-1" /> : null}
        </tr>
      </thead>
      <tbody className="divide-y">
        {findings.map((f) => {
          const color = findingColor(f, catalog);
          return (
            <tr key={f.id} style={{ borderColor: DIVIDER }}>
              <td className="px-3 py-1 text-sm font-bold" style={{ color, fontFamily: FONT_MONO }}>
                {siglaOf(f, catalog) || "—"}
              </td>
              <td className="px-3 py-1 font-medium">{findingTypeLabel(f.type ?? "")}</td>
              <td className="px-3 py-1" style={{ color: INK_2 }}>{locationSummary(f)}</td>
              <td className="px-3 py-1">
                <span className="inline-flex items-center gap-1.5">
                  <span aria-hidden="true" className="inline-block size-2.5 rounded-full" style={{ background: color }} />
                  {findingStatusLabel(f, catalog)}
                </span>
              </td>
              {onRemove ? (
                <td className="px-3 py-0.5 text-right">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="size-7 hover:bg-black/5"
                    aria-label={`Eliminar hallazgo ${findingTypeLabel(f.type ?? "")}`}
                    disabled={onRemove.pending}
                    onClick={() => f.id != null && onRemove.run(f.id)}
                  >
                    <Trash2 className="size-3.5" style={{ color: RED }} />
                  </Button>
                </td>
              ) : null}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
