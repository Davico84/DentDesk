import { HeartPulse } from "lucide-react";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import { useActiveBackground } from "@/modules/clinical-history/hooks/useConsultationNotesApi";
import { BACKGROUND_SOURCE_LABELS, backgroundCategoryOptions } from "./noteOptions";

/**
 * Antecedentes vigentes del paciente (spec: <i>Antecedentes vigentes del paciente</i>), por
 * categoría y con su origen visible: lo de la ficha se lee distinto de lo que firmó un profesional.
 */
export function ActiveBackgroundCard({ patientId }: { patientId: number }) {
  const { data } = useActiveBackground(patientId);
  const items = data?.items ?? [];

  return (
    <section className="rounded-2xl border bg-card p-5 shadow-sm" aria-labelledby="active-background-title">
      <h2 id="active-background-title" className="mb-1 flex items-center gap-2 text-sm font-semibold">
        <HeartPulse className="size-4" aria-hidden="true" />
        Antecedentes vigentes
      </h2>
      <p className="mb-4 text-xs text-muted-foreground">
        Lo que hay que tener presente antes de tratar. Lo nuevo de una consulta aparece al sellarla.
      </p>
      <div className="flex flex-col gap-4">
        {backgroundCategoryOptions.map((category) => {
          const inCategory = items.filter((item) => item.category === category.value);
          return (
            <div key={category.value}>
              <h3 className="mb-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">{category.label}</h3>
              {inCategory.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nada registrado.</p>
              ) : (
                <ul className="flex flex-col gap-1.5">
                  {inCategory.map((item, index) => (
                    <li key={`${item.source}-${item.entryId ?? index}`} className="text-sm">
                      <span className="font-medium">{item.description}</span>
                      <span className="ml-2 text-xs text-muted-foreground">
                        {BACKGROUND_SOURCE_LABELS[item.source ?? ""]}
                        {item.recordedAt ? ` · ${dateLabel(item.recordedAt)}` : null}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
}

