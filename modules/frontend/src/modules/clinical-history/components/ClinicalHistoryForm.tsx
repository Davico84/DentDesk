import { ClipboardList, Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { useClinicalHistory, useCreateClinicalHistory } from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import { ClinicalHistoryEditor } from "./ClinicalHistoryEditor";

/** Feature de la Historia Clínica de un paciente: crear, guardar en DRAFT, sellar. La pantalla solo la monta. */
export function ClinicalHistoryForm({ patientId }: { patientId: number }) {
  const { data: history, isLoading, isError, error } = useClinicalHistory(patientId);
  const createHistory = useCreateClinicalHistory(patientId);

  const notFound = isError && isAxios404(error);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (notFound || !history) {
    return (
      <div className="flex flex-col items-center gap-4 rounded-2xl border bg-card p-8 text-center">
        <ClipboardList className="size-8 text-muted-foreground" />
        <div>
          <p className="font-medium">Este paciente aún no tiene Historia Clínica</p>
          <p className="text-sm text-muted-foreground">
            Es el documento inicial obligatorio antes de cualquier tratamiento.
          </p>
        </div>
        <Button onClick={() => createHistory.mutate()} disabled={createHistory.isPending}>
          {createHistory.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          Crear Historia Clínica
        </Button>
      </div>
    );
  }

  return <ClinicalHistoryEditor patientId={patientId} />;
}

function isAxios404(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "response" in error &&
    (error as { response?: { status?: number } }).response?.status === 404
  );
}
