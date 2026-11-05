import { AlertCircle } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";

/** Los datos personales no pudieron cargarse. Dice qué hacer, no solo que falló. */
export function ProfileLoadError() {
  return (
    <div className="rounded-2xl border bg-card">
      <DataState
        icon={AlertCircle}
        variant="destructive"
        title="No pudimos cargar tus datos"
        description="Revisa tu conexión e inténtalo de nuevo en un momento."
      />
    </div>
  );
}
