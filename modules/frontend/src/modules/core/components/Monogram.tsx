import { cn } from "@/modules/core/utils/cn";
import { initialsOf } from "@/modules/core/utils/initials";

/**
 * Iniciales en un cuadro con los colores de marca. Componente genérico de `core/components`: sirve
 * para la clínica en la barra contraída y para el usuario en la cabecera.
 */
export function Monogram({
  name,
  tone = "primary",
  className,
}: {
  name: string;
  tone?: "primary" | "secondary";
  className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-xl text-xs font-bold tracking-wide",
        tone === "primary" ? "bg-primary text-primary-foreground" : "bg-secondary text-secondary-foreground",
        className,
      )}
    >
      {initialsOf(name)}
    </span>
  );
}
