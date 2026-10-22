import { useState } from "react";
import { ChevronDown, Loader2, Plus, Stethoscope, X } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import type { ConsultationNoteResponse, DentalChartFindingResponse } from "@/modules/core/services/generated/model";
import { useDentalChart, useDentalFindingCatalog } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { isSeenRed } from "@/modules/dental-chart/utils/findingColor";
import { describeFinding } from "@/modules/dental-chart/utils/findingDisplay";
import { useAttendFinding, useRemoveAttendedFinding } from "@/modules/clinical-history/hooks/useConsultationNotesApi";

/**
 * Hallazgos del odontograma en la nota abierta (spec: <i>Hallazgos del odontograma atendidos en la
 * nota</i>, design D6): arriba los que se ven rojos como pendientes —con la fecha si ya se atendieron
 * en una consulta sellada—, luego los atendidos en esta consulta y, plegados, los demás. Marcar uno
 * lo registra en la nota y agrega su descripción al Examen del formulario (sin guardarlo); "Proponer
 * diagnóstico" precarga el formulario de diagnóstico de la nota (design D5).
 */
export function NoteFindingsPanel({
  patientId,
  note,
  notes,
  onAttended,
  onPropose,
}: {
  patientId: number;
  note: ConsultationNoteResponse;
  /** Todas las notas del paciente: de las selladas sale "Atendido el …". */
  notes: ConsultationNoteResponse[];
  onAttended: (description: string) => void;
  onPropose: (finding: DentalChartFindingResponse, description: string) => void;
}) {
  const noteId = note.id as number;
  const { data: chart, isLoading } = useDentalChart(patientId);
  const { data: catalog } = useDentalFindingCatalog();
  const attend = useAttendFinding(patientId, noteId);
  const removeAttended = useRemoveAttendedFinding(patientId, noteId);
  const [showOthers, setShowOthers] = useState(false);

  const findings = chart?.findings ?? [];
  const attendedHere = note.attendedFindings ?? [];
  const attendedIds = new Set(attendedHere.map((a) => a.findingId));
  const pending = findings.filter((f) => isSeenRed(f, catalog) && !attendedIds.has(f.id));
  const others = findings.filter((f) => !isSeenRed(f, catalog) && !attendedIds.has(f.id));

  /** La última consulta sellada que atendió el hallazgo. */
  const lastAttendedOn = (findingId: number | undefined) =>
    notes
      .filter((n) => n.status === "SELLADA" && n.attendedFindings?.some((a) => a.findingId === findingId))
      .map((n) => n.attendedAt)
      .sort()
      .at(-1);

  const markAttended = (f: DentalChartFindingResponse) => {
    const description = describeFinding(f, catalog);
    attend.mutate({ findingId: f.id as number, description }, { onSuccess: () => onAttended(description) });
  };

  const findingRow = (f: DentalChartFindingResponse) => {
    const attendedOn = lastAttendedOn(f.id);
    return (
      <li key={f.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
        <span className="min-w-0">
          {describeFinding(f, catalog)}
          {attendedOn ? (
            <span className="ml-2 text-xs text-muted-foreground">Atendido el {dateLabel(attendedOn)}</span>
          ) : null}
        </span>
        <Button type="button" variant="outline" size="sm" disabled={attend.isPending} onClick={() => markAttended(f)}>
          <Plus className="mr-1.5 size-3.5" />
          Atender
        </Button>
      </li>
    );
  };

  if (isLoading) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (findings.length === 0) {
    return <p className="text-sm text-muted-foreground">Sin hallazgos en el odontograma.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h4 className="text-xs font-medium text-muted-foreground">Pendientes</h4>
        {pending.length > 0 ? (
          <ul aria-label="Hallazgos pendientes" className="divide-y">
            {pending.map(findingRow)}
          </ul>
        ) : (
          <p className="py-2 text-sm text-muted-foreground">No hay hallazgos pendientes sin atender en esta consulta.</p>
        )}
      </div>

      {attendedHere.length > 0 ? (
        <div>
          <h4 className="text-xs font-medium text-muted-foreground">Atendidos en esta consulta</h4>
          <ul aria-label="Hallazgos atendidos en esta consulta" className="divide-y">
            {attendedHere.map((a) => {
              const finding = findings.find((f) => f.id === a.findingId);
              return (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                  <span className="min-w-0">{a.description}</span>
                  <span className="flex items-center gap-1">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={!finding}
                      onClick={() => finding && onPropose(finding, a.description ?? describeFinding(finding, catalog))}
                    >
                      <Stethoscope className="mr-1.5 size-3.5" />
                      Proponer diagnóstico
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={removeAttended.isPending}
                      onClick={() => removeAttended.mutate(a.id as number)}
                    >
                      <X className="mr-1.5 size-3.5" />
                      Quitar
                    </Button>
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}

      {others.length > 0 ? (
        <div>
          <Button
            type="button"
            variant="link"
            size="sm"
            className="h-auto px-0"
            aria-expanded={showOthers}
            onClick={() => setShowOthers((v) => !v)}
          >
            <ChevronDown className={`mr-1 size-4 transition-transform motion-reduce:transition-none ${showOthers ? "rotate-180" : ""}`} />
            {showOthers ? "Ocultar los demás hallazgos" : `Ver los demás hallazgos (${others.length})`}
          </Button>
          {showOthers ? (
            <ul aria-label="Otros hallazgos del odontograma" className="divide-y">
              {others.map(findingRow)}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
