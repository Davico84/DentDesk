import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Loader2, Pencil } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/core/ui/card";
import { PATHS, patientEditPath } from "@/routes/paths";
import type { PatientResponse } from "@/modules/core/services/generated/model";
import {
  useDeactivatePatient,
  usePatient,
  useReactivatePatient,
} from "@/modules/patients/hooks/usePatientsApi";

/** Consulta de la ficha de un paciente, con las acciones de baja/reactivación y edición. */
export function PatientDetailScreen() {
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
      <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-4 p-6">
        <BackLink />
        <p className="py-12 text-center text-sm text-destructive">No se ha encontrado el paciente.</p>
      </main>
    );
  }

  const inactive = patient.status === "INACTIVE";

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <BackLink />
          <h1 className="text-xl font-bold">{patient.fullName}</h1>
          {patient.minor ? <span className="rounded bg-accent px-1.5 py-0.5 text-xs text-accent-foreground">menor</span> : null}
          {inactive ? <span className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">de baja</span> : null}
        </div>
        <div className="flex items-center gap-2">
          <Link to={patientEditPath(id)} className={buttonVariants({ variant: "outline" })}>
            <Pencil className="mr-2 size-4" />
            Editar
          </Link>
          {inactive ? (
            <Button variant="default" disabled={reactivate.isPending} onClick={() => reactivate.mutate(id)}>
              Reactivar
            </Button>
          ) : (
            <Button variant="outline" disabled={deactivate.isPending} onClick={() => deactivate.mutate(id)}>
              Dar de baja
            </Button>
          )}
        </div>
      </div>

      <FieldGroup title="Datos personales">
        <Row label="Documento" value={patient.document} />
        <Row label="Fecha de nacimiento" value={patient.birthDate} />
        <Row label="Sexo" value={patient.sex} />
      </FieldGroup>

      <FieldGroup title="Contacto">
        <Row label="Teléfono" value={patient.phone} />
        <Row label="Correo" value={patient.email} />
        <Row label="Dirección" value={patient.address} />
      </FieldGroup>

      {patient.guardianName || patient.guardianPhone ? (
        <FieldGroup title="Responsable">
          <Row label="Nombre" value={patient.guardianName} />
          <Row label="Parentesco" value={patient.guardianRelationship} />
          <Row label="Teléfono" value={patient.guardianPhone} />
          <Row label="Documento" value={patient.guardianDocument} />
        </FieldGroup>
      ) : null}

      <FieldGroup title="Contacto de emergencia">
        <Row label="Nombre" value={patient.emergencyContactName} />
        <Row label="Teléfono" value={patient.emergencyContactPhone} />
        <Row label="Parentesco" value={patient.emergencyContactRelationship} />
      </FieldGroup>

      <FieldGroup title="Anamnesis">
        <Row label="Alergias" value={joinOrDash(patient.allergies)} />
        <Row label="Condiciones" value={joinOrDash(patient.conditions)} />
        <Row label="Medicación actual" value={joinOrDash(patient.currentMedication)} />
      </FieldGroup>
    </main>
  );
}

function BackLink() {
  return (
    <Link to={PATHS.PATIENTS} aria-label="Volver" className={buttonVariants({ variant: "ghost", size: "icon" })}>
      <ArrowLeft className="size-4" />
    </Link>
  );
}

function FieldGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-sm">{children}</CardContent>
    </Card>
  );
}

function Row({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right">{value && value.length > 0 ? value : "—"}</span>
    </div>
  );
}

function joinOrDash(values: PatientResponse["allergies"]): string {
  return values && values.length > 0 ? values.join(", ") : "";
}
