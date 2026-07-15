import { Link } from "react-router-dom";
import { UserPlus, Users } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { PATHS } from "@/routes/paths";

/** Estado vacío del listado, con voz cálida y CTA cuando no hay ninguna búsqueda. */
export function PatientListEmpty({ hasQuery }: { hasQuery: boolean }) {
  return (
    <div className="flex flex-col items-center gap-4 px-6 py-16 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-primary/5 text-primary">
        <Users className="size-7" />
      </span>
      {hasQuery ? (
        <div className="space-y-1">
          <p className="font-medium">Nadie coincide con tu búsqueda</p>
          <p className="text-sm text-muted-foreground">Prueba con otro nombre o número de documento.</p>
        </div>
      ) : (
        <div className="space-y-1">
          <p className="font-medium">Aún no hay pacientes</p>
          <p className="text-sm text-muted-foreground">Cuando registres al primero, aparecerá aquí.</p>
        </div>
      )}
      {!hasQuery ? (
        <Link to={PATHS.PATIENT_NEW} className={buttonVariants({ variant: "default" })}>
          <UserPlus className="mr-2 size-4" />
          Registrar al primero
        </Link>
      ) : null}
    </div>
  );
}

/** Estado de error del listado. */
export function PatientListError() {
  return (
    <div className="px-6 py-16 text-center">
      <p className="font-medium text-destructive">No pudimos cargar los pacientes</p>
      <p className="mt-1 text-sm text-muted-foreground">Revisa tu conexión e inténtalo de nuevo en un momento.</p>
    </div>
  );
}
