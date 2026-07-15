import { useForm, useWatch, type UseFormRegister, type FieldError } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Label } from "@/modules/core/ui/label";
import { Input } from "@/modules/core/ui/input";
import { PATHS, patientDetailPath } from "@/routes/paths";
import { applyServerFieldErrors } from "@/modules/core/utils/apiError";
import {
  patientSchema,
  toRequest,
  toDefaults,
  isMinor,
  type PatientValues,
} from "@/modules/patients/schemas/patient";
import { useCreatePatient, usePatient, useUpdatePatient } from "@/modules/patients/hooks/usePatientsApi";

export function PatientFormScreen() {
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
    values: toDefaults(patientQuery.data),
  });

  const birthDate = useWatch({ control, name: "birthDate" });
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
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-6">
      <div className="flex items-center gap-3">
        <Link
          to={isEdit ? patientDetailPath(id!) : PATHS.PATIENTS}
          aria-label="Volver"
          className={buttonVariants({ variant: "ghost", size: "icon" })}
        >
          <ArrowLeft className="size-4" />
        </Link>
        <h1 className="text-xl font-bold">{isEdit ? "Editar paciente" : "Nuevo paciente"}</h1>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-6" noValidate>
        <Section title="Datos personales">
          <Field label="Nombre completo" name="fullName" register={register} error={errors.fullName} required />
          <Field label="Fecha de nacimiento" name="birthDate" type="date" register={register} error={errors.birthDate} required />
          <Field label="Documento" name="document" register={register} error={errors.document} />
          <Field label="Sexo" name="sex" register={register} error={errors.sex} />
        </Section>

        <Section title="Contacto">
          <Field label="Teléfono" name="phone" register={register} error={errors.phone} />
          <Field label="Correo" name="email" register={register} error={errors.email} />
          <Field label="Dirección" name="address" register={register} error={errors.address} />
        </Section>

        {minor ? (
          <Section title="Responsable (obligatorio para menores)">
            <Field label="Nombre del responsable" name="guardianName" register={register} error={errors.guardianName} required />
            <Field label="Parentesco" name="guardianRelationship" register={register} error={errors.guardianRelationship} required />
            <Field label="Teléfono del responsable" name="guardianPhone" register={register} error={errors.guardianPhone} required />
            <Field label="Documento del responsable" name="guardianDocument" register={register} error={errors.guardianDocument} />
          </Section>
        ) : null}

        <Section title="Contacto de emergencia">
          <Field label="Nombre" name="emergencyContactName" register={register} error={errors.emergencyContactName} />
          <Field label="Teléfono" name="emergencyContactPhone" register={register} error={errors.emergencyContactPhone} />
          <Field label="Parentesco" name="emergencyContactRelationship" register={register} error={errors.emergencyContactRelationship} />
        </Section>

        <Section title="Anamnesis (separar por comas)">
          <Field label="Alergias" name="allergies" register={register} error={errors.allergies} placeholder="penicilina, látex" />
          <Field label="Condiciones / antecedentes" name="conditions" register={register} error={errors.conditions} placeholder="diabetes, hipertensión" />
          <Field label="Medicación actual" name="currentMedication" register={register} error={errors.currentMedication} />
        </Section>

        <Button type="submit" disabled={pending} className="self-start">
          {pending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          {isEdit ? "Guardar cambios" : "Registrar paciente"}
        </Button>
      </form>
    </main>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <fieldset className="flex flex-col gap-4 rounded-md border p-4">
      <legend className="px-1 text-sm font-semibold text-muted-foreground">{title}</legend>
      {children}
    </fieldset>
  );
}

type FieldProps = {
  label: string;
  name: keyof PatientValues;
  register: UseFormRegister<PatientValues>;
  error?: FieldError;
  type?: string;
  required?: boolean;
  placeholder?: string;
};

function Field({ label, name, register, error, type, required, placeholder }: FieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={name}>
        {label}
        {required ? <span className="text-destructive"> *</span> : null}
      </Label>
      <Input id={name} type={type} placeholder={placeholder} {...register(name)} />
      {error ? <p className="text-xs text-destructive">{error.message}</p> : null}
    </div>
  );
}
