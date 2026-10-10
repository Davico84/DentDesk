import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ClipboardList, NotebookPen, Pencil, Smile, Users } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { ConfirmDialog } from "@/modules/core/ui/confirm-dialog";
import { InfoCard, InfoRow } from "@/modules/core/components/InfoCard";
import { PATHS, clinicalHistoryPath, consultationNotesPath, dentalChartPath, patientEditPath } from "@/routes/paths";
import {
  useDeactivatePatient,
  usePatient,
  useReactivatePatient,
} from "@/modules/patients/hooks/usePatientsApi";
import { PatientAvatar } from "./PatientAvatar";
import { PatientStatusBadge } from "./PatientStatusBadge";
import { PatientMinorBadge } from "./PatientMinorBadge";
import { PatientDetailSkeleton } from "./PatientDetailSkeleton";
import { PatientNotFound } from "./PatientNotFound";
import { ageLabel, documentLabel, joinOrDash } from "./patientDisplay";
import { guardianRelationshipOptions, labelOf, sexOptions } from "./patientOptions";
import { PageContainer } from "@/modules/core/components/PageContainer";

/** Feature de la consulta de una ficha: datos, y acciones de edición/baja/reactivación. */
export function PatientDetail() {
  const params = useParams();
  const id = Number(params.id);

  const { data: patient, isLoading, isError } = usePatient(id);
  const deactivate = useDeactivatePatient();
  const reactivate = useReactivatePatient();
  const [confirmingBaja, setConfirmingBaja] = useState(false);

  if (isLoading) return <PatientDetailSkeleton />;
  if (isError || !patient) return <PatientNotFound />;

  const inactive = patient.status === "INACTIVE";

  return (
    <PageContainer>
      <div className="flex items-center gap-2">
        <Link
          to={PATHS.PATIENTS}
          aria-label="Volver al listado"
          className={buttonVariants({ variant: "ghost", size: "icon" })}
        >
          <ArrowLeft className="size-4" />
        </Link>
        <span className="text-sm text-muted-foreground">Pacientes</span>
      </div>

      {/*
        Dos filas: la identidad arriba y las acciones abajo, cada grupo con flex-wrap. Con cinco
        acciones en una sola fila sin envolver, la tarjeta desbordaba (update-clinical-history 6.9).
        Documentos clínicos a la izquierda; acciones sobre la ficha a la derecha. Los tres documentos
        con el mismo estilo: la ficha es un punto de partida, no una navegación con uno "elegido".
      */}
      <header className="flex flex-col gap-4 rounded-2xl border bg-card p-5 shadow-xs">
        <div className="flex items-center gap-4">
          <PatientAvatar fullName={patient.fullName!} muted={inactive} className="size-14 text-lg" />
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold tracking-tight">{patient.fullName}</h1>
              {patient.minor ? <PatientMinorBadge /> : null}
            </div>
            <div className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
              <PatientStatusBadge status={patient.status} />
              <span>· {ageLabel(patient.birthDate!)}</span>
            </div>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <nav aria-label="Documentos clínicos" className="flex flex-wrap items-center gap-2">
            <Link to={clinicalHistoryPath(id)} className={buttonVariants({ variant: "outline", size: "lg" })}>
              <ClipboardList className="mr-2 size-4" />
              Historia Clínica
            </Link>
            <Link to={consultationNotesPath(id)} className={buttonVariants({ variant: "outline", size: "lg" })}>
              <NotebookPen className="mr-2 size-4" />
              Consultas
            </Link>
            <Link to={dentalChartPath(id)} className={buttonVariants({ variant: "outline", size: "lg" })}>
              <Smile className="mr-2 size-4" />
              Odontograma
            </Link>
          </nav>
          <div className="flex flex-wrap items-center gap-2">
            <Link to={patientEditPath(id)} className={buttonVariants({ variant: "ghost", size: "lg" })}>
              <Pencil className="mr-2 size-4" />
              Editar
            </Link>
            {inactive ? (
              <Button size="lg" disabled={reactivate.isPending} onClick={() => reactivate.mutate(id)}>
                Reactivar
              </Button>
            ) : (
              <Button variant="ghost" size="lg" disabled={deactivate.isPending} onClick={() => setConfirmingBaja(true)}>
                Dar de baja
              </Button>
            )}
          </div>
        </div>
      </header>

      <InfoCard title="Datos personales">
        <InfoRow label="Documento" value={documentLabel(patient)} />
        <InfoRow label="Fecha de nacimiento" value={patient.birthDate} />
        <InfoRow label="Sexo" value={labelOf(sexOptions, patient.sex)} />
      </InfoCard>

      <InfoCard title="Contacto">
        <InfoRow label="Teléfono" value={patient.phone} />
        <InfoRow label="Correo" value={patient.email} />
        <InfoRow label="Dirección" value={patient.address} />
      </InfoCard>

      {/*
        Aviso NO bloqueante (update-patients D1): el teléfono es legítimamente compartido entre
        pacientes de una misma familia. No es un error — solo información.
      */}
      {patient.phoneSharedWithOtherPatients ? (
        <div className="flex items-center gap-3 rounded-2xl border bg-accent px-4 py-3 text-sm text-accent-foreground">
          <Users className="size-4 shrink-0" />
          <span>Este teléfono también está registrado a nombre de otro paciente de la clínica.</span>
        </div>
      ) : null}

      {patient.guardianName || patient.guardianPhone ? (
        <InfoCard title="Responsable">
          <InfoRow label="Nombre" value={patient.guardianName} />
          <InfoRow label="Parentesco" value={labelOf(guardianRelationshipOptions, patient.guardianRelationship)} />
          <InfoRow label="Teléfono" value={patient.guardianPhone} />
          <InfoRow label="Documento" value={patient.guardianDocument} />
        </InfoCard>
      ) : null}

      <InfoCard title="Contacto de emergencia">
        <InfoRow label="Nombre" value={patient.emergencyContactName} />
        <InfoRow label="Teléfono" value={patient.emergencyContactPhone} />
        <InfoRow label="Parentesco" value={patient.emergencyContactRelationship} />
      </InfoCard>

      <InfoCard title={patient.anamnesisFrozen ? "Anamnesis al dar de alta" : "Anamnesis"}>
        <InfoRow label="Alergias" value={joinOrDash(patient.allergies)} />
        <InfoRow label="Condiciones" value={joinOrDash(patient.conditions)} />
        <InfoRow label="Medicación actual" value={joinOrDash(patient.currentMedication)} />
        {patient.anamnesisFrozen ? (
          <p className="text-xs text-muted-foreground">
            Registrada al dar de alta, sin autoría clínica. Ya no se edita: lo nuevo se registra en la{" "}
            <Link to={consultationNotesPath(id)} className="text-primary underline-offset-4 hover:underline">
              consulta
            </Link>
            .
          </p>
        ) : null}
      </InfoCard>

      {/*
        Dar de baja ARCHIVA (design.md D4): es reversible y no destruye nada, así que NO usa el
        rojo destructivo — sería exagerar la consecuencia. El texto dice qué pasa de verdad, en vez
        de un "¿estás seguro?" que no informa.
      */}
      <ConfirmDialog
        open={confirmingBaja}
        onOpenChange={setConfirmingBaja}
        title={`¿Dar de baja a ${patient.fullName}?`}
        description="Dejará de aparecer en el listado, pero su ficha y su historia clínica se conservan. Puedes reactivarlo cuando vuelva."
        confirmLabel="Dar de baja"
        pending={deactivate.isPending}
        onConfirm={() => deactivate.mutate(id, { onSuccess: () => setConfirmingBaja(false) })}
      />
    </PageContainer>
  );
}
