import type { LucideIcon } from "lucide-react";
import { ChevronDown, Menu } from "lucide-react";
import { Link } from "react-router-dom";
import { Monogram } from "@/modules/core/components/Monogram";
import { ThemeToggle } from "@/modules/core/ui/theme-toggle";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/modules/core/ui/dropdown-menu";
import { ROLE_LABEL } from "@/modules/core/auth/roleLabel";
import type { Role } from "@/store/useSessionStore";
import { MOBILE_NAV_ID } from "./navItems";

/** Un enlace del menú de la cuenta (lo que es de la persona, no de una sección de la clínica). */
export interface AccountLink {
  label: string;
  to: string;
  icon: LucideIcon;
}

/**
 * Cabecera del shell: la clínica a la izquierda; quién usa la app (nombre o, si no tiene, su correo,
 * y su rol) y el modo claro/oscuro a la derecha. En celular abre el cajón de navegación. Quién usa la app
 * abre el menú de su cuenta, con los enlaces que recibe por props: `core` no conoce ningún dominio.
 */
export function Header({
  organizationName,
  userName,
  role,
  accountLinks,
  mobileNavOpen,
  onOpenMobileNav,
}: {
  organizationName: string;
  userName: string;
  role: Role;
  accountLinks: AccountLink[];
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
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={`Tu cuenta: ${userName}, ${ROLE_LABEL[role]}`}
            className="flex min-h-11 min-w-0 items-center gap-2.5 rounded-xl px-1.5 text-left hover:bg-muted focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Monogram name={userName} tone="secondary" className="size-8 rounded-full" />
            <span className="hidden min-w-0 flex-col leading-tight md:flex">
              <span className="truncate text-sm font-medium text-foreground">{userName}</span>
              <span className="text-xs text-muted-foreground">{ROLE_LABEL[role]}</span>
            </span>
            <ChevronDown className="hidden size-4 text-muted-foreground md:block" aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-56">
            <DropdownMenuLabel className="font-normal">
              <span className="block truncate text-sm font-medium">{userName}</span>
              <span className="block text-xs text-muted-foreground">{ROLE_LABEL[role]}</span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {accountLinks.map(({ label, to, icon: Icon }) => (
              <DropdownMenuItem key={to} asChild className="min-h-11 cursor-pointer">
                <Link to={to}>
                  <Icon className="size-4" aria-hidden="true" />
                  {label}
                </Link>
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
        <ThemeToggle />
      </div>
    </header>
  );
}
