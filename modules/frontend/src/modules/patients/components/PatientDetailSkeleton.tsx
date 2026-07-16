/**
 * Esqueleto de carga de la ficha. Espera con la FORMA de lo que va a llegar, en vez de un spinner:
 * se percibe más corta y el layout no "salta" al pintar.
 *
 * <p>`motion-reduce:animate-none`: quien pide menos movimiento ve las siluetas quietas. La forma
 * ya comunica "esto está cargando" sin el pulso.
 */
export function PatientDetailSkeleton() {
  const pulse = "animate-pulse rounded bg-muted motion-reduce:animate-none";
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col gap-6 p-6" aria-busy="true">
      <div className={`h-9 w-24 ${pulse}`} />
      <div className="flex items-center gap-4 rounded-2xl border bg-card p-5 shadow-sm">
        <div className={`size-14 shrink-0 rounded-full ${pulse}`} />
        <div className="flex-1 space-y-2">
          <div className={`h-5 w-48 ${pulse}`} />
          <div className={`h-3 w-32 ${pulse}`} />
        </div>
      </div>
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="space-y-3 rounded-2xl border bg-card p-5 shadow-sm">
          <div className={`h-4 w-32 ${pulse}`} />
          <div className={`h-3 w-full ${pulse}`} />
          <div className={`h-3 w-2/3 ${pulse}`} />
        </div>
      ))}
    </main>
  );
}
