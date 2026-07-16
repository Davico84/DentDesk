import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Loader2 } from "lucide-react";
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
  type PatientValues,
} from "@/modules/patients/schemas/patient";
import { useCreatePatient, usePatient, useUpdatePatient } from "@/modules/patients/hooks/usePatientsApi";
import { FormSection, FormField, SelectField } from "@/modules/core/ui/form-field";
import { documentTypeOptions, sexOptions, guardianRelationshipOptions } from "./patientOptions";

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
    // Validación en BLUR, no solo al enviar: el error aparece al salir del campo, cuando ya
    // terminaste de escribirlo (no en cada tecla, que sería ruido).
    mode: "onBlur",
    values: toDefaults(patientQuery.data),
  });

  const birthDate = useWatch({ control, name: "birthDate" });
  const documentType = useWatch({ control, name: "documentType" });
  const minor = isMinor(birthDate ?? "");
  const pending = createPatient.isPending || updatePatient.isPending;

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
        <Link
          to={isEdit ? patientDetailPath(id!) : PATHS.PATIENTS}
          aria-label="Volver"
          className={buttonVariants({ variant: "ghost", size: "icon" })}
        >
          <ArrowLeft className="size-4" />
        </Link>
        <h1 className="text-2xl font-bold tracking-tight">{isEdit ? "Editar paciente" : "Nuevo paciente"}</h1>
      </header>

      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
        <FormSection title="Datos personales" description="Solo el nombre y la fecha de nacimiento son obligatorios.">
          <FormField label="Nombre completo" name="fullName" register={register} error={errors.fullName} required wide />
          <FormField label="Fecha de nacimiento" name="birthDate" type="date" register={register} error={errors.birthDate} required />
          <SelectField label="Sexo" name="sex" register={register} error={errors.sex} options={sexOptions} />
          <SelectField
            label="Tipo de documento"
            name="documentType"
            register={register}
            error={errors.documentType}
            options={documentTypeOptions}
          />
          <FormField
            label="Documento"
            name="document"
            register={register}
            error={errors.document}
            hint={documentHint(documentType ?? "")}
          />
        </FormSection>

        {/* type="tel"/"email": abren el teclado correcto en móvil (recepción trabaja en tablet). */}
        <FormSection
          title="Contacto"
          collapsible
          hasError={Boolean(errors.phone || errors.email || errors.address)}
        >
          <FormField label="Teléfono" name="phone" type="tel" register={register} error={errors.phone} />
          <FormField label="Correo" name="email" type="email" register={register} error={errors.email} />
          <FormField label="Dirección" name="address" register={register} error={errors.address} wide />
        </FormSection>

        {minor ? (
          <FormSection
            title="Responsable"
            description="Este paciente es menor de edad: sus datos de responsable son obligatorios."
          >
            <FormField label="Nombre del responsable" name="guardianName" register={register} error={errors.guardianName} required />
            <SelectField
              label="Parentesco"
              name="guardianRelationship"
              register={register}
              error={errors.guardianRelationship}
              options={guardianRelationshipOptions}
              required
            />
            <FormField label="Teléfono del responsable" name="guardianPhone" type="tel" register={register} error={errors.guardianPhone} required />
            <FormField label="Documento del responsable" name="guardianDocument" register={register} error={errors.guardianDocument} />
          </FormSection>
        ) : null}

        {/* Divulgación progresiva: lo opcional empieza plegado — el alta rápida son 2 campos. */}
        <FormSection
          title="Contacto de emergencia"
          description="Opcional."
          collapsible
          defaultOpen={false}
          hasError={Boolean(errors.emergencyContactName || errors.emergencyContactPhone || errors.emergencyContactRelationship)}
        >
          <FormField label="Nombre" name="emergencyContactName" register={register} error={errors.emergencyContactName} />
          <FormField label="Teléfono" name="emergencyContactPhone" type="tel" register={register} error={errors.emergencyContactPhone} />
          <FormField label="Parentesco" name="emergencyContactRelationship" register={register} error={errors.emergencyContactRelationship} wide />
        </FormSection>

        <FormSection
          title="Anamnesis"
          description="Opcional. Separa cada elemento con comas: sirven para alertar antes de un tratamiento."
          collapsible
          defaultOpen={false}
          hasError={Boolean(errors.allergies || errors.conditions || errors.currentMedication)}
        >
          <FormField label="Alergias" name="allergies" register={register} error={errors.allergies} placeholder="penicilina, látex" wide />
          <FormField label="Condiciones / antecedentes" name="conditions" register={register} error={errors.conditions} placeholder="diabetes, hipertensión" wide />
          <FormField label="Medicación actual" name="currentMedication" register={register} error={errors.currentMedication} wide />
        </FormSection>

        <Button type="submit" disabled={pending} size="lg" className="self-start rounded-xl">
          {pending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          {isEdit ? "Guardar cambios" : "Registrar paciente"}
        </Button>
      </form>
    </main>
  );
}
