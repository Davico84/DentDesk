import { cloneElement, type ReactElement, type ReactNode } from "react";
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
 *
 * `repeatsLabel`: el tooltip solo muestra a la vista el `aria-label` que el control ya tiene (íconos
 * de la barra lateral). Entonces no se enlaza como descripción, para que el lector de pantalla no
 * anuncie el nombre dos veces.
 */
export function HintTooltip({
  content,
  children,
  triggerDisabled = false,
  side = "bottom",
  repeatsLabel = false,
}: {
  content: ReactNode;
  children: ReactElement;
  triggerDisabled?: boolean;
  side?: "top" | "right" | "bottom" | "left";
  repeatsLabel?: boolean;
}) {
  // Las props del hijo pisan las del `Slot` de Radix: así se anula su `aria-describedby`.
  const trigger = repeatsLabel ? cloneElement(children as ReactElement<{ "aria-describedby"?: string }>, { "aria-describedby": undefined }) : children;
  return (
    <TooltipProvider delayDuration={250}>
      <Tooltip>
        <TooltipTrigger asChild>
          {triggerDisabled ? (
            <span tabIndex={0} className="inline-flex rounded-md">
              {trigger}
            </span>
          ) : (
            trigger
          )}
        </TooltipTrigger>
        <TooltipContent side={side} className="max-w-72 text-xs leading-relaxed">
          {content}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
