import { Link } from "react-router-dom";
import { UserPlus, Users } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { DataState } from "@/modules/core/components/DataState";
import { PATHS } from "@/routes/paths";

/**
 * Listado sin resultados. Distingue los dos vacíos, que no son el mismo problema: **no hay nadie
 * todavía** (→ invita a registrar al primero) vs **la búsqueda no encuentra** (→ sugiere qué
 * probar). Un "0 resultados" a secas es un callejón.
 */
export function PatientListEmpty({ hasQuery }: { hasQuery: boolean }) {
  return (
    <DataState
      icon={Users}
      title={hasQuery ? "Nadie coincide con tu búsqueda" : "Aún no hay pacientes"}
      description={
        hasQuery
          ? "Prueba con otro nombre o número de documento."
          : "Cuando registres al primero, aparecerá aquí."
      }
      action={
        !hasQuery ? (
          <Link to={PATHS.PATIENT_NEW} className={buttonVariants({ variant: "default" })}>
            <UserPlus className="mr-2 size-4" />
            Registrar al primero
          </Link>
        ) : undefined
      }
    />
  );
}
