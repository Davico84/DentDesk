import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/modules/core/ui/dialog";
import { SelectField, TextareaField, type SelectOption } from "@/modules/core/ui/form-field";
import {
  correctionSchema,
  toCorrectionRequest,
  type CorrectionValues,
} from "@/modules/clinical-history/schemas/consultationNote";
import { useAddCorrection } from "@/modules/clinical-history/hooks/useConsultationNotesApi";

const EMPTY: CorrectionValues = { field: "", correctedText: "", reason: "" };

/**
 * Corrección de un error material (DS 009-2017-SA art. 47 c, spec: <i>Corrección de un error
 * material como registro nuevo</i>). Solo ofrece los campos que tenían texto: corregir un vacío es
 * una omisión, que la norma excluye; y los diagnósticos no están entre los campos.
 */
export function CorrectionDialog({
  patientId,
  noteId,
  fields,
  open,
  onOpenChange,
}: {
  patientId: number;
  noteId: number;
  fields: SelectOption[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const addCorrection = useAddCorrection(patientId, noteId);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CorrectionValues>({ resolver: zodResolver(correctionSchema), mode: "onTouched", defaultValues: EMPTY });

  const close = (next: boolean) => {
    if (!next) reset(EMPTY);
    onOpenChange(next);
  };

  const onSubmit = (values: CorrectionValues) =>
    addCorrection.mutate(toCorrectionRequest(values), { onSuccess: () => close(false) });

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Corregir un error material</DialogTitle>
          <DialogDescription>
            La corrección queda como un registro nuevo, con su fecha y tu usuario. El texto original no se borra y se
            sigue mostrando junto a la corrección.
          </DialogDescription>
        </DialogHeader>
        <form id="correction-form" onSubmit={handleSubmit(onSubmit)} className="grid gap-4" noValidate>
          <SelectField
            label="Campo con el error"
            name="field"
            register={register}
            error={errors.field}
            options={fields}
            placeholder="Elige el campo"
            required
          />
          <TextareaField label="Texto correcto" name="correctedText" register={register} error={errors.correctedText} />
          <TextareaField label="Motivo de la corrección" name="reason" register={register} error={errors.reason} rows={2} />
        </form>
        <DialogFooter className="gap-2 sm:gap-2">
          <Button variant="ghost" onClick={() => close(false)} disabled={addCorrection.isPending}>
            Cancelar
          </Button>
          <Button type="submit" form="correction-form" disabled={addCorrection.isPending}>
            {addCorrection.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            Registrar corrección
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
