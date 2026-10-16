import { useMemo } from "react";
import { GuidedTour } from "@/modules/core/components/guided-tour/GuidedTour";
import type { TourStep } from "@/modules/core/components/guided-tour/tourTypes";
import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { CHART_GUIDE_ANCHOR, findChartAnchor, type ChartGuideAnchor } from "./chartGuideAnchors";
import { buildChartGuideSteps } from "./chartGuideSteps";

/** Funciones de ancla estables (identidad fija): el recorrido re-mide solo al cambiar de paso. */
const ANCHORS = Object.fromEntries(
  Object.values(CHART_GUIDE_ANCHOR).map((anchor) => [anchor, () => findChartAnchor(anchor)]),
) as Record<ChartGuideAnchor, () => Element | null>;

/**
 * Guía paso a paso sobre el odontograma real (spec dental-chart, "Guía para registrar hallazgos sobre el
 * odontograma"): resalta dónde tocar para cada localización, con ejemplos del catálogo. No registra nada.
 */
export function DentalChartGuide({
  open,
  catalog,
  onClose,
}: {
  open: boolean;
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  onClose: () => void;
}) {
  const steps: TourStep[] = useMemo(
    () =>
      buildChartGuideSteps(catalog ?? []).map((step) => ({
        title: step.title,
        body: step.body,
        examples: step.examples,
        anchor: step.anchor ? ANCHORS[step.anchor] : undefined,
      })),
    [catalog],
  );
  return <GuidedTour open={open} steps={steps} onClose={onClose} />;
}
