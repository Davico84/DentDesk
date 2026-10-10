import { Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import type { DiagnosisResponse } from "@/modules/core/services/generated/model";
import { labelOf, tipoDiagnosticoOptions } from "./diagnosisOptions";

/** Lo que la lista necesita de un diagnóstico: comparten la forma el de la Historia y el de la nota. */
type ListedDiagnosis = Pick<DiagnosisResponse, "id" | "cie10Code" | "ubicacion" | "fdiNumber" | "tipoDiagnostico" | "createdAt">;

/**
 * Diagnósticos ya agregados. Con `onRemove` se pueden eliminar (Historia en DRAFT, nota abierta);
 * sin él, solo lectura.
 */
export function DiagnosisList({
  diagnoses,
  onRemove,
  removing = false,
}: {
  diagnoses: ListedDiagnosis[];
  onRemove?: (diagnosisId: number) => void;
  removing?: boolean;
}) {

  if (diagnoses.length === 0) {
    return <p className="text-sm text-muted-foreground">Sin diagnósticos registrados todavía.</p>;
  }

  return (
    <ul className="flex flex-col gap-2">
      {diagnoses.map((d) => (
        <li key={d.id} className="flex items-center justify-between gap-3 rounded-xl border bg-card px-3 py-2 text-sm">
          <div>
            <span className="font-medium">{d.cie10Code}</span>
            <span className="ml-2 text-muted-foreground">
              {d.ubicacion === "PIEZA" ? `Pieza ${d.fdiNumber}` : "General"} · {labelOf(tipoDiagnosticoOptions, d.tipoDiagnostico)}
              {d.createdAt ? ` · ${dateLabel(d.createdAt)}` : null}
            </span>
          </div>
          {onRemove ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="Eliminar diagnóstico"
              disabled={removing}
              onClick={() => d.id != null && onRemove(d.id)}
            >
              <Trash2 className="size-4 text-destructive" />
            </Button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
