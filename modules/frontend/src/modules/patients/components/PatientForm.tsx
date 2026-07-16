import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, HeartPulse, Loader2, Phone, Siren, User, UserCheck } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { PATHS, patientDetailPath } from "@/routes/paths";
import { applyServerFieldErrors } from "@/modules/core/utils/apiError";
import {
  patientSchema,
  toRequest,
  toDefaults,
  isMinor,
  documentHint,
  documentMaxLength,
  PHONE_HINT,
  type PatientValues,
} from "@/modules/patients/schemas/patient";
import { useCreatePatient, usePatient, useUpdatePatient } from "@/modules/patients/hooks/usePatientsApi";
import { FormSection, FormField, SelectField, FormErrorSummary } from "@/modules/core/ui/form-field";
import { documentTypeOptions, sexOptions, guardianRelationshipOptions } from "./patientOptions";

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
  const minor = isMinor(birthDate ?? "");
  const pending = createPatient.isPending || updatePatient.isPending;
  const backTo = isEdit ? patientDetailPath(id!) : PATHS.PATIENTS;

  if (isEdit && patientQuery.isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const onSubmit = (values: PatientValues) => {
    const request = toRequest(values);
    const options = {
      onSuccess: (saved: { id?: number }) => navigate(patientDetailPath(saved.id ?? id!)),
      onError: (error: unknown) => applyServerFieldErrors(error, setError),
    };
    if (isEdit) updatePatient.mutate(request, options);
    else createPatient.mutate(request, options);
  };

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col gap-6 p-6">
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
          <FormField label={LABELS.phone} name="phone" type="tel" register={register} error={errors.phone} hint={PHONE_HINT} />
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
            <FormField label={LABELS.guardianPhone} name="guardianPhone" type="tel" register={register} error={errors.guardianPhone} hint={PHONE_HINT} required />
            <FormField label={LABELS.guardianDocument} name="guardianDocument" register={register} error={errors.guardianDocument} />
          </FormSection>
        ) : null}

        {/* Divulgación progresiva: lo opcional empieza plegado — el alta rápida son 2 campos. */}
        <FormSection
          title="Contacto de emergencia"
          description="Opcional."
          icon={<Siren className="size-4" />}
          collapsible
          defaultOpen={false}
          hasError={Boolean(errors.emergencyContactName || errors.emergencyContactPhone || errors.emergencyContactRelationship)}
        >
          <FormField label={LABELS.emergencyContactName} name="emergencyContactName" register={register} error={errors.emergencyContactName} />
          <FormField label={LABELS.emergencyContactPhone} name="emergencyContactPhone" type="tel" register={register} error={errors.emergencyContactPhone} hint={PHONE_HINT} />
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
          <FormField label={LABELS.allergies} name="allergies" register={register} error={errors.allergies} placeholder="penicilina, látex" wide />
          <FormField label={LABELS.conditions} name="conditions" register={register} error={errors.conditions} placeholder="diabetes, hipertensión" wide />
          <FormField label={LABELS.currentMedication} name="currentMedication" register={register} error={errors.currentMedication} wide />
        </FormSection>

        {/*
          Barra de acciones FIJA: en un formulario largo, dejar el botón al final obliga a
          scrollear hasta abajo para guardar. Aquí siempre está a la vista.
        */}
        <div className="sticky bottom-0 -mx-6 border-t bg-background/95 px-6 py-4 backdrop-blur supports-[backdrop-filter]:bg-background/80">
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
    </main>
  );
}
