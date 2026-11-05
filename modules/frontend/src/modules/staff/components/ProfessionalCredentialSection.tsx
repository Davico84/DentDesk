import { BadgeCheck } from "lucide-react";
import { useScrollToHash } from "@/modules/core/hooks/useScrollToHash";
import { CREDENTIAL_SECTION_ID } from "@/routes/paths";
import { useMyCredentialQuery } from "../hooks/useProfessionalCredential";
import { ClinicalCapacitySummary } from "./ClinicalCapacitySummary";
import { ProfessionalCredentialForm } from "./ProfessionalCredentialForm";
import { ProfessionalCredentialSkeleton } from "./ProfessionalCredentialSkeleton";
import { StaffListError } from "./StaffListError";

/**
 * Sección "Credencial profesional" de Mi perfil (update-staff-professional-credential D7, reubicada por
 * update-authentication-my-profile D6): cualquier miembro declara aquí, para sí mismo, su colegiatura y hasta cuándo
 * está habilitado. Vale en todas sus clínicas; lo que cambia por clínica es la capacidad (la clínica puede retirarla).
 * Lleva el ancla `#credencial`: a ella llevan el aviso de firma y la ruta anterior.
 */
export function ProfessionalCredentialSection() {
  const query = useMyCredentialQuery();
  useScrollToHash(CREDENTIAL_SECTION_ID, !query.isLoading);

  return (
    <section id={CREDENTIAL_SECTION_ID} aria-labelledby="credential-title" className="flex scroll-mt-6 flex-col gap-4">
      <header>
        <h2 id="credential-title" className="flex items-center gap-2 text-lg font-semibold tracking-tight">
          <BadgeCheck className="size-5 text-primary" aria-hidden="true" />
          Credencial profesional
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Para firmar actos clínicos necesitas tu colegiatura y una habilitación vigente. La declaras tú, y vale en
          todas las clínicas donde trabajas.
        </p>
      </header>

      {query.isLoading ? (
        <ProfessionalCredentialSkeleton />
      ) : query.isError ? (
        <div className="rounded-2xl border bg-card">
          <StaffListError what="tu credencial" />
        </div>
      ) : (
        <>
          <ClinicalCapacitySummary capacity={query.data?.clinicalCapacity} />
          <div className="rounded-2xl border bg-card p-5 shadow-xs">
            <ProfessionalCredentialForm credential={query.data?.credential} />
          </div>
          <p className="text-xs text-muted-foreground">
            Dentdesk no verifica estos datos con el Colegio Odontológico: se guardan tal como los declaras.
          </p>
        </>
      )}
    </section>
  );
}
