import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ClipboardList, HeartPulse, Loader2, Stethoscope } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormSection, TextareaField } from "@/modules/core/ui/form-field";
import {
  clinicalHistorySchema,
  toDefaults,
  toRequest,
  missingChecklistFields,
  type ClinicalHistoryValues,
} from "@/modules/clinical-history/schemas/clinicalHistory";
import {
  useClinicalHistory,
  useCompleteClinicalHistory,
  useUpdateClinicalHistory,
} from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import { ChecklistProgress } from "./ChecklistProgress";
import { ClinicalHistoryStatusBadge } from "./ClinicalHistoryStatusBadge";
import { DiagnosisForm } from "./DiagnosisForm";
import { DiagnosisList } from "./DiagnosisList";
import { CompleteHistoryDialog } from "./CompleteHistoryDialog";
import { RevisadoField } from "./RevisadoField";

/** Edición de la Historia Clínica ya creada (DRAFT editable, COMPLETED de solo lectura). */
export function ClinicalHistoryEditor({ patientId }: { patientId: number }) {
  const { data: history } = useClinicalHistory(patientId);
  const updateHistory = useUpdateClinicalHistory(patientId);
  const completeHistory = useCompleteClinicalHistory(patientId);
  const [confirmingComplete, setConfirmingComplete] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<ClinicalHistoryValues>({
    resolver: zodResolver(clinicalHistorySchema),
    mode: "onTouched",
    values: toDefaults(history),
  });

  const values = useWatch({ control });
  const completed = history?.status === "COMPLETED";
  const diagnosisCount = history?.diagnosticos?.length ?? 0;
  const missing = missingChecklistFields(values as ClinicalHistoryValues, diagnosisCount);

  const onSubmit = (formValues: ClinicalHistoryValues) => {
    updateHistory.mutate(toRequest(formValues));
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Historia Clínica</h2>
          <p className="text-sm text-muted-foreground">{history?.fullName}</p>
        </div>
        <div className="flex items-center gap-3">
          {!completed ? <ChecklistProgress missingFields={missing} /> : null}
          <ClinicalHistoryStatusBadge status={history?.status} />
        </div>
      </div>

      <fieldset disabled={completed} className="flex flex-col gap-6">
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
          <FormSection title="Motivo y antecedentes" icon={<Stethoscope className="size-4" />}>
            <TextareaField label="Motivo de consulta" name="motivoConsulta" register={register} error={errors.motivoConsulta} wide />
            <TextareaField label="Enfermedad actual" name="enfermedadActual" register={register} error={errors.enfermedadActual} wide />
            <TextareaField label="Antecedentes personales" name="antecedentesPersonales" register={register} error={errors.antecedentesPersonales} wide />
          </FormSection>

          <FormSection
            title="Alergias, medicación y antecedentes familiares"
            description='Marca "revisado" aunque no haya nada que registrar — es un dato clínico, no un campo vacío.'
            icon={<HeartPulse className="size-4" />}
          >
            <RevisadoField label="Alergias" revisadoName="alergiasRevisado" detalleName="alergiasDetalle" control={control} register={register} />
            <RevisadoField
              label="Medicación actual"
              revisadoName="medicacionRevisado"
              detalleName="medicacionDetalle"
              control={control}
              register={register}
            />
            <RevisadoField
              label="Antecedentes familiares"
              revisadoName="antecedentesFamiliaresRevisado"
              detalleName="antecedentesFamiliaresDetalle"
              control={control}
              register={register}
            />
          </FormSection>

          <FormSection title="Examen clínico y pronóstico" icon={<ClipboardList className="size-4" />}>
            <TextareaField label="Examen clínico" name="examenClinico" register={register} error={errors.examenClinico} wide />
            <TextareaField label="Pronóstico" name="pronostico" register={register} error={errors.pronostico} wide />
          </FormSection>

          {!completed ? (
            <div className="flex justify-end">
              <Button type="submit" disabled={updateHistory.isPending} variant="outline">
                {updateHistory.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
                Guardar avance
              </Button>
            </div>
          ) : null}
        </form>

        <section className="rounded-2xl border bg-card p-5 shadow-sm">
          <h3 className="mb-4 text-sm font-semibold">Diagnóstico (CIE10)</h3>
          <DiagnosisList patientId={patientId} diagnoses={history?.diagnosticos ?? []} editable={!completed} />
          {!completed ? (
            <div className="mt-4 border-t pt-4">
              <DiagnosisForm patientId={patientId} />
            </div>
          ) : null}
        </section>
      </fieldset>

      {!completed ? (
        <div className="flex justify-end">
          <Button onClick={() => setConfirmingComplete(true)} size="lg" className="rounded-xl">
            Completar Historia Clínica
          </Button>
        </div>
      ) : null}

      <CompleteHistoryDialog
        open={confirmingComplete}
        onOpenChange={setConfirmingComplete}
        missingFields={missing}
        pending={completeHistory.isPending}
        onConfirm={() => completeHistory.mutate(undefined, { onSuccess: () => setConfirmingComplete(false) })}
      />
    </div>
  );
}
