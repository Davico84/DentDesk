import type { ReactElement, ReactNode } from "react";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/modules/core/ui/tooltip";

/**
 * Tooltip de ayuda sobre un control (en vez del atributo nativo `title`, ver `docs/frontend.md`
 * §4.1). Componente genérico de `core/components`: no conoce ningún dominio.
 *
 * Trae su propio `TooltipProvider` (Radix admite anidarlos) para que el llamador —y sus tests— no
 * dependan de que la app lo haya montado arriba.
 *
 * `triggerDisabled`: un botón deshabilitado no recibe hover ni foco (`pointer-events: none`), así
 * que el tooltip nunca se abriría. En ese caso se envuelve en un `span` enfocable que sí los recibe
 * — justo cuando más falta explicar por qué el control no está disponible.
 */
export function HintTooltip({
  content,
  children,
  triggerDisabled = false,
  side = "bottom",
}: {
  content: ReactNode;
  children: ReactElement;
  triggerDisabled?: boolean;
  side?: "top" | "right" | "bottom" | "left";
}) {
  return (
    <TooltipProvider delayDuration={250}>
      <Tooltip>
        <TooltipTrigger asChild>
          {triggerDisabled ? (
            <span tabIndex={0} className="inline-flex rounded-md">
              {children}
            </span>
          ) : (
            children
          )}
        </TooltipTrigger>
        <TooltipContent side={side} className="max-w-72 text-xs leading-relaxed">
          {content}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
