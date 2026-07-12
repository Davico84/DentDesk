import { Link } from "react-router-dom";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { cn } from "@/modules/core/utils/cn";
import { PATHS } from "@/routes/paths";

/** Estado de acceso denegado (rol sin permiso) con opción de volver. */
export function AccessDenied() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-3 p-4 text-center">
      <h1 className="text-2xl font-bold">Acceso denegado</h1>
      <p className="max-w-sm text-muted-foreground">
        No tienes permiso para ver esta página.
      </p>
      <Link to={PATHS.ROOT} className={cn(buttonVariants({ variant: "outline" }))}>
        Volver al inicio
      </Link>
    </main>
  );
}
