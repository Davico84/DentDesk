import { Link } from "react-router-dom";
import { CheckCircle2, Circle, CircleAlert, Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/modules/core/ui/dialog";
import { CHECKLIST_LABELS } from "@/modules/clinical-history/schemas/clinicalHistory";
import { DENTAL_CHART_CHECKLIST_LABELS } from "@/modules/dental-chart/schemas/dentalChart";

/**
 * Confirmación antes de sellar a COMPLETED (ADR-0011): explícita sobre que la información
 * inicial **queda fija** — es la salvaguarda contra el error de tipeo antes de sellar un
 * documento clínico. Si falta algo del checklist, lo muestra y bloquea el sello (la autoridad
 * real es el servidor; esto solo evita el viaje redundante).
 */
export function CompleteHistoryDialog({
  open,
  onOpenChange,
  missingFields,
  dentalChartMissingFields = [],
  dentalChartHref,
  onConfirm,
  pending,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  missingFields: string[];
  /** Lo que le falta al odontograma inicial, que se sella en la misma operación (ADR-0012 D5). */
  dentalChartMissingFields?: string[];
  dentalChartHref?: string;
  onConfirm: () => void;
  pending: boolean;
}) {
  const ready = missingFields.length === 0 && dentalChartMissingFields.length === 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Completar Historia Clínica</DialogTitle>
          <DialogDescription>
            {ready
              ? "Al confirmar, la información inicial (motivo, antecedentes, examen, diagnóstico y pronóstico) queda fija: no se podrá editar directamente después."
              : "Todavía falta información para poder sellar la Historia Clínica."}
          </DialogDescription>
        </DialogHeader>

        <ul className="flex flex-col gap-1.5 text-sm">
          {Object.entries(CHECKLIST_LABELS).map(([field, label]) => {
            const missing = missingFields.includes(field);
            return (
              <li key={field} className="flex items-center gap-2">
                {missing ? (
                  <Circle className="size-4 shrink-0 text-muted-foreground" />
                ) : (
                  <CheckCircle2 className="size-4 shrink-0 text-success" />
                )}
                <span className={missing ? "text-muted-foreground" : ""}>{label}</span>
              </li>
            );
          })}
        </ul>

        {dentalChartMissingFields.length > 0 ? (
          <div className="flex flex-col gap-1.5 border-t pt-3 text-sm">
            <p className="font-medium">Odontograma inicial</p>
            <ul className="flex flex-col gap-1.5">
              {dentalChartMissingFields.map((field) => (
                <li key={field} className="flex items-center gap-2 text-muted-foreground">
                  <CircleAlert className="size-4 shrink-0" />
                  {DENTAL_CHART_CHECKLIST_LABELS[field] ?? field}
                </li>
              ))}
            </ul>
            {dentalChartHref ? (
              <Link to={dentalChartHref} className="text-primary underline-offset-4 hover:underline">
                Ir al odontograma
              </Link>
            ) : null}
          </div>
        ) : null}

        <DialogFooter className="gap-2 sm:gap-2">
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
            Cancelar
          </Button>
          <Button onClick={onConfirm} disabled={pending || !ready}>
            {pending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            Completar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
