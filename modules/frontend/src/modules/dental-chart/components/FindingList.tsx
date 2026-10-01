import { Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { useRemoveFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { RED, findingColor, findingStatusLabel } from "@/modules/dental-chart/utils/findingColor";
import { PAPER, PAPER_ALT, INK, INK_2, DIVIDER, FONT_BODY, FONT_MONO } from "@/modules/dental-chart/utils/chartPalette";

/** Lista de hallazgos ya registrados en el odontograma abierto, con opción de eliminar.
 *  Formato acordado con el user (artefacto de diseño): tabla sigla/hallazgo/localización/estado
 *  — no solo el tipo y la sigla, para que se pueda auditar el odontograma sin leer el gráfico. */
export function FindingList({
  patientId,
  findings,
  catalog,
  editable,
}: {
  patientId: number;
  findings: DentalChartFindingResponse[];
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  editable: boolean;
}) {
  const removeFinding = useRemoveFinding(patientId);

  if (findings.length === 0) {
    return <p className="text-sm text-muted-foreground">Sin hallazgos registrados todavía.</p>;
  }

  // Mismo "papel" que el odontograma (chartPalette.ts): es parte del documento legal, así que no
  // cambia con el modo oscuro. Por eso el texto y los separadores también son fijos, no tokens.
  return (
    <div className="overflow-x-auto rounded-lg border" style={{ background: PAPER, color: INK, fontFamily: FONT_BODY }}>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-xs uppercase tracking-wide" style={{ background: PAPER_ALT, color: INK_2, borderColor: DIVIDER }}>
            <th className="px-3 py-1 font-medium">Sigla</th>
            <th className="px-3 py-1 font-medium">Hallazgo</th>
            <th className="px-3 py-1 font-medium">Localización</th>
            <th className="px-3 py-1 font-medium">Estado</th>
            {editable ? <th className="px-3 py-1" /> : null}
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
                {editable ? (
                  <td className="px-3 py-0.5 text-right">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="size-7 hover:bg-black/5"
                      aria-label={`Eliminar hallazgo ${findingTypeLabel(f.type ?? "")}`}
                      disabled={removeFinding.isPending}
                      onClick={() => f.id != null && removeFinding.mutate(f.id)}
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
    </div>
  );
}

function siglaOf(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): string {
  const entry = catalog?.find((e) => e.type === f.type && (e.subtype ?? "") === (f.subtype ?? ""));
  return entry?.acronym ?? "";
}

function locationSummary(f: DentalChartFindingResponse): string {
  const l = f.location;
  if (!l) return "";
  switch (l.locationKind) {
    case "SURFACE":
      return `Pieza ${l.toothA}, superficie ${l.surface}`;
    case "TOOTH":
      return `Pieza ${l.toothA}`;
    case "BETWEEN_TEETH":
      return `Entre las piezas ${l.toothA} y ${l.toothB}`;
    case "TOOTH_RANGE":
      return `Rango: piezas ${(l.teeth ?? []).join(", ")}`;
    case "ARCH":
      return `Arco ${l.arch?.toLowerCase()}`;
    default:
      return "";
  }
}
