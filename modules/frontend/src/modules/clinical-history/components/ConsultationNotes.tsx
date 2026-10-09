import { Link } from "react-router-dom";
import { FileText, Info, Loader2, Plus } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { clinicalHistoryPath } from "@/routes/paths";
import { useClinicalHistoryEnablement } from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import { useConsultationNotes, useOpenNote } from "@/modules/clinical-history/hooks/useConsultationNotesApi";
import { usePatient } from "@/modules/patients/hooks/usePatientsApi";
import { ActiveBackgroundCard } from "./ActiveBackgroundCard";
import { OpenNoteEditor } from "./OpenNoteEditor";
import { SealedNoteCard } from "./SealedNoteCard";

const NOTICE = "flex items-center gap-2 rounded-2xl border bg-accent px-4 py-3 text-sm text-accent-foreground";

/**
 * Notas de consulta del paciente (`update-clinical-history`, ADR-0020): la nota abierta arriba —una
 * olvidada bloquea abrir otra, así que tiene que estar a la vista—, después las selladas de la más
 * reciente a la más antigua, y al costado los antecedentes vigentes.
 */
export function ConsultationNotes({ patientId }: { patientId: number }) {
  const { data: patient } = usePatient(patientId);
  const { data: enablement, isLoading: loadingEnablement } = useClinicalHistoryEnablement(patientId);
  const { data: notes, isLoading: loadingNotes } = useConsultationNotes(patientId);
  const openNote = useOpenNote(patientId);

  if (loadingEnablement || loadingNotes) {
    return (
      <div className="flex justify-center py-12">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const historySealed = enablement?.status === "COMPLETED";
  const inactive = patient?.status === "INACTIVE";
  const open = notes?.find((n) => n.status === "ABIERTA");
  const sealed = (notes ?? []).filter((n) => n.status === "SELLADA").reverse();

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">Notas de consulta</h1>
          <p className="text-sm text-muted-foreground">{patient?.fullName}</p>
        </div>
        {historySealed && !inactive && !open ? (
          <Button
            className="rounded-xl"
            disabled={openNote.isPending}
            onClick={() => openNote.mutate(new Date().toISOString())}
          >
            {openNote.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : <Plus className="mr-2 size-4" />}
            Nueva nota de consulta
          </Button>
        ) : null}
      </div>

      {!historySealed ? (
        <p className={NOTICE}>
          <Info className="size-4 shrink-0" aria-hidden="true" />
          <span>
            Las notas de consulta empiezan cuando la Historia Clínica está completa.{" "}
            <Link to={clinicalHistoryPath(patientId)} className={buttonVariants({ variant: "link", size: "sm" })}>
              Ir a la Historia Clínica
            </Link>
          </span>
        </p>
      ) : null}
      {inactive ? (
        <p className={NOTICE}>
          <Info className="size-4 shrink-0" aria-hidden="true" />
          El paciente está dado de baja: reactívalo para registrar una consulta.
        </p>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <div className="flex flex-col gap-5">
          {open ? <OpenNoteEditor key={open.id} patientId={patientId} note={open} /> : null}
          {sealed.map((note) => (
            <SealedNoteCard key={note.id} patientId={patientId} note={note} />
          ))}
          {!open && sealed.length === 0 && historySealed ? (
            <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed p-10 text-center text-sm text-muted-foreground">
              <FileText className="size-6" aria-hidden="true" />
              Todavía no hay consultas después de la admisión.
            </div>
          ) : null}
        </div>
        {historySealed ? <ActiveBackgroundCard patientId={patientId} /> : null}
      </div>
    </div>
  );
}

