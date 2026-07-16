import { Link } from "react-router-dom";
import { UserX } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
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
      <span className="flex size-14 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <UserX className="size-7" />
      </span>
      <div className="space-y-1">
        <p className="font-medium">No encontramos este paciente</p>
        <p className="text-sm text-muted-foreground">
          Puede que se haya eliminado, o que el enlace no sea de esta clínica.
        </p>
      </div>
      <Link to={PATHS.PATIENTS} className={buttonVariants({ variant: "default", size: "lg" })}>
        Volver al listado
      </Link>
    </main>
  );
}
