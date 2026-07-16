import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Pencil, UserX } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { ConfirmDialog } from "@/modules/core/ui/confirm-dialog";
import { InfoCard, InfoRow } from "@/modules/core/components/info-card";
import { PATHS, patientEditPath } from "@/routes/paths";
import type { PatientResponse } from "@/modules/core/services/generated/model";
import {
  useDeactivatePatient,
  usePatient,
  useReactivatePatient,
} from "@/modules/patients/hooks/usePatientsApi";
import { PatientAvatar } from "./PatientAvatar";
import { StatusBadge, MinorBadge } from "./PatientBadges";
import { ageLabel } from "./patientDisplay";
import { documentTypeOptions, guardianRelationshipOptions, labelOf, sexOptions } from "./patientOptions";

/** Feature de la consulta de una ficha: datos, y acciones de edición/baja/reactivación. */
export function PatientDetail() {
  const params = useParams();
  const id = Number(params.id);

  const { data: patient, isLoading, isError } = usePatient(id);
  const deactivate = useDeactivatePatient();
  const reactivate = useReactivatePatient();
  const [confirmingBaja, setConfirmingBaja] = useState(false);

  if (isLoading) return <PatientDetailSkeleton />;

  // El error lleva SALIDA (regla error-recovery): un "no encontrado" sin acción es un callejón.
  if (isError || !patient) {
    return (
      <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col items-center justify-center gap-4 p-6 text-center">
        <span className="flex size-14 items-center justify-center rounded-full bg-muted text-muted-foreground">
          <UserX className="size-7" />
        </span>
        <div className="space-y-1">
          <p className="font-medium">No encontramos este paciente</p>
          <p className="text-sm text-muted-foreground">
            Puede que se haya eliminado, o que el enlace no sea de esta clínica.
          </p>
        </div>
        <Link to={PATHS.PATIENTS} className={buttonVariants({ variant: "default", size: "lg" })}>
          Volver al listado
        </Link>
      </main>
    );
  }

  const inactive = patient.status === "INACTIVE";

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col gap-6 p-6">
      <div className="flex items-center gap-2">
        <BackLink />
        <span className="text-sm text-muted-foreground">Pacientes</span>
      </div>

      <header className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border bg-card p-5 shadow-sm">
        <div className="flex items-center gap-4">
          <PatientAvatar fullName={patient.fullName!} muted={inactive} className="size-14 text-lg" />
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold tracking-tight">{patient.fullName}</h1>
              {patient.minor ? <MinorBadge /> : null}
            </div>
            <div className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
              <StatusBadge status={patient.status} />
              <span>· {ageLabel(patient.birthDate!)}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Link to={patientEditPath(id)} className={buttonVariants({ variant: "outline", size: "lg" })}>
            <Pencil className="mr-2 size-4" />
            Editar
          </Link>
          {inactive ? (
            <Button size="lg" disabled={reactivate.isPending} onClick={() => reactivate.mutate(id)}>
              Reactivar
            </Button>
          ) : (
            <Button variant="outline" size="lg" disabled={deactivate.isPending} onClick={() => setConfirmingBaja(true)}>
              Dar de baja
            </Button>
          )}
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

      <InfoCard title="Anamnesis">
        <InfoRow label="Alergias" value={joinOrDash(patient.allergies)} />
        <InfoRow label="Condiciones" value={joinOrDash(patient.conditions)} />
        <InfoRow label="Medicación actual" value={joinOrDash(patient.currentMedication)} />
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
    </main>
  );
}

function BackLink() {
  return (
    <Link to={PATHS.PATIENTS} aria-label="Volver al listado" className={buttonVariants({ variant: "ghost", size: "icon" })}>
      <ArrowLeft className="size-4" />
    </Link>
  );
}

/**
 * Espera con la FORMA de lo que va a llegar, en vez de un spinner: se percibe más corta y no
 * "salta" el layout al pintar. `motion-reduce`: sin pulso para quien pide menos movimiento.
 */
function PatientDetailSkeleton() {
  const pulse = "animate-pulse rounded bg-muted motion-reduce:animate-none";
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col gap-6 p-6" aria-busy="true">
      <div className={`h-9 w-24 ${pulse}`} />
      <div className="flex items-center gap-4 rounded-2xl border bg-card p-5 shadow-sm">
        <div className={`size-14 shrink-0 rounded-full ${pulse}`} />
        <div className="flex-1 space-y-2">
          <div className={`h-5 w-48 ${pulse}`} />
          <div className={`h-3 w-32 ${pulse}`} />
        </div>
      </div>
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="space-y-3 rounded-2xl border bg-card p-5 shadow-sm">
          <div className={`h-4 w-32 ${pulse}`} />
          <div className={`h-3 w-full ${pulse}`} />
          <div className={`h-3 w-2/3 ${pulse}`} />
        </div>
      ))}
    </main>
  );
}

function joinOrDash(values: PatientResponse["allergies"]): string {
  return values && values.length > 0 ? values.join(", ") : "";
}

/** "DNI 12345678" — el tipo va con el número, que es como se lee de verdad. */
function documentLabel(patient: PatientResponse): string {
  if (!patient.document) return "";
  const type = labelOf(documentTypeOptions, patient.documentType);
  return type ? `${type} ${patient.document}` : patient.document;
}
