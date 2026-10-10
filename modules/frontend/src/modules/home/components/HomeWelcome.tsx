import { Palette, Search, UserPlus } from "lucide-react";
import { useSessionStore } from "@/store/useSessionStore";
import { canAccess, sectionById } from "@/modules/core/config/sections";
import { PageContainer } from "@/modules/core/components/PageContainer";
import { ShortcutCard } from "@/modules/core/components/ShortcutCard";
import { FOCUS_SEARCH_STATE } from "@/modules/patients/utils/listNavigation";
import { PATHS } from "@/routes/paths";

/**
 * Inicio dentro del shell (`add-app-shell` design D7): saludo y atajos a lo que más se hace. El
 * atajo a Marca sale de la misma configuración de secciones que el menú, así no se muestra a quien
 * no puede entrar. Sin cerrar sesión: está en la barra.
 */
export function HomeWelcome() {
  const user = useSessionStore.use.user();
  if (!user) return null;

  const firstName = user.fullName?.trim().split(/\s+/)[0];
  const showBranding = canAccess(sectionById("branding"), user.role);

  return (
    <PageContainer className="gap-8">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">{firstName ? `Hola, ${firstName}` : "Hola"}</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Estás en <span className="font-medium text-foreground">{user.organizationName}</span>. ¿Qué
          necesitas hacer?
        </p>
      </header>

      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <li>
          <ShortcutCard
            to={PATHS.PATIENT_NEW}
            icon={UserPlus}
            title="Nuevo paciente"
            description="Registra sus datos y abre su ficha."
          />
        </li>
        <li>
          <ShortcutCard
            to={PATHS.PATIENTS}
            state={FOCUS_SEARCH_STATE}
            icon={Search}
            title="Buscar paciente"
            description="Por nombre o documento."
          />
        </li>
        {showBranding ? (
          <li>
            <ShortcutCard
              to={PATHS.BRANDING}
              icon={Palette}
              title="Personalizar marca"
              description="Logo y colores de la clínica."
            />
          </li>
        ) : null}
      </ul>
    </PageContainer>
  );
}
