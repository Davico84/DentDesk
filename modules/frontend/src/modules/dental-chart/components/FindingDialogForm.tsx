import { useMemo } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { SelectField, type SelectOption } from "@/modules/core/ui/form-field";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/modules/core/ui/dialog";
import { applyServerFieldErrors } from "@/modules/core/utils/apiError";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import {
  buildFindingSchema,
  catalogEntryFor,
  findingFormDefaults,
  subtypesFor,
  toFindingRequest,
  type FindingFormValues,
} from "@/modules/dental-chart/schemas/dentalChart";
import { findingTypeLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { useAddFinding, useUpdateFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";
import type { ClickedLocation } from "./FindingDialog";

const CONDITION_OPTIONS: SelectOption[] = [
  { value: "GOOD", label: "Buen estado" },
  { value: "POOR", label: "Mal estado" },
];

/** El formulario propiamente dicho, dentro de {@link import("./FindingDialog").FindingDialog}: la
 *  localización ya está decidida por el click y no se muestra como campo editable — solo
 *  tipo/subtipo/estado/grado, según lo que ese tipo exija (el catálogo filtra las opciones por
 *  `location.locationKind`, así que no se puede elegir un tipo de otro alcance). */
export function FindingDialogForm({
  open,
  onOpenChange,
  patientId,
  catalog,
  location,
  locationText,
  editingFinding,
  onBack,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  patientId: number;
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  location: ClickedLocation;
  locationText: string;
  editingFinding: DentalChartFindingResponse | null;
  onBack?: () => void;
}) {
  const addFinding = useAddFinding(patientId);
  const updateFinding = useUpdateFinding(patientId);
  const isEditing = editingFinding != null;

  const defaults: FindingFormValues = editingFinding
    ? {
        type: editingFinding.type ?? "",
        subtype: editingFinding.subtype ?? "",
        condition: editingFinding.condition ?? "",
        grade: editingFinding.grade ?? "",
        location: { ...findingFormDefaults().location, ...location },
      }
    : findingFormDefaults(location);

  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<FindingFormValues>({
    resolver: zodResolver(buildFindingSchema(catalog)),
    mode: "onTouched",
    defaultValues: defaults,
  });

  const type = useWatch({ control, name: "type" });
  const subtype = useWatch({ control, name: "subtype" });

  const typeOptions: SelectOption[] = useMemo(() => {
    const types = new Set<string>();
    for (const e of catalog ?? []) {
      if (e.type && e.scope === location.locationKind) types.add(e.type);
    }
    return [...types].map((t) => ({ value: t, label: findingTypeLabel(t) }));
  }, [catalog, location.locationKind]);
  const subtypeOptions: SelectOption[] = useMemo(
    () => subtypesFor(catalog, type).map((e) => ({ value: e.subtype ?? "", label: e.acronym ?? e.subtype ?? "" })),
    [catalog, type],
  );
  const entry = catalogEntryFor(catalog, type, subtype);
  const requiresCondition = entry?.colorBehavior === "SEGUN_ESTADO";

  const pending = addFinding.isPending || updateFinding.isPending;

  const onSubmit = (values: FindingFormValues) => {
    const request = toFindingRequest({ ...values, location: { ...values.location, ...location, locationKind: location.locationKind } });
    if (isEditing && editingFinding?.id != null) {
      updateFinding.mutate(
        { findingId: editingFinding.id, data: request },
        { onSuccess: () => onOpenChange(false), onError: (e) => applyServerFieldErrors<FindingFormValues>(e, setError) },
      );
    } else {
      addFinding.mutate(request, {
        onSuccess: () => onOpenChange(false),
        onError: (e) => applyServerFieldErrors<FindingFormValues>(e, setError),
      });
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEditing ? "Editar hallazgo" : "Agregar hallazgo"}</DialogTitle>
          <DialogDescription>{locationText}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4 sm:grid-cols-2" noValidate>
          <SelectField label="Tipo de hallazgo" name="type" register={register} error={errors.type} options={typeOptions} required wide />
          {subtypeOptions.length > 0 ? (
            <SelectField label="Subtipo" name="subtype" register={register} error={errors.subtype} options={subtypeOptions} required wide />
          ) : null}
          {requiresCondition ? (
            <SelectField label="Estado de conservación" name="condition" register={register} error={errors.condition} options={CONDITION_OPTIONS} required wide />
          ) : null}
          {entry?.requiresGrade ? (
            <div className="flex flex-col gap-1.5 sm:col-span-2">
              <label htmlFor="grade" className="text-sm font-medium">
                Grado de movilidad (1-3)
              </label>
              <input
                id="grade"
                type="text"
                inputMode="numeric"
                className="h-11 rounded-xl border border-input bg-card px-3 text-sm"
                {...register("grade", { setValueAs: (v: string) => (v === "" ? "" : Number(v)) })}
              />
              {errors.grade ? (
                <p role="alert" className="text-xs text-destructive">
                  {errors.grade.message}
                </p>
              ) : null}
            </div>
          ) : null}

          <DialogFooter className="sm:col-span-2">
            {onBack ? (
              <Button type="button" variant="ghost" onClick={onBack} disabled={pending}>
                Volver
              </Button>
            ) : null}
            <Button type="submit" disabled={pending || !type}>
              {pending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
              {isEditing ? "Guardar cambios" : "Agregar hallazgo"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
