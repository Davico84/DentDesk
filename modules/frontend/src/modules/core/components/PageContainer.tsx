import type { ComponentProps } from "react";
import { cn } from "@/modules/core/utils/cn";

/**
 * Contenedor de toda pantalla interna: **un solo ancho** para que el contenido no salte de
 * tamaño al navegar entre la ficha, la Historia, el odontograma o las consultas. Primitivo
 * genérico de `core`: no conoce ningún dominio.
 *
 * Cambiar el ancho de la app es cambiar esta clase, no cada pantalla.
 *
 * Es un `<div>`: el único `<main>` lo pone el shell (`layouts/AppLayout`, `add-app-shell` design D3).
 */
export function PageContainer({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("mx-auto flex w-full max-w-5xl flex-col gap-6 p-4 sm:p-6", className)} {...props} />;
}
