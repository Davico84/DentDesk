import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField, SelectField } from "@/modules/core/ui/form-field";
import {
  DiagnosisRequestUbicacion,
  type Cie10SearchResultResponse,
  type DiagnosisRequest,
} from "@/modules/core/services/generated/model";
import {
  diagnosisSchema,
  diagnosisDefaults,
  toDiagnosisRequest,
  type DiagnosisValues,
} from "@/modules/clinical-history/schemas/diagnosis";
import { Cie10SearchField } from "./Cie10SearchField";
import { ubicacionOptions, tipoDiagnosticoOptions } from "./diagnosisOptions";

/**
 * Agrega un diagnóstico CIE10 (spec: diagnóstico CIE10). No sabe a qué documento: la Historia en
 * DRAFT o una nota de consulta abierta le pasan su propia mutación (`update-clinical-history`).
 *
 * Propuesto desde un hallazgo del odontograma (`update-consultation-note-findings` D5) llega con la
 * ubicación ya cargada (`initial`) y, si el hallazgo las tiene, con códigos sugeridos que se eligen
 * igual que un resultado del buscador — que sigue disponible siempre. Nada se registra sin "Agregar".
 */
export function DiagnosisForm({
  onAdd,
  pending,
  initial,
  suggestions = [],
}: {
  onAdd: (request: DiagnosisRequest, onSuccess: () => void) => void;
  pending: boolean;
  initial?: Partial<DiagnosisValues>;
  suggestions?: Cie10SearchResultResponse[];
}) {
  const [cie10Description, setCie10Description] = useState<string>();

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
    defaultValues: { ...diagnosisDefaults(), ...initial },
  });

  const ubicacion = useWatch({ control, name: "ubicacion" });
  const cie10Code = useWatch({ control, name: "cie10Code" });

  const onSubmit = (values: DiagnosisValues) => {
    onAdd(toDiagnosisRequest(values), () => {
      reset(diagnosisDefaults());
      setCie10Description(undefined);
    });
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4 sm:grid-cols-2" noValidate>
      {suggestions.length > 0 && !cie10Code ? (
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <p className="text-xs font-medium text-muted-foreground">Sugeridos para este hallazgo</p>
          <ul className="flex flex-wrap gap-2" aria-label="Códigos sugeridos">
            {suggestions.map((s) => (
              <li key={s.code}>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-auto rounded-xl py-1.5 text-left"
                  onClick={() => {
                    setValue("cie10Code", s.code ?? "", { shouldValidate: true });
                    setCie10Description(s.descripcion);
                  }}
                >
                  <span className="font-semibold">{s.code}</span>
                  <span className="ml-1.5 font-normal text-muted-foreground">{s.descripcion}</span>
                </Button>
              </li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">O busca cualquier otro código del catálogo:</p>
        </div>
      ) : null}
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
        <Button type="submit" disabled={pending} size="sm" className="rounded-xl">
          {pending ? <Loader2 className="mr-2 size-4 animate-spin" /> : <Plus className="mr-2 size-4" />}
          Agregar diagnóstico
        </Button>
      </div>
    </form>
  );
}
