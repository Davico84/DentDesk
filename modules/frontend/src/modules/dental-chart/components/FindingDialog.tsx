import { useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/modules/core/ui/dialog";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import type { FindingFormValues } from "@/modules/dental-chart/schemas/dentalChart";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { useRemoveFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { FindingDialogForm } from "./FindingDialogForm";

export type ClickedLocation = {
  locationKind: "TOOTH" | "SURFACE" | "ARCH";
  toothA?: number;
  surface?: FindingFormValues["location"]["surface"];
  arch?: FindingFormValues["location"]["arch"];
};

/**
 * Registrar/editar/eliminar hallazgos **sin perder de vista el odontograma**: clickear una pieza
 * ya no scrollea a un formulario lejano — abre este modal con la localización ya fija (el click la
 * decidió). Si ya hay hallazgos ahí (normativamente puede haber varios: sellante y caries
 * coexisten en la misma superficie, ADR-0017 D7), primero se elige cuál — evita el modo "adivinar
 * a cuál te referís" que tenía el flujo anterior. Se monta con `key={locationKey(location)}`
 * (ver `DentalChartEditor`) para que el estado interno arranque de cero en cada click.
 */
export function FindingDialog({
  open,
  onOpenChange,
  patientId,
  catalog,
  location,
  existingFindings,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  patientId: number;
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  location: ClickedLocation;
  existingFindings: DentalChartFindingResponse[];
}) {
  const [editing, setEditing] = useState<DentalChartFindingResponse | null | undefined>(existingFindings.length === 0 ? null : undefined);
  const removeFinding = useRemoveFinding(patientId);

  const locationText = locationLabel(location);

  if (editing === undefined) {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{locationText}</DialogTitle>
            <DialogDescription>{existingFindings.length} hallazgo(s) registrado(s) acá.</DialogDescription>
          </DialogHeader>
          <ul className="flex flex-col divide-y">
            {existingFindings.map((f) => (
              <li key={f.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                <span>
                  {findingTypeLabel(f.type ?? "")}
                  {f.subtype ? <span className="text-muted-foreground"> · {f.subtype}</span> : null}
                </span>
                <span className="flex items-center gap-1">
                  <Button type="button" variant="ghost" size="icon" aria-label="Editar" onClick={() => setEditing(f)}>
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="Eliminar"
                    disabled={removeFinding.isPending}
                    onClick={() => f.id != null && removeFinding.mutate(f.id)}
                  >
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </span>
              </li>
            ))}
          </ul>
          <DialogFooter>
            <Button type="button" size="sm" onClick={() => setEditing(null)}>
              <Plus className="mr-2 size-4" />
              Agregar otro hallazgo
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <FindingDialogForm
      open={open}
      onOpenChange={onOpenChange}
      patientId={patientId}
      catalog={catalog}
      location={location}
      locationText={locationText}
      editingFinding={editing}
      onBack={existingFindings.length > 0 ? () => setEditing(undefined) : undefined}
    />
  );
}

function locationLabel(location: ClickedLocation): string {
  const surfaceLabels: Record<string, string> = {
    MESIAL: "mesial",
    DISTAL: "distal",
    VESTIBULAR: "vestibular",
    PALATINO: "palatino",
    LINGUAL: "lingual",
    OCLUSAL: "oclusal",
    INCISAL: "incisal",
  };
  switch (location.locationKind) {
    case "TOOTH":
      return `Pieza ${location.toothA}`;
    case "SURFACE":
      return `Pieza ${location.toothA}, superficie ${surfaceLabels[location.surface ?? ""] ?? location.surface}`;
    case "ARCH":
      return `Arco ${location.arch === "SUPERIOR" ? "superior" : "inferior"}`;
    default:
      return "";
  }
}
