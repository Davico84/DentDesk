import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { CHART_GUIDE_ANCHOR, GUIDE_GAP, GUIDE_TOOTH, type ChartGuideAnchor } from "./chartGuideAnchors";

export type FindingScope = NonNullable<DentalFindingCatalogEntryResponse["scope"]>;

/** Un paso de la guía, sin React: el componente le pone la función de ancla. */
export interface ChartGuideStep {
  title: string;
  body: string;
  anchor?: ChartGuideAnchor;
  examples?: string[];
}

const MAX_EXAMPLES = 3;

/** Hasta 3 nombres de tipos con ese alcance, en el orden del catálogo y sin repetir (un tipo tiene varios subtipos). */
export function examplesFor(catalog: DentalFindingCatalogEntryResponse[], scope: FindingScope): string[] {
  const types: string[] = [];
  for (const entry of catalog) {
    if (entry.scope === scope && entry.type && !types.includes(entry.type)) types.push(entry.type);
    if (types.length === MAX_EXAMPLES) break;
  }
  return types.map(findingTypeLabel);
}

/** Nombre corto de cada alcance, el mismo que usa su paso de la guía (también agrupa el buscador de la barra). */
export const SCOPE_TITLE: Record<FindingScope, string> = {
  SURFACE: "Una cara de la pieza",
  TOOTH: "Toda la pieza",
  BETWEEN_TEETH: "Entre dos piezas",
  ARCH: "Un arco completo",
  TOOTH_RANGE: "Varias piezas seguidas",
};

const LOCATION_STEPS: { scope: FindingScope; anchor: ChartGuideAnchor; title: string; body: string }[] = [
  {
    scope: "SURFACE",
    anchor: CHART_GUIDE_ANCHOR.toothSurface,
    title: SCOPE_TITLE.SURFACE,
    body: `Toca una de las caras de la corona (por ejemplo, la central de la ${GUIDE_TOOTH}) para lo que afecta solo esa superficie.`,
  },
  {
    scope: "TOOTH",
    anchor: CHART_GUIDE_ANCHOR.toothNumber,
    title: SCOPE_TITLE.TOOTH,
    body: `Toca el número de la pieza (por ejemplo, el ${GUIDE_TOOTH}) para lo que afecta a la pieza completa.`,
  },
  {
    scope: "BETWEEN_TEETH",
    anchor: CHART_GUIDE_ANCHOR.betweenTeeth,
    title: SCOPE_TITLE.BETWEEN_TEETH,
    body: `Toca el espacio entre dos piezas vecinas (por ejemplo, entre la ${GUIDE_GAP[0]} y la ${GUIDE_GAP[1]}) para lo que ocurre entre ellas.`,
  },
  {
    scope: "ARCH",
    anchor: CHART_GUIDE_ANCHOR.arch,
    title: SCOPE_TITLE.ARCH,
    body: "Usa los botones de arco superior o inferior para lo que abarca toda la arcada.",
  },
  {
    scope: "TOOTH_RANGE",
    anchor: CHART_GUIDE_ANCHOR.range,
    title: SCOPE_TITLE.TOOTH_RANGE,
    body: "Usa \"Registrar rango\" y elige la primera y la última pieza de la misma fila: queda un solo hallazgo.",
  },
];

/**
 * Pasos de la guía del odontograma (design D3): introducción, una por localización y el cierre. Cada localización
 * nombra hasta 3 ejemplos del catálogo; si el catálogo no tiene tipos con ese alcance, su paso no se muestra.
 */
export function buildChartGuideSteps(catalog: DentalFindingCatalogEntryResponse[]): ChartGuideStep[] {
  const locationSteps = LOCATION_STEPS.flatMap(({ scope, ...step }) => {
    const examples = examplesFor(catalog, scope);
    return examples.length > 0 ? [{ ...step, examples }] : [];
  });
  return [
    {
      title: "¿Dónde registro un hallazgo?",
      body: "El lugar que tocas en el odontograma decide qué hallazgos te ofrece el registro. Te mostramos los cinco lugares posibles.",
    },
    ...locationSteps,
    {
      title: "¿No encuentras el hallazgo?",
      body: "Búscalo por su nombre con este botón, y después toca en el odontograma dónde va: solo se activan los lugares donde se puede registrar. También puedes escribir el nombre en \"Tipo de hallazgo\" al registrar; si va en otro lugar, te lleva ahí.",
      anchor: CHART_GUIDE_ANCHOR.search,
    },
  ];
}
