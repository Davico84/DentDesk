import { useEffect, useMemo } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Plus } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField, SelectField, type SelectOption } from "@/modules/core/ui/form-field";
import { applyServerFieldErrors } from "@/modules/core/utils/apiError";
import { applicableSurfaces, isFdiValid } from "@/modules/core/utils/fdiTeeth";
import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import {
  buildFindingSchema,
  catalogEntryFor,
  findingFormDefaults,
  subtypesFor,
  toFindingRequest,
  type FindingFormValues,
} from "@/modules/dental-chart/schemas/dentalChart";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { useAddFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";

const CONDITION_OPTIONS: SelectOption[] = [
  { value: "GOOD", label: "Buen estado" },
  { value: "POOR", label: "Mal estado" },
];
const ARCH_OPTIONS: SelectOption[] = [
  { value: "SUPERIOR", label: "Superior" },
  { value: "INFERIOR", label: "Inferior" },
];
const SURFACE_LABELS: Record<string, string> = {
  MESIAL: "Mesial",
  DISTAL: "Distal",
  VESTIBULAR: "Vestibular",
  PALATINO: "Palatino",
  LINGUAL: "Lingual",
  OCLUSAL: "Oclusal",
  INCISAL: "Incisal",
};

/**
 * Agrega un hallazgo al odontograma abierto, con la forma de localización elegible a mano — a
 * diferencia de {@link import("./FindingDialog").FindingDialog}, que fija la localización desde el
 * click. Queda como el único camino para **rango** y **entre-piezas**, que todavía no son
 * clickeables en el propio gráfico (design.md D10, Open Question 4). El catálogo
 * (tipo→alcance/subtipos/grado/estado) viaja por contrato (design.md D6): el formulario solo
 * pinta los campos que el tipo elegido exige, sin reinventar esas reglas.
 */
export function FindingForm({
  patientId,
  catalog,
}: {
  patientId: number;
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
}) {
  const addFinding = useAddFinding(patientId);

  const {
    register,
    handleSubmit,
    control,
    setValue,
    setError,
    reset,
    formState: { errors },
  } = useForm<FindingFormValues>({
    resolver: zodResolver(buildFindingSchema(catalog)),
    mode: "onTouched",
    defaultValues: findingFormDefaults(),
  });

  const type = useWatch({ control, name: "type" });
  const subtype = useWatch({ control, name: "subtype" });
  const toothA = useWatch({ control, name: "location.toothA" });
  const teethInput = useWatch({ control, name: "location.teethInput" });

  useEffect(() => {
    const teeth = teethInput
      .split(",")
      .map((s) => Number(s.trim()))
      .filter((n) => Number.isFinite(n) && n !== 0);
    setValue("location.teeth", teeth, { shouldValidate: true });
  }, [teethInput, setValue]);

  // El subtipo pertenece a UN tipo — cambiar de tipo sin limpiarlo deja un subtipo ajeno que
  // `catalogEntryFor` no encuentra: `entry` queda undefined y TODOS los campos condicionales
  // (grado, pieza, superficie...) desaparecen en silencio, incluso para tipos que sí los exigen.
  useEffect(() => {
    setValue("subtype", "", { shouldValidate: false });
  }, [type, setValue]);

  const typeOptions: SelectOption[] = useMemo(() => {
    const types = new Set<string>();
    for (const e of catalog ?? []) {
      if (e.type) types.add(e.type);
    }
    return [...types].map((t) => ({ value: t, label: findingTypeLabel(t) }));
  }, [catalog]);
  const subtypeOptions: SelectOption[] = useMemo(
    () => subtypesFor(catalog, type).map((e) => ({ value: e.subtype ?? "", label: e.acronym ?? e.subtype ?? "" })),
    [catalog, type],
  );
  const entry = catalogEntryFor(catalog, type, subtype);
  const scope = entry?.scope;
  const requiresCondition = entry?.colorBehavior === "SEGUN_ESTADO";

  // El formulario no tiene un selector de "forma de localización": se deriva del tipo elegido (el
  // catálogo ya la trae). Además de fijar locationKind, hay que LIMPIAR los campos que no le
  // pertenecen a la nueva forma: si el usuario cambia de un tipo SURFACE a uno TOOTH, `surface`
  // queda con un valor viejo que el servidor rechaza ("no aplica a esta forma de localización")
  // aunque en el formulario ya no se vea ese campo. `toothA` sobrevive entre formas que lo usan
  // (SURFACE/TOOTH/BETWEEN_TEETH) para no perder lo que el click en el odontograma precargó.
  useEffect(() => {
    if (!scope) return;
    const ownsToothA = scope === "SURFACE" || scope === "TOOTH" || scope === "BETWEEN_TEETH";
    setValue("location.locationKind", scope, { shouldValidate: false });
    if (!ownsToothA) setValue("location.toothA", "", { shouldValidate: false });
    if (scope !== "SURFACE") setValue("location.surface", "", { shouldValidate: false });
    if (scope !== "BETWEEN_TEETH") setValue("location.toothB", "", { shouldValidate: false });
    if (scope !== "ARCH") setValue("location.arch", "", { shouldValidate: false });
    if (scope !== "TOOTH_RANGE") {
      setValue("location.teeth", [], { shouldValidate: false });
      setValue("location.teethInput", "", { shouldValidate: false });
    }
  }, [scope, setValue]);

  const applicable = typeof toothA === "number" && isFdiValid(toothA) ? new Set(applicableSurfaces(toothA)) : null;
  const surfaceOptions: SelectOption[] = Object.entries(SURFACE_LABELS)
    .filter(([value]) => !applicable || applicable.has(value as never))
    .map(([value, label]) => ({ value, label }));
  const teethErrorRaw = errors.location?.teeth;
  const teethError = teethErrorRaw && "message" in teethErrorRaw ? { type: "custom", message: teethErrorRaw.message } : undefined;

  const onSubmit = (values: FindingFormValues) => {
    addFinding.mutate(toFindingRequest(values), {
      onSuccess: () => reset(findingFormDefaults()),
      onError: (error) => applyServerFieldErrors<FindingFormValues>(error, setError),
    });
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4 sm:grid-cols-2" noValidate>
      <SelectField label="Tipo de hallazgo" name="type" register={register} error={errors.type} options={typeOptions} required wide />

      {subtypeOptions.length > 0 ? (
        <SelectField label="Subtipo" name="subtype" register={register} error={errors.subtype} options={subtypeOptions} required />
      ) : null}

      {requiresCondition ? (
        <SelectField label="Estado de conservación" name="condition" register={register} error={errors.condition} options={CONDITION_OPTIONS} required />
      ) : null}

      {entry?.requiresGrade ? (
        <FormField
          label="Grado de movilidad (1-3)"
          name="grade"
          type="text"
          inputMode="numeric"
          valueAsNumber
          register={register}
          error={errors.grade}
          required
        />
      ) : null}

      {scope === "SURFACE" || scope === "TOOTH" ? (
        <FormField
          label="Pieza (FDI)"
          name="location.toothA"
          type="text"
          inputMode="numeric"
          valueAsNumber
          register={register}
          error={errors.location?.toothA}
          hint="Notación FDI, p. ej. 36."
          required
        />
      ) : null}
      {scope === "SURFACE" ? (
        <SelectField
          label="Superficie"
          name="location.surface"
          register={register}
          error={errors.location?.surface}
          options={surfaceOptions}
          required
        />
      ) : null}

      {scope === "BETWEEN_TEETH" ? (
        <>
          <FormField
            label="Pieza A (FDI)"
            name="location.toothA"
            type="text"
            inputMode="numeric"
            valueAsNumber
            register={register}
            error={errors.location?.toothA}
            required
          />
          <FormField
            label="Pieza B (FDI)"
            name="location.toothB"
            type="text"
            inputMode="numeric"
            valueAsNumber
            register={register}
            error={errors.location?.toothB}
            required
          />
        </>
      ) : null}

      {scope === "TOOTH_RANGE" ? (
        <div className="sm:col-span-2">
          <FormField
            label="Piezas del rango (FDI, separadas por coma)"
            name="location.teethInput"
            type="text"
            register={register}
            error={teethError}
            hint="Ej: 15,14,13 — el orden identifica los pilares."
            wide
          />
        </div>
      ) : null}

      {scope === "ARCH" ? (
        <SelectField label="Arco" name="location.arch" register={register} error={errors.location?.arch} options={ARCH_OPTIONS} required />
      ) : null}

      <div className="sm:col-span-2">
        <Button type="submit" disabled={addFinding.isPending || !type} size="sm" className="rounded-xl">
          {addFinding.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : <Plus className="mr-2 size-4" />}
          Agregar hallazgo
        </Button>
      </div>
    </form>
  );
}
