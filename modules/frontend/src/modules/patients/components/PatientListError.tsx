import { AlertCircle } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";

/** El listado no pudo cargarse. Dice qué hacer, no solo que falló. */
export function PatientListError() {
  return (
    <DataState
      icon={AlertCircle}
      variant="destructive"
      title="No pudimos cargar los pacientes"
      description="Revisa tu conexión e inténtalo de nuevo en un momento."
    />
  );
}
