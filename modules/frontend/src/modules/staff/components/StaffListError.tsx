import { AlertCircle } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";

/** Una de las listas no pudo cargarse. Dice qué hacer, no solo que falló. */
export function StaffListError({ what }: { what: string }) {
  return (
    <DataState
      icon={AlertCircle}
      variant="destructive"
      title={`No pudimos cargar ${what}`}
      description="Revisa tu conexión e inténtalo de nuevo en un momento."
    />
  );
}
