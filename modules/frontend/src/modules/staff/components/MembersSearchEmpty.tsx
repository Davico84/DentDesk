import { SearchX } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";
import { Button } from "@/modules/core/ui/button";

/** Nadie coincide con la búsqueda. La lista nunca está vacía de verdad: el administrador siempre aparece. */
export function MembersSearchEmpty({ onClear }: { onClear: () => void }) {
  return (
    <DataState
      icon={SearchX}
      variant="muted"
      title="Nadie coincide con tu búsqueda"
      description="Prueba con otro nombre o correo."
      action={<Button variant="outline" onClick={onClear}>Limpiar búsqueda</Button>}
    />
  );
}
