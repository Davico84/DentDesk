/** Mientras cargan horario, odontólogos y citas: la silueta de la grilla. */
export function AgendaSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando la agenda">
      <div className="h-9 w-full animate-pulse rounded-xl bg-muted" />
      <div className="h-112 w-full animate-pulse rounded-2xl bg-muted" />
    </div>
  );
}
