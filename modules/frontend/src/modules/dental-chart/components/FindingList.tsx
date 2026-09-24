import { Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { useRemoveFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";

/** Lista de hallazgos ya registrados en el odontograma abierto, con opción de eliminar. */
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
    <ul className="flex flex-col divide-y">
      {findings.map((f) => (
        <li key={f.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
          <div>
            <p className="font-medium">
              {findingTypeLabel(f.type ?? "")}
              {f.subtype ? <span className="text-muted-foreground"> · {siglaOf(f, catalog)}</span> : null}
            </p>
            <p className="text-xs text-muted-foreground">{locationSummary(f)}</p>
          </div>
          {editable ? (
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
          ) : null}
        </li>
      ))}
    </ul>
  );
}

function siglaOf(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): string {
  const entry = catalog?.find((e) => e.type === f.type && (e.subtype ?? "") === (f.subtype ?? ""));
  return entry?.acronym ?? f.subtype ?? "";
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
