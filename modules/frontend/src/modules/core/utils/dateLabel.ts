/** "28 jul 2026" a partir de un ISO 8601 (`Instant` o `LocalDate`). Primitivo genérico de `core`. */
export function dateLabel(iso: string | undefined): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("es-PE", { day: "numeric", month: "short", year: "numeric" }).format(date);
}
