import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField, SelectField } from "@/modules/core/ui/form-field";
import { DiagnosisRequestUbicacion } from "@/modules/core/services/generated/model";
import {
  diagnosisSchema,
  diagnosisDefaults,
  toDiagnosisRequest,
  type DiagnosisValues,
} from "@/modules/clinical-history/schemas/diagnosis";
import { useAddDiagnosis } from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import { Cie10SearchField } from "./Cie10SearchField";
import { ubicacionOptions, tipoDiagnosticoOptions } from "./diagnosisOptions";

/** Agrega un diagnóstico a la Historia en DRAFT (spec: diagnóstico CIE10). */
export function DiagnosisForm({ patientId }: { patientId: number }) {
  const [cie10Description, setCie10Description] = useState<string>();
  const addDiagnosis = useAddDiagnosis(patientId);

  const {
    register,
    handleSubmit,
    control,
    setValue,
    reset,
    formState: { errors },
  } = useForm<DiagnosisValues>({
    resolver: zodResolver(diagnosisSchema),
    mode: "onTouched",
    defaultValues: diagnosisDefaults(),
  });

  const ubicacion = useWatch({ control, name: "ubicacion" });
  const cie10Code = useWatch({ control, name: "cie10Code" });

  const onSubmit = (values: DiagnosisValues) => {
    addDiagnosis.mutate(toDiagnosisRequest(values), {
      onSuccess: () => {
        reset(diagnosisDefaults());
        setCie10Description(undefined);
      },
    });
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4 sm:grid-cols-2" noValidate>
      <Cie10SearchField
        selectedCode={cie10Code || undefined}
        selectedDescription={cie10Description}
        onSelect={(result) => {
          setValue("cie10Code", result?.code ?? "", { shouldValidate: true });
          setCie10Description(result?.descripcion);
        }}
      />
      {errors.cie10Code ? (
        <p role="alert" className="-mt-2 text-xs text-destructive sm:col-span-2">
          {errors.cie10Code.message}
        </p>
      ) : null}

      <SelectField
        label="Ubicación"
        name="ubicacion"
        register={register}
        error={errors.ubicacion}
        options={ubicacionOptions}
        required
      />
      {ubicacion === DiagnosisRequestUbicacion.PIEZA ? (
        <FormField
          label="Número de pieza (FDI)"
          name="fdiNumber"
          type="text"
          inputMode="numeric"
          valueAsNumber
          register={register}
          error={errors.fdiNumber}
          hint="Notación FDI, p. ej. 36."
          required
        />
      ) : null}
      <SelectField
        label="Tipo de diagnóstico"
        name="tipoDiagnostico"
        register={register}
        error={errors.tipoDiagnostico}
        options={tipoDiagnosticoOptions}
        required
        wide={ubicacion === DiagnosisRequestUbicacion.PIEZA}
      />

      <div className="sm:col-span-2">
        <Button type="submit" disabled={addDiagnosis.isPending} size="sm" className="rounded-xl">
          {addDiagnosis.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : <Plus className="mr-2 size-4" />}
          Agregar diagnóstico
        </Button>
      </div>
    </form>
  );
}
