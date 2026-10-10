import { NavLink } from "react-router-dom";
import { HintTooltip } from "@/modules/core/components/HintTooltip";
import { cn } from "@/modules/core/utils/cn";
import type { NavItemConfig } from "./navItems";
import { SmileArc } from "./SmileArc";

/**
 * Un ítem de la navegación. `compact` = solo ícono: el nombre queda en `aria-label` y en un tooltip
 * al pasar el ratón o enfocar (nunca el atributo `title`, `docs/frontend.md` §4.1). Activo en la
 * ruta de su sección y en sus subrutas, con `aria-current="page"` (lo pone `NavLink`).
 */
export function NavItem({
  item,
  compact,
  onNavigate,
}: {
  item: NavItemConfig;
  compact: boolean;
  onNavigate?: () => void;
}) {
  const Icon = item.icon;
  const link = (
    <NavLink
      to={item.to}
      end={item.end}
      onClick={onNavigate}
      aria-label={compact ? item.label : undefined}
      className={({ isActive }) =>
        cn(
          "group relative flex h-10 items-center rounded-xl text-sm transition-colors",
          "focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
          compact ? "w-10 justify-center" : "gap-3 px-3",
          isActive
            ? "bg-primary/10 font-medium text-primary"
            : "text-muted-foreground hover:bg-muted hover:text-foreground",
        )
      }
    >
      {({ isActive }) => (
        <>
          <Icon className="size-[1.125rem] shrink-0" aria-hidden="true" />
          {compact ? null : <span className="truncate">{item.label}</span>}
          {isActive ? (
            <SmileArc
              className={cn("absolute bottom-1", compact ? "left-1/2 -translate-x-1/2" : "left-3")}
            />
          ) : null}
        </>
      )}
    </NavLink>
  );

  return compact ? (
    <HintTooltip content={item.label} side="right" repeatsLabel>
      {link}
    </HintTooltip>
  ) : (
    link
  );
}
