import { useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
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
import { findingTypeLabel, subtypeOptionLabel } from "@/modules/dental-chart/schemas/findingTypeLabels";
import { GroupedCombobox } from "@/modules/core/components/GroupedCombobox";
import { useAddFinding, useUpdateFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { findingTypeGroups, scopeOfType } from "@/modules/dental-chart/guidance/findingTypeGroups";
import { relocate, SCOPE_PLACE, type AcceptedRelocation } from "@/modules/dental-chart/guidance/relocation";
import type { ClickedLocation } from "./FindingDialog";

const CONDITION_OPTIONS: SelectOption[] = [
  { value: "GOOD", label: "Buen estado" },
  { value: "POOR", label: "Mal estado" },
];

/** El formulario propiamente dicho, dentro de {@link import("./FindingDialog").FindingDialog}: la
 *  localización ya está decidida por el click y no se muestra como campo editable — solo
 *  tipo/subtipo/estado/grado, según lo que ese tipo exija. El tipo se elige en un selector con
 *  búsqueda sobre todo el catálogo (update-dental-chart-guidance D5): primero los de esta zona; uno
 *  de otra zona no se elige acá, se ofrece ir a donde se registra (nunca se guarda en una zona que
 *  no le corresponde). */
export function FindingDialogForm({
  open,
  onOpenChange,
  patientId,
  catalog,
  location,
  locationText,
  editingFinding,
  initialType,
  onBack,
  onRelocate,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  patientId: number;
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  location: ClickedLocation;
  locationText: string;
  editingFinding: DentalChartFindingResponse | null;
  /** Tipo ya elegido al llegar desde "¿No encuentras el hallazgo?" de otra localización (design D5). */
  initialType?: string;
  onBack?: () => void;
  /** El usuario aceptó ir a otra localización desde el buscador (design D5). */
  onRelocate?: (accepted: AcceptedRelocation) => void;
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
    : { ...findingFormDefaults(location), type: initialType ?? "" };

  const {
    register,
    handleSubmit,
    control,
    setError,
    setValue,
    formState: { errors },
  } = useForm<FindingFormValues>({
    resolver: zodResolver(buildFindingSchema(catalog)),
    mode: "onTouched",
    defaultValues: defaults,
  });

  const type = useWatch({ control, name: "type" });
  const subtype = useWatch({ control, name: "subtype" });

  // Abierta la lista del selector, Escape la cierra a ella y no al diálogo (ver GroupedCombobox).
  const [typeListOpen, setTypeListOpen] = useState(false);
  // Tipo de otra zona elegido en el selector: se muestra dónde va y la acción, sin tocar el formulario.
  const [elsewhere, setElsewhere] = useState<AcceptedRelocation | null>(null);
  const typeGroups = useMemo(
    () => findingTypeGroups(catalog, location.locationKind, !isEditing),
    [catalog, location.locationKind, isEditing],
  );
  const onPickType = (picked: string, setType: (value: string) => void) => {
    const scope = scopeOfType(catalog, picked);
    const relocation = scope ? relocate(location, scope) : ({ kind: "select" } as const);
    if (relocation.kind !== "select") {
      setElsewhere({ type: picked, relocation });
      return;
    }
    setElsewhere(null);
    // Lo que depende del tipo anterior deja de valer con el nuevo.
    setValue("subtype", "");
    setValue("condition", "");
    setValue("grade", "");
    setType(picked);
  };
  const subtypeOptions: SelectOption[] = useMemo(
    () =>
      subtypesFor(catalog, type).map((e) => {
        const code = e.subtype ?? "";
        return { value: code, label: subtypeOptionLabel(type, code, e.acronym ?? code) };
      }),
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
      <DialogContent
        className="sm:max-w-md"
        onEscapeKeyDown={(e) => {
          if (typeListOpen) e.preventDefault();
        }}
      >
        <DialogHeader>
          <DialogTitle>{isEditing ? "Editar hallazgo" : "Agregar hallazgo"}</DialogTitle>
          <DialogDescription>{locationText}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4 sm:grid-cols-2" noValidate>
          <Controller
            control={control}
            name="type"
            render={({ field }) => (
              <GroupedCombobox
                id="type"
                label="Tipo de hallazgo"
                required
                groups={typeGroups}
                value={field.value}
                onSelect={(picked) => onPickType(picked, field.onChange)}
                onBlur={field.onBlur}
                onOpenChange={setTypeListOpen}
                error={errors.type?.message}
                disabled={catalog == null}
                placeholder={catalog == null ? "Cargando…" : "Escribe o elige, ej.: caries"}
                emptyMessage="No hay ningún hallazgo con ese nombre en el catálogo."
              />
            )}
          />
          {elsewhere ? (
            <div role="status" className="flex flex-col gap-2 rounded-xl border bg-muted/40 p-3 text-sm sm:col-span-2">
              <p>
                <span className="font-medium">{findingTypeLabel(elsewhere.type)}</span> se registra{" "}
                {SCOPE_PLACE[scopeOfType(catalog, elsewhere.type) ?? location.locationKind]}, no{" "}
                {SCOPE_PLACE[location.locationKind]}.
              </p>
              <div>
                <Button type="button" size="sm" onClick={() => onRelocate?.(elsewhere)}>
                  {elsewhere.relocation.action}
                </Button>
              </div>
            </div>
          ) : null}
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
