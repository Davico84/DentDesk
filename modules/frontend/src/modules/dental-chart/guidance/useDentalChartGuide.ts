import { useState } from "react";
import type { DentalChartResponse } from "@/modules/core/services/generated/model";
import { useGuidanceStore } from "@/store/useGuidanceStore";
import { useSessionStore } from "@/store/useSessionStore";

/**
 * Cuándo se muestra la guía del odontograma (design D4). Se abre sola solo con el bloque **ya cargado y abierto**, el
 * catálogo disponible, sin un registro de hallazgo, un buscador ni el modo ubicar activos (`busy`) y si el usuario no
 * la vio: mientras React Query carga,
 * `block` es `undefined` y sin esta condición se abriría un instante sobre un odontograma sellado. Derivado, sin efectos.
 */
export function useDentalChartGuide({
  block,
  catalogReady,
  busy,
}: {
  block: DentalChartResponse | undefined;
  catalogReady: boolean;
  busy: boolean;
}) {
  const userId = useSessionStore((s) => s.user?.id);
  const seen = useGuidanceStore((s) => s.hasSeen("dental-chart", userId));
  const markSeen = useGuidanceStore((s) => s.markSeen);
  const [manualOpen, setManualOpen] = useState(false);

  const editable = block != null && block.status === "OPEN";
  const autoOpen = editable && catalogReady && !seen && !busy && userId != null;

  return {
    available: editable,
    open: editable && (manualOpen || autoOpen),
    openGuide: () => setManualOpen(true),
    closeGuide: () => {
      setManualOpen(false);
      markSeen("dental-chart", userId);
    },
  };
}
