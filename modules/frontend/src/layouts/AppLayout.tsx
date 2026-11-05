import { useState } from "react";
import { Outlet } from "react-router-dom";
import { UserRound } from "lucide-react";
import { useSessionStore } from "@/store/useSessionStore";
import { useSidebarStore } from "@/store/useSidebarStore";
import { useLogout } from "@/modules/auth/hooks/useAuth";
import { OrgLogo } from "@/modules/branding/components/OrgLogo";
import { DESKTOP_QUERY, useMediaQuery } from "@/modules/core/hooks/useMediaQuery";
import { navItemsFor } from "@/modules/core/components/shell/navItems";
import { Sidebar } from "@/modules/core/components/shell/Sidebar";
import { Header } from "@/modules/core/components/shell/Header";
import { MobileNav } from "@/modules/core/components/shell/MobileNav";
import type { AccountLink } from "@/modules/core/components/shell/Header";
import { PATHS } from "@/routes/paths";

/** Lo que es de la persona, en el menú de la cuenta: sus datos, su contraseña y su credencial. */
const ACCOUNT_LINKS: AccountLink[] = [{ label: "Mi perfil", to: PATHS.PROFILE, icon: UserRound }];

/**
 * Shell de la zona privada (`add-app-shell` design D2): barra lateral, cabecera y el **único**
 * `<main>` de la página. Vive en `layouts/` porque conecta `auth` (cerrar sesión), `branding` (logo)
 * y los stores con los componentes genéricos de `core/components/shell`.
 *
 * El marco mide como mucho 1920 px y queda centrado sobre un fondo tenue (D6); el ancho del
 * contenido lo sigue dando `PageContainer` en cada pantalla.
 */
export function AppLayout() {
  const user = useSessionStore.use.user();
  const collapsed = useSidebarStore.use.collapsed();
  const toggle = useSidebarStore.use.toggle();
  const isDesktop = useMediaQuery(DESKTOP_QUERY);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const logout = useLogout();

  // `RequireAuth` envuelve este layout: sin usuario nunca se llega aquí.
  if (!user) return null;

  const items = navItemsFor(user.role);
  const userName = user.fullName?.trim() || user.email;
  const logo = <OrgLogo className="h-8 w-auto max-w-40 object-contain" />;
  const onLogout = () => logout.mutate();

  return (
    <div className="min-h-screen bg-muted/40">
      <div className="mx-auto flex min-h-screen w-full max-w-[1920px] bg-background">
        <Sidebar
          items={items}
          logo={logo}
          organizationName={user.organizationName}
          // Tablet: siempre solo íconos. Escritorio: lo que el usuario eligió.
          compact={!isDesktop || collapsed}
          canCollapse={isDesktop}
          onToggle={toggle}
          onLogout={onLogout}
          logoutPending={logout.isPending}
        />
        <div className="flex min-w-0 flex-1 flex-col">
          <Header
            organizationName={user.organizationName}
            userName={userName}
            role={user.role}
            accountLinks={ACCOUNT_LINKS}
            mobileNavOpen={mobileNavOpen}
            onOpenMobileNav={() => setMobileNavOpen(true)}
          />
          <main className="flex-1">
            <Outlet />
          </main>
        </div>
      </div>
      <MobileNav
        open={mobileNavOpen}
        onOpenChange={setMobileNavOpen}
        items={items}
        logo={logo}
        onLogout={onLogout}
        logoutPending={logout.isPending}
      />
    </div>
  );
}
