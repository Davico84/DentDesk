import { Link, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { patientDetailPath } from "@/routes/paths";
import { ConsultationNotes } from "@/modules/clinical-history/components/ConsultationNotes";
import { PageContainer } from "@/modules/core/components/PageContainer";

/**
 * Pantalla de las notas de consulta de un paciente: hoja de composición (docs/frontend.md §1, §4.6).
 * Solo monta la feature; la lógica vive en `modules/clinical-history/`.
 */
export function ConsultationNotesScreen() {
  const params = useParams();
  const patientId = Number(params.id);

  return (
    <PageContainer>
      <div className="flex items-center gap-2">
        <Link
          to={patientDetailPath(patientId)}
          aria-label="Volver a la ficha del paciente"
          className={buttonVariants({ variant: "ghost", size: "icon" })}
        >
          <ArrowLeft className="size-4" />
        </Link>
        <span className="text-sm text-muted-foreground">Ficha del paciente</span>
      </div>

      <ConsultationNotes patientId={patientId} />
    </PageContainer>
  );
}
