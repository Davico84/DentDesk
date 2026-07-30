import { Link, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { patientDetailPath } from "@/routes/paths";
import { ClinicalHistoryForm } from "@/modules/clinical-history/components/ClinicalHistoryForm";

/**
 * Pantalla de la Historia Clínica de un paciente: hoja de composición (docs/frontend.md §1, §4.6).
 * Solo monta la feature; la lógica y los componentes viven en `modules/clinical-history/`.
 */
export function ClinicalHistoryScreen() {
  const params = useParams();
  const patientId = Number(params.id);

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-3xl flex-col gap-6 p-6">
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

      <ClinicalHistoryForm patientId={patientId} />
    </main>
  );
}
