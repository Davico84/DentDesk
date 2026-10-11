import { Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { RED, findingColor, findingStatusLabel } from "@/modules/dental-chart/utils/findingColor";
import { locationSummary, siglaOf } from "@/modules/dental-chart/utils/findingDisplay";
import { INK_2, DIVIDER, FONT_MONO } from "@/modules/dental-chart/utils/chartPalette";

/**
 * Hallazgos en bloques (celular, < 640 px): los cuatro datos a la vista sin columnas ocultas
 * (`update-responsive-layout` design D4). Sigla y tipo arriba; localización y estado abajo;
 * eliminar con objetivo táctil de 44 px.
 */
export function FindingCards({
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
    <ul className="divide-y text-sm">
      {findings.map((f) => {
        const color = findingColor(f, catalog);
        return (
          <li key={f.id} className="flex items-start gap-3 px-3 py-2.5" style={{ borderColor: DIVIDER }}>
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <p className="flex flex-wrap items-baseline gap-x-2">
                <span className="font-bold" style={{ color, fontFamily: FONT_MONO }}>
                  {siglaOf(f, catalog) || "—"}
                </span>
                <span className="font-medium">{findingTypeLabel(f.type ?? "")}</span>
              </p>
              <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs" style={{ color: INK_2 }}>
                <span>{locationSummary(f)}</span>
                <span className="inline-flex items-center gap-1.5">
                  <span aria-hidden="true" className="inline-block size-2.5 rounded-full" style={{ background: color }} />
                  {findingStatusLabel(f, catalog)}
                </span>
              </p>
            </div>
            {onRemove ? (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="-my-1 size-11 shrink-0 hover:bg-black/5"
                aria-label={`Eliminar hallazgo ${findingTypeLabel(f.type ?? "")}`}
                disabled={onRemove.pending}
                onClick={() => f.id != null && onRemove.run(f.id)}
              >
                <Trash2 className="size-4" style={{ color: RED }} />
              </Button>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
