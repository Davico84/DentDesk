import { LogOut } from "lucide-react";
import { HintTooltip } from "@/modules/core/components/HintTooltip";
import { cn } from "@/modules/core/utils/cn";

/**
 * Cerrar sesión, al pie de la barra. Deshabilitado mientras la petición está en curso para no
 * duplicarla. En modo solo ícono, el nombre va en `aria-label` y en un tooltip.
 */
export function LogoutButton({
  onLogout,
  pending,
  compact,
}: {
  onLogout: () => void;
  pending: boolean;
  compact: boolean;
}) {
  const label = pending ? "Cerrando sesión…" : "Cerrar sesión";
  const button = (
    <button
      type="button"
      onClick={onLogout}
      disabled={pending}
      aria-label={compact ? label : undefined}
      className={cn(
        "flex h-10 items-center rounded-xl text-sm text-muted-foreground transition-colors",
        "hover:bg-destructive/10 hover:text-destructive disabled:pointer-events-none disabled:opacity-60",
        "focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
        compact ? "w-10 justify-center" : "w-full gap-3 px-3",
      )}
    >
      <LogOut className="size-[1.125rem] shrink-0" aria-hidden="true" />
      {compact ? null : <span>{label}</span>}
    </button>
  );

  return compact ? (
    <HintTooltip content={label} side="right" repeatsLabel triggerDisabled={pending}>
      {button}
    </HintTooltip>
  ) : (
    button
  );
}
