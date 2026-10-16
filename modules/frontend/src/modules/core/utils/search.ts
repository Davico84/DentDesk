/**
 * Normaliza un texto para buscar sin tildes ni mayúsculas: "María" y "maria" son la misma búsqueda. Primitivo
 * genérico de `core` (lo usan el personal y el catálogo de hallazgos).
 */
export function normalizeForSearch(value: string): string {
  return value.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().trim();
}
