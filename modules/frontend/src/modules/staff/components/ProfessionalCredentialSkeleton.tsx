/** Esqueleto de carga de la credencial propia: el resumen y los dos campos. */
export function ProfessionalCredentialSkeleton() {
  return (
    <div className="flex flex-col gap-4" aria-hidden="true">
      <div className="h-20 animate-pulse rounded-2xl bg-muted motion-reduce:animate-none" />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="h-11 animate-pulse rounded-xl bg-muted motion-reduce:animate-none" />
        <div className="h-11 animate-pulse rounded-xl bg-muted motion-reduce:animate-none" />
      </div>
    </div>
  );
}
