import { useState } from "react";
import { GroupedCombobox } from "@/modules/core/components/GroupedCombobox";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/modules/core/ui/dialog";
import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import type { FindingScope } from "./chartGuideSteps";
import { findingTypeGroups, scopeOfType } from "./findingTypeGroups";

/**
 * "Buscar hallazgo" desde la barra del odontograma (design D5): empezar por el nombre sin haber tocado el gráfico.
 * Lista todo el catálogo agrupado por dónde se registra; al elegir, el llamador activa el modo ubicar con ese tipo.
 */
export function FindingSearchDialog({
  open,
  onOpenChange,
  catalog,
  onPick,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  onPick: (type: string, scope: FindingScope) => void;
}) {
  const [listOpen, setListOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="sm:max-w-md"
        onEscapeKeyDown={(e) => {
          if (listOpen) e.preventDefault();
        }}
      >
        <DialogHeader>
          <DialogTitle>Buscar hallazgo</DialogTitle>
          <DialogDescription>Elige el hallazgo y después toca en el odontograma dónde va.</DialogDescription>
        </DialogHeader>
        <GroupedCombobox
          id="finding-search"
          label="Hallazgo"
          groups={findingTypeGroups(catalog, null)}
          value=""
          onSelect={(type) => {
            const scope = scopeOfType(catalog, type);
            if (scope) onPick(type, scope);
          }}
          onOpenChange={setListOpen}
          placeholder="Escribe el nombre, ej.: corona"
          emptyMessage="No hay ningún hallazgo con ese nombre en el catálogo."
        />
      </DialogContent>
    </Dialog>
  );
}
