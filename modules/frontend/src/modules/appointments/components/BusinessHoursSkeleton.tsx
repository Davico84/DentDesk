/** Mientras carga el horario de atención. */
export function BusinessHoursSkeleton() {
  return (
    <div className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando el horario de atención">
      {Array.from({ length: 7 }, (_, i) => (
        <div key={i} className="h-14 w-full animate-pulse rounded-xl bg-muted" />
      ))}
    </div>
  );
}
