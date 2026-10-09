import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Plus, Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField, SelectField } from "@/modules/core/ui/form-field";
import type { BackgroundEntryResponse } from "@/modules/core/services/generated/model";
import {
  backgroundAltaSchema,
  toAltaRequest,
  toBajaRequest,
  type BackgroundAltaValues,
} from "@/modules/clinical-history/schemas/consultationNote";
import {
  useActiveBackground,
  useAddBackgroundEntry,
  useRemoveBackgroundEntry,
} from "@/modules/clinical-history/hooks/useConsultationNotesApi";
import { backgroundCategoryOptions } from "./noteOptions";
import { labelOf } from "./diagnosisOptions";

/**
 * Antecedentes nuevos dentro de la nota abierta (spec: <i>Antecedentes nuevos como asientos de alta
 * y baja</i>): un alta registra algo nuevo; una baja cierra un antecedente vigente de una consulta
 * anterior. Ninguno borra nada: la baja es otro asiento.
 */
export function BackgroundEntriesPanel({
  patientId,
  noteId,
  entries,
}: {
  patientId: number;
  noteId: number;
  entries: BackgroundEntryResponse[];
}) {
  const addEntry = useAddBackgroundEntry(patientId, noteId);
  const removeEntry = useRemoveBackgroundEntry(patientId, noteId);
  const { data: active } = useActiveBackground(patientId);
  const closedHere = new Set(entries.filter((e) => e.kind === "BAJA").map((e) => e.closesEntryId));
  // Solo se da de baja lo que vino de una consulta sellada: la Historia y la ficha no son asientos.
  const closable = (active?.items ?? []).filter((item) => item.source === "NOTA" && !closedHere.has(item.entryId));

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<BackgroundAltaValues>({
    resolver: zodResolver(backgroundAltaSchema),
    mode: "onTouched",
    defaultValues: { category: "", description: "" },
  });

  const onSubmit = (values: BackgroundAltaValues) => {
    addEntry.mutate(toAltaRequest(values), { onSuccess: () => reset({ category: "", description: "" }) });
  };

  return (
    <div className="flex flex-col gap-4">
      {entries.length === 0 ? (
        <p className="text-sm text-muted-foreground">Sin antecedentes nuevos en esta consulta.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {entries.map((entry) => (
            <li key={entry.id} className="flex items-center justify-between gap-3 rounded-xl border px-3 py-2 text-sm">
              <div>
                <span className="font-medium">{entry.kind === "ALTA" ? "Alta" : "Baja"}</span>
                <span className="ml-2 text-muted-foreground">{labelOf(backgroundCategoryOptions, entry.category)}</span>
                <span className="ml-2">{entry.description ?? describeClosed(entry, active?.items)}</span>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label="Quitar antecedente"
                disabled={removeEntry.isPending}
                onClick={() => entry.id != null && removeEntry.mutate(entry.id)}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="grid gap-4 sm:grid-cols-2" noValidate>
        <SelectField
          label="Categoría"
          name="category"
          register={register}
          error={errors.category}
          options={backgroundCategoryOptions}
          placeholder="Elige una categoría"
          required
        />
        <FormField label="Descripción" name="description" register={register} error={errors.description} required />
        <div className="sm:col-span-2">
          <Button type="submit" size="sm" className="rounded-xl" disabled={addEntry.isPending}>
            {addEntry.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : <Plus className="mr-2 size-4" />}
            Registrar antecedente
          </Button>
        </div>
      </form>

      {closable.length > 0 ? (
        <div className="border-t pt-4">
          <h4 className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Dar de baja un antecedente de consultas anteriores
          </h4>
          <ul className="flex flex-col gap-2">
            {closable.map((item) => (
              <li key={item.entryId} className="flex items-center justify-between gap-3 text-sm">
                <span>
                  <span className="font-medium">{item.description}</span>
                  <span className="ml-2 text-muted-foreground">{labelOf(backgroundCategoryOptions, item.category)}</span>
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={addEntry.isPending}
                  onClick={() => item.entryId != null && addEntry.mutate(toBajaRequest(item.entryId))}
                >
                  Dar de baja
                </Button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}

/** Una baja sin descripción se lee por lo que cierra. */
function describeClosed(
  entry: BackgroundEntryResponse,
  items: { entryId?: number; description?: string }[] | undefined,
): string {
  return items?.find((item) => item.entryId === entry.closesEntryId)?.description ?? "antecedente anterior";
}
