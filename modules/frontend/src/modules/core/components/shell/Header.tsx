import { Menu } from "lucide-react";
import { Monogram } from "@/modules/core/components/Monogram";
import { ThemeToggle } from "@/modules/core/ui/theme-toggle";
import { ROLE_LABEL } from "@/modules/core/auth/roleLabel";
import type { Role } from "@/store/useSessionStore";
import { MOBILE_NAV_ID } from "./navItems";

/**
 * Cabecera del shell: la clínica a la izquierda; quién usa la app (nombre o, si no tiene, su correo,
 * y su rol) y el modo claro/oscuro a la derecha. En celular abre el cajón de navegación.
 */
export function Header({
  organizationName,
  userName,
  role,
  mobileNavOpen,
  onOpenMobileNav,
}: {
  organizationName: string;
  userName: string;
  role: Role;
  mobileNavOpen: boolean;
  onOpenMobileNav: () => void;
}) {
  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b border-border bg-background/85 px-4 backdrop-blur-sm sm:px-6">
      <button
        type="button"
        onClick={onOpenMobileNav}
        aria-label="Abrir menú"
        aria-expanded={mobileNavOpen}
        aria-controls={MOBILE_NAV_ID}
        className="inline-flex size-9 items-center justify-center rounded-md text-foreground hover:bg-muted focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring sm:hidden"
      >
        <Menu className="size-5" aria-hidden="true" />
      </button>

      <p className="min-w-0 truncate font-semibold text-foreground">{organizationName}</p>

      <div className="ml-auto flex items-center gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <Monogram name={userName} tone="secondary" className="size-8 rounded-full" />
          <div className="hidden min-w-0 flex-col leading-tight md:flex">
            <span className="truncate text-sm font-medium text-foreground">{userName}</span>
            <span className="text-xs text-muted-foreground">{ROLE_LABEL[role]}</span>
          </div>
          <span className="sr-only md:hidden">
            {userName}, {ROLE_LABEL[role]}
          </span>
        </div>
        <ThemeToggle />
      </div>
    </header>
  );
}
