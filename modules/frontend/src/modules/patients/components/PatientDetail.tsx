import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Loader2, Pencil } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
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

/** Feature de la consulta de una ficha: datos, y acciones de edición/baja/reactivación. */
export function PatientDetail() {
  const params = useParams();
  const id = Number(params.id);

  const { data: patient, isLoading, isError } = usePatient(id);
  const deactivate = useDeactivatePatient();
  const reactivate = useReactivatePatient();

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !patient) {
    return (
      <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col gap-4 p-6">
        <BackLink />
        <p className="py-12 text-center text-sm text-destructive">No se ha encontrado el paciente.</p>
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
          <Link to={patientEditPath(id)} className={buttonVariants({ variant: "outline" })}>
            <Pencil className="mr-2 size-4" />
            Editar
          </Link>
          {inactive ? (
            <Button disabled={reactivate.isPending} onClick={() => reactivate.mutate(id)}>
              Reactivar
            </Button>
          ) : (
            <Button variant="outline" disabled={deactivate.isPending} onClick={() => deactivate.mutate(id)}>
              Dar de baja
            </Button>
          )}
        </div>
      </header>

      <InfoCard title="Datos personales">
        <InfoRow label="Documento" value={patient.document} />
        <InfoRow label="Fecha de nacimiento" value={patient.birthDate} />
        <InfoRow label="Sexo" value={patient.sex} />
      </InfoCard>

      <InfoCard title="Contacto">
        <InfoRow label="Teléfono" value={patient.phone} />
        <InfoRow label="Correo" value={patient.email} />
        <InfoRow label="Dirección" value={patient.address} />
      </InfoCard>

      {patient.guardianName || patient.guardianPhone ? (
        <InfoCard title="Responsable">
          <InfoRow label="Nombre" value={patient.guardianName} />
          <InfoRow label="Parentesco" value={patient.guardianRelationship} />
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

function joinOrDash(values: PatientResponse["allergies"]): string {
  return values && values.length > 0 ? values.join(", ") : "";
}
