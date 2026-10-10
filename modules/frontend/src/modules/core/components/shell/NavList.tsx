import type { NavItemConfig } from "./navItems";
import { NavItem } from "./NavItem";

/** La lista de secciones. La comparten la barra lateral y el cajón móvil. */
export function NavList({
  items,
  compact,
  onNavigate,
}: {
  items: NavItemConfig[];
  compact: boolean;
  onNavigate?: () => void;
}) {
  return (
    <nav aria-label="Principal">
      <ul className="flex flex-col gap-1">
        {items.map((item) => (
          <li key={item.id} className={compact ? "flex justify-center" : undefined}>
            <NavItem item={item} compact={compact} onNavigate={onNavigate} />
          </li>
        ))}
      </ul>
    </nav>
  );
}
