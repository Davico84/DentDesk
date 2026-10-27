import { PageContainer } from "@/modules/core/components/PageContainer";
import { useMyCredentialQuery } from "../hooks/useProfessionalCredential";
import { ClinicalCapacitySummary } from "./ClinicalCapacitySummary";
import { ProfessionalCredentialForm } from "./ProfessionalCredentialForm";
import { ProfessionalCredentialSkeleton } from "./ProfessionalCredentialSkeleton";
import { StaffListError } from "./StaffListError";

/**
 * Feature "Mi credencial profesional" (update-staff-professional-credential D7): cualquier miembro declara aquí,
 * para sí mismo, su colegiatura y hasta cuándo está habilitado. Vale en todas sus clínicas; lo que cambia por
 * clínica es la capacidad (la clínica puede retirarla).
 */
export function ProfessionalCredentialPage() {
  const query = useMyCredentialQuery();

  return (
    <PageContainer className="max-w-3xl">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Mi credencial profesional</h1>
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
          <section aria-label="Datos de la credencial" className="rounded-2xl border bg-card p-5 shadow-xs">
            <ProfessionalCredentialForm credential={query.data?.credential} />
          </section>
          <p className="text-xs text-muted-foreground">
            Dentdesk no verifica estos datos con el Colegio Odontológico: se guardan tal como los declaras.
          </p>
        </>
      )}
    </PageContainer>
  );
}
