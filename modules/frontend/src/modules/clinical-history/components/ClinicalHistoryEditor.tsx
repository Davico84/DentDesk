import { useState } from "react";
import { Link } from "react-router-dom";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { CircleAlert, ClipboardList, HeartPulse, Loader2, Smile, Stethoscope } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { FormSection, TextareaField } from "@/modules/core/ui/form-field";
import { dentalChartPath } from "@/routes/paths";
import { useDentalChart, useDentalFindingCatalog } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { missingDentalChartFields } from "@/modules/dental-chart/schemas/dentalChart";
import {
  clinicalHistorySchema,
  toDefaults,
  toRequest,
  missingChecklistFields,
  type ClinicalHistoryValues,
} from "@/modules/clinical-history/schemas/clinicalHistory";
import {
  useAddDiagnosis,
  useClinicalHistory,
  useCompleteClinicalHistory,
  useRemoveDiagnosis,
  useUpdateClinicalHistory,
} from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import { ChecklistProgress } from "./ChecklistProgress";
import { ClinicalHistoryStatusBadge } from "./ClinicalHistoryStatusBadge";
import { DiagnosisForm } from "./DiagnosisForm";
import { DiagnosisList } from "./DiagnosisList";
import { CompleteHistoryDialog } from "./CompleteHistoryDialog";
import { RevisadoField } from "./RevisadoField";
import { useMyClinicalCapacity } from "@/modules/staff/hooks/useProfessionalCredential";
import { SignedByLine } from "@/modules/staff/components/SignedByLine";
import { SigningCapacityNotice } from "@/modules/staff/components/SigningCapacityNotice";

/** Edición de la Historia Clínica ya creada (DRAFT editable, COMPLETED de solo lectura). */
export function ClinicalHistoryEditor({ patientId }: { patientId: number }) {
  const { data: history } = useClinicalHistory(patientId);
  const { data: dentalChart } = useDentalChart(patientId);
  const { data: findingCatalog } = useDentalFindingCatalog();
  const updateHistory = useUpdateClinicalHistory(patientId);
  const completeHistory = useCompleteClinicalHistory(patientId);
  const addDiagnosis = useAddDiagnosis(patientId);
  const removeDiagnosis = useRemoveDiagnosis(patientId);
  const [confirmingComplete, setConfirmingComplete] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty },
  } = useForm<ClinicalHistoryValues>({
    resolver: zodResolver(clinicalHistorySchema),
    mode: "onTouched",
    values: toDefaults(history),
  });

  const values = useWatch({ control });
  const completed = history?.status === "COMPLETED";
  // Cerrar la admisión es firmar: exige capacidad clínica (update-staff-professional-credential D7).
  const { capacity } = useMyClinicalCapacity();
  const canSign = capacity?.state === "ENABLED";
  const diagnosisCount = history?.diagnosticos?.length ?? 0;
  const hasInitialDentalChart = dentalChart != null;
  const missing = missingChecklistFields(values as ClinicalHistoryValues, diagnosisCount, hasInitialDentalChart);
  // El sellado cierra la admisión entera: el odontograma también tiene su checklist, y sin mostrarlo
  // aquí el diálogo dejaba confirmar y el servidor rechazaba sin decir qué faltaba.
  const dentalChartMissing = hasInitialDentalChart ? missingDentalChartFields(dentalChart, findingCatalog) : [];

  const onSubmit = (formValues: ClinicalHistoryValues) => {
    updateHistory.mutate(toRequest(formValues));
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Historia Clínica</h2>
          <p className="text-sm text-muted-foreground">{history?.fullName}</p>
          {completed ? (
            <SignedByLine signer={history?.signer} signedBeforeCredentials={history?.signedBeforeCredentials} />
          ) : null}
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
            <div className="flex items-center justify-end gap-3">
              {isDirty ? (
                <p className="flex items-center gap-1.5 text-xs text-warning">
                  <CircleAlert className="size-3.5" aria-hidden="true" />
                  Tienes cambios sin guardar
                </p>
              ) : null}
              <Button type="submit" disabled={updateHistory.isPending} variant="outline">
                {updateHistory.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
                Guardar avance
              </Button>
            </div>
          ) : null}
        </form>

        <section className="rounded-2xl border bg-card p-5 shadow-xs">
          <h3 className="mb-4 text-sm font-semibold">Diagnóstico (CIE10)</h3>
          <DiagnosisList
            diagnoses={history?.diagnosticos ?? []}
            onRemove={completed ? undefined : (id) => removeDiagnosis.mutate(id)}
            removing={removeDiagnosis.isPending}
          />
          {!completed ? (
            <div className="mt-4 border-t pt-4">
              <DiagnosisForm
                pending={addDiagnosis.isPending}
                onAdd={(request, onSuccess) => addDiagnosis.mutate(request, { onSuccess })}
              />
            </div>
          ) : null}
        </section>
      </fieldset>

      {/*
        add-dental-chart (design.md D4/D5b): el sellado ya no depende solo de esta pantalla, así
        que sin esto el odontólogo chocaría con un rechazo del servidor sin entender por qué.
      */}
      {!completed && hasInitialDentalChart && dentalChartMissing.length > 0 ? (
        <div className="flex items-center justify-between gap-3 rounded-2xl border bg-accent px-4 py-3 text-sm text-accent-foreground">
          <span className="flex items-center gap-2">
            <Smile className="size-4 shrink-0" />
            Al odontograma inicial le falta información para poder completar la admisión.
          </span>
          <Link to={dentalChartPath(patientId)} className={buttonVariants({ variant: "outline", size: "sm" })}>
            Completar odontograma
          </Link>
        </div>
      ) : null}

      {!completed && !hasInitialDentalChart ? (
        <div className="flex items-center justify-between gap-3 rounded-2xl border bg-accent px-4 py-3 text-sm text-accent-foreground">
          <span className="flex items-center gap-2">
            <Smile className="size-4 shrink-0" />
            Falta el odontograma inicial: también es requisito para completar la admisión.
          </span>
          <Link to={dentalChartPath(patientId)} className={buttonVariants({ variant: "outline", size: "sm" })}>
            Ir al odontograma
          </Link>
        </div>
      ) : null}

      {!completed ? <SigningCapacityNotice capacity={capacity} /> : null}

      {!completed ? (
        <div className="flex flex-col items-end gap-1.5">
          {isDirty ? (
            <p className="flex items-center gap-1.5 text-xs text-warning">
              <CircleAlert className="size-3.5" aria-hidden="true" />
              Guarda el avance antes de completar — el sello valida lo ya guardado, no lo tipeado
            </p>
          ) : null}
          <Button
            onClick={() => setConfirmingComplete(true)}
            size="lg"
            className="rounded-xl"
            disabled={isDirty || !canSign}
          >
            Completar Historia Clínica
          </Button>
        </div>
      ) : null}

      <CompleteHistoryDialog
        open={confirmingComplete}
        onOpenChange={setConfirmingComplete}
        missingFields={missing}
        dentalChartMissingFields={dentalChartMissing}
        dentalChartHref={dentalChartPath(patientId)}
        pending={completeHistory.isPending}
        onConfirm={() => completeHistory.mutate(undefined, { onSuccess: () => setConfirmingComplete(false) })}
      />
    </div>
  );
}
