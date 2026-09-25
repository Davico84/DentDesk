import { Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { useRemoveFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { findingColor, findingStatusLabel } from "@/modules/dental-chart/utils/findingColor";

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

  return (
    <div className="overflow-x-auto rounded-lg border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <th className="px-3 py-2 font-medium">Sigla</th>
            <th className="px-3 py-2 font-medium">Hallazgo</th>
            <th className="px-3 py-2 font-medium">Localización</th>
            <th className="px-3 py-2 font-medium">Estado</th>
            {editable ? <th className="px-3 py-2" /> : null}
          </tr>
        </thead>
        <tbody className="divide-y">
          {findings.map((f) => {
            const color = findingColor(f, catalog);
            return (
              <tr key={f.id}>
                <td className="px-3 py-2.5 font-mono text-sm font-bold" style={{ color }}>
                  {siglaOf(f, catalog) || "—"}
                </td>
                <td className="px-3 py-2.5 font-medium">{findingTypeLabel(f.type ?? "")}</td>
                <td className="px-3 py-2.5 text-muted-foreground">{locationSummary(f)}</td>
                <td className="px-3 py-2.5">
                  <span className="inline-flex items-center gap-1.5">
                    <span aria-hidden="true" className="inline-block size-2.5 rounded-full" style={{ background: color }} />
                    {findingStatusLabel(f, catalog)}
                  </span>
                </td>
                {editable ? (
                  <td className="px-3 py-2.5 text-right">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={`Eliminar hallazgo ${findingTypeLabel(f.type ?? "")}`}
                      disabled={removeFinding.isPending}
                      onClick={() => f.id != null && removeFinding.mutate(f.id)}
                    >
                      <Trash2 className="size-4 text-destructive" />
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
