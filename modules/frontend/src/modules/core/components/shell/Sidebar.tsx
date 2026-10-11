import type { ReactNode } from "react";
import { PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { Monogram } from "@/modules/core/components/Monogram";
import { HintTooltip } from "@/modules/core/components/HintTooltip";
import { cn } from "@/modules/core/utils/cn";
import type { NavItemConfig } from "./navItems";
import { NavList } from "./NavList";
import { LogoutButton } from "./LogoutButton";

/**
 * Barra lateral fija (desde 640 px; en celular la reemplaza el cajón). En tablet siempre muestra
 * solo íconos; en escritorio (`canCollapse`) se expande o contrae con el botón del pie, que refleja
 * el estado con `aria-expanded`. Contraída, el logo cede su lugar a las iniciales de la clínica.
 * Componente de `core`: el logo y las acciones entran por props (`add-app-shell` design D2).
 */
export function Sidebar({
  items,
  logo,
  organizationName,
  compact,
  canCollapse,
  onToggle,
  onLogout,
  logoutPending,
}: {
  items: NavItemConfig[];
  logo: ReactNode;
  organizationName: string;
  compact: boolean;
  canCollapse: boolean;
  onToggle: () => void;
  onLogout: () => void;
  logoutPending: boolean;
}) {
  const toggleLabel = compact ? "Expandir barra lateral" : "Contraer barra lateral";
  const ToggleIcon = compact ? PanelLeftOpen : PanelLeftClose;
  const toggle = (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={!compact}
      aria-label={toggleLabel}
      className={cn(
        "flex h-10 items-center rounded-xl text-sm text-muted-foreground transition-colors",
        "hover:bg-muted hover:text-foreground focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
        compact ? "w-10 justify-center" : "w-full gap-3 px-3",
      )}
    >
      <ToggleIcon className="size-[1.125rem] shrink-0" aria-hidden="true" />
      {compact ? null : <span aria-hidden="true">Contraer</span>}
    </button>
  );

  return (
    // El fondo y el borde ocupan todo el alto de la página; el contenido viaja `sticky` con el scroll.
    <aside
      className={cn(
        "hidden shrink-0 border-r border-border bg-card sm:block",
        "transition-[width] duration-200 motion-reduce:transition-none",
        compact ? "w-16" : "w-60",
      )}
    >
      {/* Sin `items-center` en la columna: centrar encoge a lo justo el contenedor con scroll, y el arco
          del ítem activo (20 px) lo desbordaba y hacía aparecer una barra de scroll horizontal. */}
      <div className={cn("sticky top-0 flex h-screen flex-col", compact ? "px-3" : "px-4")}>
        <div className={cn("flex h-16 shrink-0 items-center", compact ? "justify-center" : "px-1")}>
          {compact ? <Monogram name={organizationName} className="size-10" /> : logo}
        </div>

        <div className="flex-1 overflow-x-hidden overflow-y-auto py-4">
          <NavList items={items} compact={compact} />
        </div>

        <div className={cn("flex shrink-0 flex-col gap-1 border-t border-border py-3", compact && "items-center")}>
          {canCollapse ? (
            compact ? (
              <HintTooltip content={toggleLabel} side="right" repeatsLabel>
                {toggle}
              </HintTooltip>
            ) : (
              toggle
            )
          ) : null}
          <LogoutButton onLogout={onLogout} pending={logoutPending} compact={compact} />
        </div>
      </div>
    </aside>
  );
}
