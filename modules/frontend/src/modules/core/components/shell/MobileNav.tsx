import type { ReactNode } from "react";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "@/modules/core/ui/sheet";
import type { NavItemConfig } from "./navItems";
import { MOBILE_NAV_ID } from "./navItems";
import { NavList } from "./NavList";
import { LogoutButton } from "./LogoutButton";

/**
 * Cajón de navegación del celular, sobre el `Sheet` de shadcn: foco atrapado, `Escape` y clic en
 * el fondo los da Radix. Se cierra al elegir una sección. Etiquetas visibles: sin tooltips.
 */
export function MobileNav({
  open,
  onOpenChange,
  items,
  logo,
  onLogout,
  logoutPending,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  items: NavItemConfig[];
  logo: ReactNode;
  onLogout: () => void;
  logoutPending: boolean;
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent id={MOBILE_NAV_ID} side="left" className="flex w-72 flex-col gap-0 p-0 sm:hidden">
        <SheetTitle className="sr-only">Menú</SheetTitle>
        <SheetDescription className="sr-only">Secciones de la aplicación</SheetDescription>
        <div className="flex h-16 shrink-0 items-center border-b border-border px-5">{logo}</div>
        <div className="flex-1 overflow-y-auto px-4 py-4">
          <NavList items={items} compact={false} onNavigate={() => onOpenChange(false)} />
        </div>
        <div className="shrink-0 border-t border-border px-4 py-3">
          <LogoutButton onLogout={onLogout} pending={logoutPending} compact={false} />
        </div>
      </SheetContent>
    </Sheet>
  );
}
