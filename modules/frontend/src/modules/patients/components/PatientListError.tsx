/** El listado no pudo cargarse. Dice qué hacer, no solo que falló. */
export function PatientListError() {
  return (
    <div className="px-6 py-16 text-center">
      <p className="font-medium text-destructive">No pudimos cargar los pacientes</p>
      <p className="mt-1 text-sm text-muted-foreground">Revisa tu conexión e inténtalo de nuevo en un momento.</p>
    </div>
  );
}

