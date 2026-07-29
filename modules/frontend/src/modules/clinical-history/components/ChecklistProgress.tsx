import { CHECKLIST_LABELS } from "@/modules/clinical-history/schemas/clinicalHistory";

const TOTAL = Object.keys(CHECKLIST_LABELS).length;

/**
 * Progreso del checklist mínimo, visible **mientras se llena** — no solo al intentar sellar
 * (design.md D4). Verlo avanzar en tiempo real evita la sorpresa de abrir el diálogo de sello y
 * recién ahí enterarse de qué falta.
 */
export function ChecklistProgress({ missingFields }: { missingFields: string[] }) {
  const done = TOTAL - missingFields.length;
  const ready = missingFields.length === 0;

  return (
    <div className="flex items-center gap-2 text-xs text-muted-foreground">
      <div className="h-1.5 w-24 overflow-hidden rounded-full bg-muted">
        <div
          className={["h-full rounded-full transition-all duration-300", ready ? "bg-success" : "bg-primary"].join(" ")}
          style={{ width: `${(done / TOTAL) * 100}%` }}
        />
      </div>
      <span>
        {done} de {TOTAL} listos para completar
      </span>
    </div>
  );
}
