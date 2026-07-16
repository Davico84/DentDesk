/**
 * Esqueleto de carga del listado (loader). Vive en el módulo, no en la pantalla.
 *
 * <p>`motion-reduce:animate-none`: quien pide menos movimiento ve las siluetas quietas en vez de
 * latiendo. Sigue comunicando "esto está cargando" por su forma, sin el pulso.
 */
export function PatientListSkeleton() {
  return (
    <ul className="divide-y" aria-hidden="true">
      {Array.from({ length: 6 }).map((_, i) => (
        <li key={i} className="flex items-center gap-4 px-4 py-3">
          <div className="size-10 shrink-0 animate-pulse rounded-full bg-muted motion-reduce:animate-none" />
          <div className="flex-1 space-y-2">
            <div className="h-3.5 w-40 animate-pulse rounded bg-muted motion-reduce:animate-none" />
            <div className="h-2.5 w-24 animate-pulse rounded bg-muted motion-reduce:animate-none" />
          </div>
          <div className="h-5 w-16 animate-pulse rounded-full bg-muted motion-reduce:animate-none" />
        </li>
      ))}
    </ul>
  );
}
