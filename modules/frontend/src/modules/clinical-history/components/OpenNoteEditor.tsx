import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { CheckCircle2, Circle, CircleAlert, ClipboardList, HeartPulse, Loader2, Stethoscope } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { ConfirmDialog } from "@/modules/core/ui/confirm-dialog";
import { FormField, FormSection, TextareaField } from "@/modules/core/ui/form-field";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import type { ConsultationNoteResponse } from "@/modules/core/services/generated/model";
import {
  consultationNoteSchema,
  missingNoteFields,
  noteDefaults,
  toNoteRequest,
  type ConsultationNoteValues,
  type NoteChecklistField,
} from "@/modules/clinical-history/schemas/consultationNote";
import {
  useAddNoteDiagnosis,
  useDiscardNote,
  useRemoveNoteDiagnosis,
  useSealNote,
  useUpdateNote,
} from "@/modules/clinical-history/hooks/useConsultationNotesApi";
import { BackgroundEntriesPanel } from "./BackgroundEntriesPanel";
import { DiagnosisForm } from "./DiagnosisForm";
import { DiagnosisList } from "./DiagnosisList";
import { NOTE_CHECKLIST_LABELS, NOTE_TEXT_FIELDS } from "./noteOptions";

/**
 * La nota abierta (spec: <i>Nota abierta editable y descartable solo vacía</i> y <i>Sellado
 * irreversible de la nota</i>): borrador que se guarda en varias sesiones; se descarta solo si está
 * vacía; al sellarse queda fija. Lo que pide el sellado se valida sobre lo YA guardado.
 */
export function OpenNoteEditor({ patientId, note }: { patientId: number; note: ConsultationNoteResponse }) {
  const noteId = note.id as number;
  const updateNote = useUpdateNote(patientId, noteId);
  const sealNote = useSealNote(patientId, noteId);
  const discardNote = useDiscardNote(patientId, noteId);
  const addDiagnosis = useAddNoteDiagnosis(patientId, noteId);
  const removeDiagnosis = useRemoveNoteDiagnosis(patientId, noteId);
  const [confirming, setConfirming] = useState<"seal" | "discard" | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<ConsultationNoteValues>({
    resolver: zodResolver(consultationNoteSchema),
    mode: "onTouched",
    values: noteDefaults(note),
  });

  const missing = missingNoteFields(note);
  const empty =
    NOTE_TEXT_FIELDS.every(({ key }) => !note[key]?.trim()) &&
    !note.nextAppointmentOn &&
    (note.diagnoses?.length ?? 0) === 0 &&
    (note.backgroundEntries?.length ?? 0) === 0;

  const onSubmit = (values: ConsultationNoteValues) => updateNote.mutate(toNoteRequest(values));

  return (
    <article className="flex flex-col gap-5 rounded-2xl border-2 border-primary/30 bg-background p-5" aria-label="Nota de consulta abierta">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold">Consulta del {dateLabel(note.attendedAt)}</h2>
          <p className="text-xs text-muted-foreground">Borrador: se puede editar hasta sellarla.</p>
        </div>
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs" aria-label="Lo que falta para sellar">
          {Object.entries(NOTE_CHECKLIST_LABELS).map(([field, label]) => {
            const done = !missing.includes(field as NoteChecklistField);
            return (
              <li key={field} className={`flex items-center gap-1.5 ${done ? "" : "text-muted-foreground"}`}>
                {done ? <CheckCircle2 className="size-3.5 text-success" /> : <Circle className="size-3.5" />}
                {label}
              </li>
            );
          })}
        </ul>
      </header>

      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
        <FormSection title="Atención" icon={<Stethoscope className="size-4" />}>
          <FormField
            label="Fecha y hora de la atención"
            name="attendedAt"
            type="datetime-local"
            register={register}
            error={errors.attendedAt}
            hint="Cuándo atendiste, aunque lo escribas después."
            required
          />
          <FormField label="Próxima cita" name="nextAppointmentOn" type="date" register={register} error={errors.nextAppointmentOn} />
          {NOTE_TEXT_FIELDS.slice(0, 5).map(({ key, label }) => (
            <TextareaField key={key} label={label} name={key} register={register} error={errors[key]} wide />
          ))}
        </FormSection>

        <FormSection title="Examen y tratamiento" icon={<ClipboardList className="size-4" />}>
          {NOTE_TEXT_FIELDS.slice(5).map(({ key, label }) => (
            <TextareaField key={key} label={label} name={key} register={register} error={errors[key]} wide />
          ))}
        </FormSection>

        <div className="flex items-center justify-end gap-3">
          {isDirty ? (
            <p className="flex items-center gap-1.5 text-xs text-warning">
              <CircleAlert className="size-3.5" aria-hidden="true" />
              Tienes cambios sin guardar
            </p>
          ) : null}
          <Button type="submit" variant="outline" disabled={updateNote.isPending}>
            {updateNote.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            Guardar avance
          </Button>
        </div>
      </form>

      <section className="rounded-2xl border bg-card p-5 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold">Diagnóstico (CIE10)</h3>
        <DiagnosisList
          diagnoses={note.diagnoses ?? []}
          onRemove={(id) => removeDiagnosis.mutate(id)}
          removing={removeDiagnosis.isPending}
        />
        <p className="mt-3 text-xs text-muted-foreground">
          &quot;Repetido&quot; solo vale para un código ya diagnosticado antes al paciente.
        </p>
        <div className="mt-4 border-t pt-4">
          <DiagnosisForm
            pending={addDiagnosis.isPending}
            onAdd={(request, onSuccess) => addDiagnosis.mutate(request, { onSuccess })}
          />
        </div>
      </section>

      <section className="rounded-2xl border bg-card p-5 shadow-sm">
        <h3 className="mb-1 flex items-center gap-2 text-sm font-semibold">
          <HeartPulse className="size-4" aria-hidden="true" />
          Antecedentes nuevos
        </h3>
        <p className="mb-4 text-xs text-muted-foreground">
          Una alergia, enfermedad o medicación que aparece hoy se registra aquí: lo escrito antes no se modifica.
        </p>
        <BackgroundEntriesPanel patientId={patientId} noteId={noteId} entries={note.backgroundEntries ?? []} />
      </section>

      <footer className="flex flex-wrap items-center justify-end gap-3">
        {empty ? (
          <Button variant="ghost" onClick={() => setConfirming("discard")}>
            Descartar nota vacía
          </Button>
        ) : null}
        <Button size="lg" className="rounded-xl" disabled={isDirty || missing.length > 0} onClick={() => setConfirming("seal")}>
          Sellar nota
        </Button>
      </footer>
      {isDirty ? (
        <p className="-mt-3 text-right text-xs text-warning">Guarda el avance antes de sellar: se sella lo guardado.</p>
      ) : null}

      <ConfirmDialog
        open={confirming === "seal"}
        onOpenChange={(open) => setConfirming(open ? "seal" : null)}
        title="Sellar nota de consulta"
        description="Al sellarla, la nota queda fija: no se podrá editar ni descartar. Un error material solo podrá corregirse con un registro nuevo, por quien la selló."
        confirmLabel="Sellar"
        pending={sealNote.isPending}
        onConfirm={() => sealNote.mutate(undefined, { onSettled: () => setConfirming(null) })}
      />
      <ConfirmDialog
        open={confirming === "discard"}
        onOpenChange={(open) => setConfirming(open ? "discard" : null)}
        title="Descartar nota vacía"
        description="La nota no tiene contenido. Al descartarla podrás abrir otra con la fecha correcta."
        confirmLabel="Descartar"
        destructive
        pending={discardNote.isPending}
        onConfirm={() => discardNote.mutate(undefined, { onSettled: () => setConfirming(null) })}
      />
    </article>
  );
}
