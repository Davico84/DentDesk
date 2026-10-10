import { cn } from "@/modules/core/utils/cn";

/**
 * El arco de sonrisa que marca la sección activa: el mismo motivo de los arcos del login, en
 * pequeño. Es el único gesto propio del shell (`add-app-shell` design D9); toma el color del
 * texto (`currentColor`), así que sigue la marca de cada clínica. Decorativo: el estado activo lo
 * comunica `aria-current`, no este dibujo.
 */
export function SmileArc({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 20 6"
      fill="none"
      aria-hidden="true"
      className={cn("pointer-events-none h-1.5 w-5", className)}
    >
      <path d="M1.5 1.5 Q10 6.5 18.5 1.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
    </svg>
  );
}
