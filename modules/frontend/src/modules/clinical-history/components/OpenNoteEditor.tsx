import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { CheckCircle2, Circle, CircleAlert, ClipboardList, HeartPulse, ListPlus, Loader2, ScanSearch, Stethoscope, Syringe } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { ConfirmDialog } from "@/modules/core/ui/confirm-dialog";
import { FormField, FormSection, TextareaField } from "@/modules/core/ui/form-field";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import {
  DiagnosisRequestUbicacion,
  type ConsultationNoteResponse,
  type DentalChartFindingResponse,
} from "@/modules/core/services/generated/model";
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
import { diagnosisLocationOf } from "@/modules/dental-chart/utils/findingDisplay";
import { useCie10Suggestions } from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import { BackgroundEntriesPanel } from "./BackgroundEntriesPanel";
import { DiagnosisForm } from "./DiagnosisForm";
import { DiagnosisList } from "./DiagnosisList";
import { NoteFindingsPanel } from "./NoteFindingsPanel";
import { NOTE_CHECKLIST_LABELS, NOTE_TEXT_FIELDS, OPTIONAL_TEXT_KEYS, labelOfField, type NoteTextKey } from "./noteOptions";

/**
 * La nota abierta (spec: <i>Nota abierta editable y descartable solo vacía</i> y <i>Sellado
 * irreversible de la nota</i>): borrador que se guarda en varias sesiones; se descarta solo si está
 * vacía; al sellarse queda fija. Lo que pide el sellado se valida sobre lo YA guardado.
 */
export function OpenNoteEditor({
  patientId,
  note,
  notes,
}: {
  patientId: number;
  note: ConsultationNoteResponse;
  /** Todas las notas del paciente (para "Atendido el …" en los hallazgos). */
  notes: ConsultationNoteResponse[];
}) {
  const noteId = note.id as number;
  const updateNote = useUpdateNote(patientId, noteId);
  const sealNote = useSealNote(patientId, noteId);
  const discardNote = useDiscardNote(patientId, noteId);
  const addDiagnosis = useAddNoteDiagnosis(patientId, noteId);
  const removeDiagnosis = useRemoveNoteDiagnosis(patientId, noteId);
  const [confirming, setConfirming] = useState<"seal" | "discard" | null>(null);
  // Diagnóstico propuesto desde un hallazgo atendido (design D5): precarga el formulario de diagnóstico
  // de la nota —uno solo en la página, sin ids repetidos— hasta agregarlo o descartar la propuesta.
  const [proposal, setProposal] = useState<{ finding: DentalChartFindingResponse; description: string } | null>(null);
  const { data: suggestions } = useCie10Suggestions(proposal?.finding.type, proposal?.finding.subtype);
  const diagnosisRef = useRef<HTMLElement>(null);
  const proposedLocation = proposal ? diagnosisLocationOf(proposal.finding) : null;

  const {
    register,
    handleSubmit,
    getValues,
    setValue,
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
    (note.backgroundEntries?.length ?? 0) === 0 &&
    (note.attendedFindings?.length ?? 0) === 0;

  // Marcar un hallazgo agrega su línea al Examen como cambio sin guardar (design D3): "Sellar" queda
  // bloqueado hasta "Guardar avance", así nadie cree que marcarlo ya guardó el examen.
  const appendToExamination = (line: string) => {
    const current = getValues("examination") ?? "";
    setValue("examination", current.trim() ? `${current.trimEnd()}
${line}` : line, { shouldDirty: true });
  };

  const onSubmit = (values: ConsultationNoteValues) => updateNote.mutate(toNoteRequest(values));
  const save = handleSubmit(onSubmit);

  // Lo opcional va plegado; se abre solo si ya tiene algo escrito o un error (FormSection lo fuerza).
  const optionalFilled = OPTIONAL_TEXT_KEYS.some((key) => note[key]?.trim());
  const optionalError = OPTIONAL_TEXT_KEYS.some((key) => errors[key]);
  const textField = (key: NoteTextKey, opts: { required?: boolean } = {}) => (
    <TextareaField
      key={key}
      label={labelOfField(key)}
      name={key}
      register={register}
      error={errors[key]}
      rows={2}
      autoGrow
      required={opts.required}
      wide
    />
  );

  return (
    <article className="flex flex-col gap-5 rounded-2xl border-2 border-primary/30 bg-background p-5" aria-label="Nota de consulta abierta">
      <header>
        <h2 className="text-base font-semibold">Consulta del {dateLabel(note.attendedAt)}</h2>
        <p className="text-xs text-muted-foreground">
          Borrador: se puede editar hasta sellarla. Los campos con <span className="text-destructive">*</span> son
          obligatorios para sellar; lo demás es opcional.
        </p>
      </header>

      {/* Orden de la atención: motivo → hallazgos → examen → diagnóstico → tratamiento. Los campos de texto no van en un
          <form> propio: entre ellos hay secciones con sus propios formularios (diagnóstico), que no se pueden anidar. */}
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
        {textField("reason", { required: true })}
      </FormSection>

      <FormSection
        title="Hallazgos del odontograma"
        description="Marca lo que atiendes hoy: queda registrado en la nota y su descripción se agrega al examen."
        icon={<ScanSearch className="size-4" />}
      >
        <div className="sm:col-span-2">
          <NoteFindingsPanel
            patientId={patientId}
            note={note}
            notes={notes}
            onAttended={appendToExamination}
            onPropose={(finding, description) => {
              setProposal({ finding, description });
              diagnosisRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
            }}
          />
        </div>
      </FormSection>

      <FormSection title="Examen" icon={<ClipboardList className="size-4" />}>
        {textField("examination", { required: true })}
      </FormSection>

      <section ref={diagnosisRef} className="scroll-mt-4 rounded-2xl border bg-card p-5 shadow-xs">
        <h3 className="mb-4 text-sm font-semibold">
          Diagnóstico (CIE10) <span className="text-destructive">*</span>
          <span className="ml-1 font-normal text-muted-foreground">(al menos uno)</span>
        </h3>
        <DiagnosisList
          diagnoses={note.diagnoses ?? []}
          onRemove={(id) => removeDiagnosis.mutate(id)}
          removing={removeDiagnosis.isPending}
        />
        <p className="mt-3 text-xs text-muted-foreground">
          &quot;Repetido&quot; solo vale para un código ya diagnosticado antes al paciente.
        </p>
        <div className="mt-4 border-t pt-4">
          {proposal ? (
            <div role="status" className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border bg-muted/40 px-3 py-2 text-sm">
              <span>
                Propuesto desde: <span className="font-medium">{proposal.description}</span>
              </span>
              <Button type="button" variant="ghost" size="sm" onClick={() => setProposal(null)}>
                Descartar propuesta
              </Button>
            </div>
          ) : null}
          <DiagnosisForm
            key={proposal ? `proposal-${proposal.finding.id}` : "plain"}
            pending={addDiagnosis.isPending}
            suggestions={proposal ? (suggestions ?? []) : []}
            initial={
              proposedLocation?.ubicacion === "PIEZA"
                ? { ubicacion: DiagnosisRequestUbicacion.PIEZA, fdiNumber: proposedLocation.fdiNumber }
                : proposedLocation
                  ? { ubicacion: DiagnosisRequestUbicacion.GENERAL }
                  : undefined
            }
            onAdd={(request, onSuccess) =>
              addDiagnosis.mutate(request, {
                onSuccess: () => {
                  onSuccess();
                  setProposal(null);
                },
              })
            }
          />
        </div>
      </section>

      <FormSection title="Tratamiento" icon={<Syringe className="size-4" />}>
        {textField("treatment", { required: true })}
      </FormSection>

      <FormSection
        title="Más detalles (opcional)"
        description="Tiempo de enfermedad, síntomas y signos, tratamiento recibido, funciones biológicas, exámenes auxiliares y referencia."
        icon={<ListPlus className="size-4" />}
        collapsible
        defaultOpen={optionalFilled}
        hasError={optionalError}
      >
        {OPTIONAL_TEXT_KEYS.map((key) => textField(key))}
      </FormSection>

      <FormSection
        title="Antecedentes nuevos (opcional)"
        description="Una alergia, enfermedad o medicación que aparece hoy: lo escrito antes no se modifica."
        icon={<HeartPulse className="size-4" />}
        collapsible
        defaultOpen={(note.backgroundEntries?.length ?? 0) > 0}
      >
        <div className="sm:col-span-2">
          <BackgroundEntriesPanel patientId={patientId} noteId={noteId} entries={note.backgroundEntries ?? []} />
        </div>
      </FormSection>

      {/* Barra fija: qué falta para sellar y las acciones, siempre a la vista aunque la nota sea larga. */}
      <footer className="sticky bottom-0 z-10 -mx-5 -mb-5 flex flex-col gap-3 rounded-b-2xl border-t bg-background px-5 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
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
          {isDirty ? (
            <p className="flex items-center gap-1.5 text-xs text-warning">
              <CircleAlert className="size-3.5" aria-hidden="true" />
              Tienes cambios sin guardar: se sella lo guardado.
            </p>
          ) : null}
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          {empty ? (
            <Button variant="ghost" onClick={() => setConfirming("discard")}>
              Descartar nota vacía
            </Button>
          ) : null}
          <Button type="button" variant="outline" disabled={updateNote.isPending} onClick={save}>
            {updateNote.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            Guardar avance
          </Button>
          <Button className="rounded-xl" disabled={isDirty || missing.length > 0} onClick={() => setConfirming("seal")}>
            Sellar nota
          </Button>
        </div>
      </footer>

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
