import { Link } from "react-router-dom";
import { UserX } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { DataState } from "@/modules/core/components/DataState";
import { PATHS } from "@/routes/paths";

/**
 * La ficha no existe en la clínica activa (o el enlace es de otra). Lleva **salida**: un "no
 * encontrado" sin acción es un callejón (regla `error-recovery`).
 *
 * <p>No dice "no tienes permiso" ni revela si existe en otra clínica: el aislamiento responde 404,
 * no 403, y la UI no debe filtrar por la puerta de atrás lo que el backend calla.
 */
export function PatientNotFound() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col items-center justify-center gap-4 p-6 text-center">
      <DataState
        icon={UserX}
        variant="muted"
        title="No encontramos este paciente"
        description="Puede que se haya eliminado, o que el enlace no sea de esta clínica."
        action={
          <Link to={PATHS.PATIENTS} className={buttonVariants({ variant: "default", size: "lg" })}>
            Volver al listado
          </Link>
        }
      />
    </main>
  );
}
