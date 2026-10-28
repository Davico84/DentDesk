import { PageContainer } from "@/modules/core/components/PageContainer";
import { ProfessionalCredentialSection } from "@/modules/staff/components/ProfessionalCredentialSection";
import { useMyProfileQuery } from "../hooks/useProfile";
import { ChangePasswordForm } from "./ChangePasswordForm";
import { ProfileDetailsForm } from "./ProfileDetailsForm";
import { ProfileDetailsSkeleton } from "./ProfileDetailsSkeleton";
import { ProfileLoadError } from "./ProfileLoadError";

/**
 * Feature "Mi perfil" (update-authentication-my-profile D6): lo que es de la persona, no de una clínica. Tres secciones
 * apiladas, sin pestañas: son bloques cortos, y una pestaña escondería el aviso de la credencial vencida.
 */
export function ProfilePage() {
  const query = useMyProfileQuery();

  return (
    <PageContainer className="max-w-3xl">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Mi perfil</h1>
        <p className="mt-1 text-sm text-muted-foreground">Tus datos, tu contraseña y tu credencial profesional.</p>
      </header>

      {query.isLoading ? (
        <ProfileDetailsSkeleton />
      ) : query.isError || !query.data ? (
        <ProfileLoadError />
      ) : (
        <ProfileDetailsForm profile={query.data} />
      )}

      <ChangePasswordForm />

      <ProfessionalCredentialSection />
    </PageContainer>
  );
}
