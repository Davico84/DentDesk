/** Esqueleto de carga de los datos personales: el nombre y el correo. */
export function ProfileDetailsSkeleton() {
  return (
    <div className="flex flex-col gap-4 rounded-2xl border bg-card p-5" aria-hidden="true">
      <div className="h-5 w-40 animate-pulse rounded bg-muted motion-reduce:animate-none" />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="h-11 animate-pulse rounded-xl bg-muted motion-reduce:animate-none" />
        <div className="h-11 animate-pulse rounded-xl bg-muted motion-reduce:animate-none" />
      </div>
    </div>
  );
}
