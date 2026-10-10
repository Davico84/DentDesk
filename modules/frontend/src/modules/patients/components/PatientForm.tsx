import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, HeartPulse, Loader2, Phone, Siren, User, UserCheck } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { patientDetailPath } from "@/routes/paths";
import { patientsListPath } from "@/modules/patients/utils/listNavigation";
import {
  applyServerFieldErrors,
  getApiProblem,
  getExistingPatientId,
  PATIENT_DOCUMENT_TAKEN,
} from "@/modules/core/utils/apiError";
import {
  patientSchema,
  toRequest,
  toDefaults,
  isMinor,
  documentHint,
  documentMaxLength,
  PHONE_HINT,
  PHONE_COUNTRY_CODE,
  type PatientValues,
} from "@/modules/patients/schemas/patient";
import { useCreatePatient, usePatient, useUpdatePatient } from "@/modules/patients/hooks/usePatientsApi";
import { FormSection, FormField, SelectField, FormErrorSummary } from "@/modules/core/ui/form-field";
import { InfoRow } from "@/modules/core/components/InfoCard";
import { documentTypeOptions, sexOptions, guardianRelationshipOptions } from "./patientOptions";
import { PhoneNumberField } from "./PhoneNumberField";
import { PageContainer } from "@/modules/core/components/PageContainer";

/**
 * Etiqueta de cada campo, en **un solo sitio**: la usan los campos y el resumen de errores. Si
 * vivieran duplicadas, el resumen acabaría nombrando un campo distinto del que se lee en pantalla.
 */
const LABELS: Record<string, string> = {
  fullName: "Nombre completo",
  birthDate: "Fecha de nacimiento",
  sex: "Sexo",
  documentType: "Tipo de documento",
  document: "Documento",
  phone: "Teléfono",
  email: "Correo",
  address: "Dirección",
  guardianName: "Nombre del responsable",
  guardianRelationship: "Parentesco",
  guardianPhone: "Teléfono del responsable",
  guardianDocument: "Documento del responsable",
  emergencyContactName: "Nombre del contacto",
  emergencyContactPhone: "Teléfono del contacto",
  emergencyContactRelationship: "Parentesco del contacto",
  allergies: "Alergias",
  conditions: "Condiciones / antecedentes",
  currentMedication: "Medicación actual",
};

/** Feature del alta/edición de un paciente. La pantalla solo la monta. */
export function PatientForm() {
  const params = useParams();
  const id = params.id ? Number(params.id) : undefined;
  const isEdit = id != null;

  const patientQuery = usePatient(id);
  const createPatient = useCreatePatient();
  const updatePatient = useUpdatePatient(id ?? 0);
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    control,
    setValue,
    trigger,
    setError,
    formState: { errors },
  } = useForm<PatientValues>({
    resolver: zodResolver(patientSchema),
    // "onTouched" = avisa tarde, corrige pronto. La PRIMERA validación es al salir del campo (no
    // te grita "correo inválido" cuando llevas una letra); pero una vez avisado, revalida en cada
    // tecla, así el error se va SOLO al llegar al 8º dígito. Con "onBlur" tendrías que salir del
    // campo para descubrir que ya lo arreglaste: el mensaje se quedaría mintiendo.
    mode: "onTouched",
    values: toDefaults(patientQuery.data),
  });

  const birthDate = useWatch({ control, name: "birthDate" });
  const documentType = useWatch({ control, name: "documentType" });
  const phone = useWatch({ control, name: "phone" });
  const minor = isMinor(birthDate ?? "");
  // Espejo de la regla del schema (update-patients D2): sin esto, "opcional"/"obligatorio" en la
  // descripción de la sección no coincidiría con lo que el propio schema exige al validar.
  const hasOwnPhone = Boolean(phone?.trim()) && phone !== PHONE_COUNTRY_CODE;
  const pending = createPatient.isPending || updatePatient.isPending;
  const anamnesisFrozen = patientQuery.data?.anamnesisFrozen === true;
  const backTo = isEdit ? patientDetailPath(id!) : patientsListPath();

  if (isEdit && patientQuery.isLoading) {
    return (
      <div className="flex items-center justify-center py-24">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const onSubmit = (values: PatientValues) => {
    const request = toRequest(values);
    const options = {
      onSuccess: (saved: { id?: number }) => navigate(patientDetailPath(saved.id ?? id!)),
      onError: (error: unknown) => {
        // Documento duplicado (D5): toast ÚNICO y accionable, en vez del genérico del hook (que se
        // abstiene para este errorType). El resto de errores sigue por applyServerFieldErrors.
        if (getApiProblem(error)?.type === PATIENT_DOCUMENT_TAKEN) {
          const existingId = getExistingPatientId(error);
          toast.error("El documento ya está registrado en esta clínica.", {
            action: existingId
              ? { label: "Abrir ficha existente", onClick: () => navigate(patientDetailPath(existingId)) }
              : undefined,
          });
          return;
        }
        applyServerFieldErrors(error, setError);
      },
    };
    if (isEdit) updatePatient.mutate(request, options);
    else createPatient.mutate(request, options);
  };

  return (
    <PageContainer>
      <header className="flex items-center gap-3">
        <Link to={backTo} aria-label="Volver" className={buttonVariants({ variant: "ghost", size: "icon" })}>
          <ArrowLeft className="size-4" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{isEdit ? "Editar paciente" : "Nuevo paciente"}</h1>
          {isEdit && patientQuery.data ? (
            <p className="text-sm text-muted-foreground">{patientQuery.data.fullName}</p>
          ) : null}
        </div>
      </header>

      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
        {/* Resumen con enlace a cada campo: en un formulario largo, el error puede quedar fuera de pantalla. */}
        <FormErrorSummary errors={errors} labels={LABELS} />

        <FormSection
          title="Datos personales"
          description="Solo el nombre y la fecha de nacimiento son obligatorios."
          icon={<User className="size-4" />}
        >
          <FormField label={LABELS.fullName} name="fullName" register={register} error={errors.fullName} required wide />
          <FormField label={LABELS.birthDate} name="birthDate" type="date" register={register} error={errors.birthDate} required />
          <SelectField label={LABELS.sex} name="sex" register={register} error={errors.sex} options={sexOptions} />
          <SelectField
            label={LABELS.documentType}
            name="documentType"
            register={register}
            error={errors.documentType}
            options={documentTypeOptions}
          />
          <FormField
            label={LABELS.document}
            name="document"
            register={register}
            error={errors.document}
            hint={documentHint(documentType ?? "")}
            maxLength={documentMaxLength(documentType ?? "")}
            inputMode={documentType === "PASSPORT" ? "text" : "numeric"}
          />
        </FormSection>

        {/* type="tel"/"email": abren el teclado correcto en móvil (recepción trabaja en tablet). */}
        <FormSection
          title="Contacto"
          icon={<Phone className="size-4" />}
          collapsible
          hasError={Boolean(errors.phone || errors.email || errors.address)}
        >
          <PhoneNumberField
            label={LABELS.phone}
            name="phone"
            register={register}
            control={control}
            setValue={setValue}
            trigger={trigger}
            error={errors.phone}
            hint={PHONE_HINT}
          />
          <FormField label={LABELS.email} name="email" type="email" register={register} error={errors.email} />
          <FormField label={LABELS.address} name="address" register={register} error={errors.address} wide />
        </FormSection>

        {minor ? (
          <FormSection title="Responsable" icon={<UserCheck className="size-4" />}>
            <p className="rounded-lg bg-accent px-3 py-2 text-xs text-accent-foreground sm:col-span-2">
              Este paciente es <strong>menor de edad</strong>: sus datos de responsable son obligatorios.
            </p>
            <FormField label={LABELS.guardianName} name="guardianName" register={register} error={errors.guardianName} required />
            <SelectField
              label={LABELS.guardianRelationship}
              name="guardianRelationship"
              register={register}
              error={errors.guardianRelationship}
              options={guardianRelationshipOptions}
              required
            />
            <PhoneNumberField
              label={LABELS.guardianPhone}
              name="guardianPhone"
              register={register}
              control={control}
              setValue={setValue}
              trigger={trigger}
              error={errors.guardianPhone}
              hint={PHONE_HINT}
              required
            />
            <FormField label={LABELS.guardianDocument} name="guardianDocument" register={register} error={errors.guardianDocument} />
          </FormSection>
        ) : null}

        {/*
          Divulgación progresiva: empieza plegado — el alta rápida son 2 campos. Se vuelve
          OBLIGATORIO cuando el paciente no tiene teléfono propio (update-patients D2); el texto lo
          explica para que el campo no se sienta arbitrario, y la sección se abre sola para que el
          error no quede oculto tras el "collapsible".
        */}
        <FormSection
          title="Contacto de emergencia"
          description={
            hasOwnPhone
              ? "Opcional."
              : "Obligatorio: este paciente no tiene teléfono propio registrado."
          }
          icon={<Siren className="size-4" />}
          collapsible
          defaultOpen={!hasOwnPhone}
          hasError={Boolean(errors.emergencyContactName || errors.emergencyContactPhone || errors.emergencyContactRelationship)}
        >
          <FormField label={LABELS.emergencyContactName} name="emergencyContactName" register={register} error={errors.emergencyContactName} />
          <PhoneNumberField
            label={LABELS.emergencyContactPhone}
            name="emergencyContactPhone"
            register={register}
            control={control}
            setValue={setValue}
            trigger={trigger}
            error={errors.emergencyContactPhone}
            hint={PHONE_HINT}
          />
          <FormField label={LABELS.emergencyContactRelationship} name="emergencyContactRelationship" register={register} error={errors.emergencyContactRelationship} wide />
        </FormSection>

        <FormSection
          title="Anamnesis"
          description="Opcional. Separa cada elemento con comas: sirven para alertar antes de un tratamiento."
          icon={<HeartPulse className="size-4" />}
          collapsible
          defaultOpen={false}
          hasError={Boolean(errors.allergies || errors.conditions || errors.currentMedication)}
        >
          {anamnesisFrozen ? (
            /*
              update-clinical-history design.md D10: con Historia Clínica, la anamnesis de la ficha no
              cambia. Los campos NO se registran, pero sus valores siguen en el estado del formulario
              (shouldUnregister=false), así que el guardado los manda tal cual y el servidor los acepta.
            */
            <div className="flex flex-col gap-2 text-sm sm:col-span-2">
              <p className="text-muted-foreground">
                El paciente ya tiene Historia Clínica: esta anamnesis queda como se registró al darlo de alta. Los
                antecedentes nuevos se registran en la consulta.
              </p>
              <dl className="flex flex-col gap-2.5">
                <InfoRow label={LABELS.allergies} value={patientQuery.data?.allergies?.join(", ")} />
                <InfoRow label={LABELS.conditions} value={patientQuery.data?.conditions?.join(", ")} />
                <InfoRow label={LABELS.currentMedication} value={patientQuery.data?.currentMedication?.join(", ")} />
              </dl>
            </div>
          ) : (
            <>
              <FormField label={LABELS.allergies} name="allergies" register={register} error={errors.allergies} placeholder="penicilina, látex" wide />
              <FormField label={LABELS.conditions} name="conditions" register={register} error={errors.conditions} placeholder="diabetes, hipertensión" wide />
              <FormField label={LABELS.currentMedication} name="currentMedication" register={register} error={errors.currentMedication} wide />
            </>
          )}
        </FormSection>

        {/*
          Barra de acciones FIJA: en un formulario largo, dejar el botón al final obliga a
          scrollear hasta abajo para guardar. Aquí siempre está a la vista.
        */}
        <div className="sticky bottom-0 -mx-6 border-t bg-background/95 px-6 py-4 backdrop-blur-sm supports-backdrop-filter:bg-background/80">
          <div className="flex items-center justify-end gap-3">
            <Link to={backTo} className={buttonVariants({ variant: "ghost" })}>
              Cancelar
            </Link>
            <Button type="submit" disabled={pending} size="lg" className="rounded-xl">
              {pending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
              {isEdit ? "Guardar cambios" : "Registrar paciente"}
            </Button>
          </div>
        </div>
      </form>
    </PageContainer>
  );
}

