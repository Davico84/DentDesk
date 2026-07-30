import { Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import type { DiagnosisResponse } from "@/modules/core/services/generated/model";
import { useRemoveDiagnosis } from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import { labelOf, tipoDiagnosticoOptions } from "./diagnosisOptions";

/** Diagnósticos ya agregados. En DRAFT se pueden eliminar; en COMPLETED, solo lectura. */
export function DiagnosisList({
  patientId,
  diagnoses,
  editable,
}: {
  patientId: number;
  diagnoses: DiagnosisResponse[];
  editable: boolean;
}) {
  const removeDiagnosis = useRemoveDiagnosis(patientId);

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
          {editable ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="Eliminar diagnóstico"
              disabled={removeDiagnosis.isPending}
              onClick={() => d.id != null && removeDiagnosis.mutate(d.id)}
            >
              <Trash2 className="size-4 text-destructive" />
            </Button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
