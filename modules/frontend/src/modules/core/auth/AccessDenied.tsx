import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { cn } from "@/modules/core/utils/cn";
import { PATHS } from "@/routes/paths";

/**
 * Estado de acceso denegado (rol sin permiso) con opción de volver.
 *
 * `inShell`: dentro del shell, que ya pone el `<main>`. Sin alto de pantalla y con el foco en el
 * título al montar, para que el lector de pantalla anuncie el motivo (`add-app-shell` design D3).
 */
export function AccessDenied({ inShell = false }: { inShell?: boolean }) {
  const titleRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (inShell) titleRef.current?.focus();
  }, [inShell]);

  const Root = inShell ? "div" : "main";
  return (
    <Root
      className={cn(
        "flex flex-col items-center justify-center gap-3 p-4 text-center",
        inShell ? "py-24" : "min-h-screen",
      )}
    >
      <h1 ref={titleRef} tabIndex={inShell ? -1 : undefined} className="text-2xl font-bold outline-hidden">
        Acceso denegado
      </h1>
      <p className="max-w-sm text-muted-foreground">
        No tienes permiso para ver esta página.
      </p>
      <Link to={PATHS.ROOT} className={cn(buttonVariants({ variant: "outline" }))}>
        Volver al inicio
      </Link>
    </Root>
  );
}
