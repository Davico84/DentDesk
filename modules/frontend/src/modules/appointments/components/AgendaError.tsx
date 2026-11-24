import { CalendarX2 } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";
import { Button } from "@/modules/core/ui/button";

/** La agenda no se pudo cargar. */
export function AgendaError({ onRetry }: { onRetry: () => void }) {
  return (
    <DataState
      icon={CalendarX2}
      variant="destructive"
      title="No se pudo cargar la agenda"
      description="Revisa tu conexión e inténtalo de nuevo."
      action={
        <Button type="button" variant="outline" onClick={onRetry}>
          Reintentar
        </Button>
      }
    />
  );
}
