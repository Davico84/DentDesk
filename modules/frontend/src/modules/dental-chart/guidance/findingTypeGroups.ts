import type { ComboboxGroup, ComboboxOption } from "@/modules/core/components/GroupedCombobox";
import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { SCOPE_TITLE, type FindingScope } from "./chartGuideSteps";
import { SCOPE_PLACE } from "./relocation";

const SCOPE_ORDER: FindingScope[] = ["SURFACE", "TOOTH", "BETWEEN_TEETH", "ARCH", "TOOTH_RANGE"];

/** Un tipo por entrada del catálogo (los subtipos repiten el tipo), con su alcance, ordenados por nombre. */
function catalogTypes(catalog: DentalFindingCatalogEntryResponse[] | undefined): { type: string; scope: FindingScope }[] {
  const byType = new Map<string, FindingScope>();
  for (const e of catalog ?? []) {
    if (e.type && e.scope && !byType.has(e.type)) byType.set(e.type, e.scope);
  }
  return [...byType]
    .map(([type, scope]) => ({ type, scope }))
    .sort((a, b) => findingTypeLabel(a.type).localeCompare(findingTypeLabel(b.type), "es"));
}

export function scopeOfType(catalog: DentalFindingCatalogEntryResponse[] | undefined, type: string): FindingScope | undefined {
  return catalog?.find((e) => e.type === type)?.scope;
}

/**
 * Grupos del selector de tipo (design D5). Con una zona tocada: "Para esta zona" y, si `includeOthers`, "En otras
 * zonas" con dónde se registra cada uno (en edición no se ofrecen: la UI no mueve un hallazgo de lugar). Sin zona (el
 * buscador de la barra): un grupo por alcance.
 */
export function findingTypeGroups(
  catalog: DentalFindingCatalogEntryResponse[] | undefined,
  here: FindingScope | null,
  includeOthers = true,
): ComboboxGroup[] {
  const types = catalogTypes(catalog);
  const option = ({ type, scope }: { type: string; scope: FindingScope }, withPlace: boolean): ComboboxOption => ({
    value: type,
    label: findingTypeLabel(type),
    hint: withPlace ? `Se registra ${SCOPE_PLACE[scope]}` : undefined,
  });

  if (here == null) {
    return SCOPE_ORDER.map((scope) => ({
      id: scope,
      label: SCOPE_TITLE[scope],
      options: types.filter((t) => t.scope === scope).map((t) => option(t, false)),
    })).filter((g) => g.options.length > 0);
  }
  const groups: ComboboxGroup[] = [
    { id: "here", label: "Para esta zona", options: types.filter((t) => t.scope === here).map((t) => option(t, false)) },
  ];
  if (includeOthers) {
    groups.push({ id: "other", label: "En otras zonas", options: types.filter((t) => t.scope !== here).map((t) => option(t, true)) });
  }
  return groups.filter((g) => g.options.length > 0);
}
