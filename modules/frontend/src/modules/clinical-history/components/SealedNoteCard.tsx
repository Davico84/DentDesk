import { useState } from "react";
import { Lock, PenLine } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import type { ConsultationNoteResponse } from "@/modules/core/services/generated/model";
import { useSessionStore } from "@/store/useSessionStore";
import { CorrectionDialog } from "./CorrectionDialog";
import { DiagnosisList } from "./DiagnosisList";
import { NOTE_TEXT_FIELDS, backgroundCategoryOptions } from "./noteOptions";
import { labelOf } from "./diagnosisOptions";

/**
 * Una nota sellada, en lectura (spec: <i>Sellado irreversible de la nota</i>). Cada corrección se
 * muestra bajo su campo, sin tapar el original (design.md D6). Solo quien la selló ve "Corregir".
 */
export function SealedNoteCard({ patientId, note }: { patientId: number; note: ConsultationNoteResponse }) {
  const user = useSessionStore.use.user();
  const [correcting, setCorrecting] = useState(false);
  const filled = NOTE_TEXT_FIELDS.filter(({ key }) => note[key]?.trim());
  const canCorrect = user?.id != null && user.id === note.sealedBy && filled.length > 0;

  return (
    <article className="flex flex-col gap-4 rounded-2xl border bg-card p-5 shadow-xs" aria-label={`Consulta del ${dateLabel(note.attendedAt)}`}>
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold">Consulta del {dateLabel(note.attendedAt)}</h3>
          <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Lock className="size-3" aria-hidden="true" />
            Sellada el {dateLabel(note.sealedAt)}
            {note.nextAppointmentOn ? ` · Próxima cita: ${dateLabel(note.nextAppointmentOn)}` : null}
          </p>
        </div>
        {canCorrect ? (
          <Button variant="outline" size="sm" onClick={() => setCorrecting(true)}>
            <PenLine className="mr-2 size-4" />
            Corregir
          </Button>
        ) : null}
      </header>

      <dl className="flex flex-col gap-3 text-sm">
        {filled.map(({ field, key, label }) => {
          const corrections = (note.corrections ?? []).filter((c) => c.field === field);
          return (
            <div key={key}>
              <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
              <dd className="whitespace-pre-line">{note[key]}</dd>
              {corrections.map((c) => (
                <dd key={c.id} className="mt-1.5 rounded-lg border border-warning/30 bg-warning/5 px-3 py-1.5">
                  <span className="block text-xs font-medium text-warning">
                    Corrección del {dateLabel(c.createdAt)} — {c.reason}
                  </span>
                  <span className="whitespace-pre-line">{c.correctedText}</span>
                </dd>
              ))}
            </div>
          );
        })}
      </dl>

      <div>
        <h4 className="mb-2 text-xs font-medium text-muted-foreground">Diagnóstico (CIE10)</h4>
        <DiagnosisList diagnoses={note.diagnoses ?? []} />
      </div>

      {(note.backgroundEntries?.length ?? 0) > 0 ? (
        <div>
          <h4 className="mb-2 text-xs font-medium text-muted-foreground">Antecedentes registrados</h4>
          <ul className="flex flex-col gap-1 text-sm">
            {note.backgroundEntries!.map((entry) => (
              <li key={entry.id}>
                <span className="font-medium">{entry.kind === "ALTA" ? "Alta" : "Baja"}</span>
                <span className="ml-2 text-muted-foreground">{labelOf(backgroundCategoryOptions, entry.category)}</span>
                {entry.description ? <span className="ml-2">{entry.description}</span> : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {canCorrect ? (
        <CorrectionDialog
          patientId={patientId}
          noteId={note.id as number}
          fields={filled.map(({ field, label }) => ({ value: field, label }))}
          open={correcting}
          onOpenChange={setCorrecting}
        />
      ) : null}
    </article>
  );
}
