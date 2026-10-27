import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField } from "@/modules/core/ui/form-field";
import type { Credential } from "@/modules/core/services/generated/model";
import { applyServerFieldErrors, getApiProblem, getUserFriendlyError } from "@/modules/core/utils/apiError";
import { useDeclareCredential } from "../hooks/useProfessionalCredential";
import { professionalCredentialSchema, type ProfessionalCredentialValues } from "../schemas/professionalCredential";

const COP_TAKEN = "/errors/credential-number-taken";
const DATE_IN_PAST = "/errors/licensed-until-in-past";

/**
 * Declarar o renovar la credencial propia (spec staff: Declarar la credencial profesional propia). La validación
 * vive en el schema; los rechazos del servidor (número de otra cuenta, fecha pasada en la hora de Perú) se pintan
 * en su campo. La declaración jurada se acepta cada vez: renovar es volver a declarar.
 */
export function ProfessionalCredentialForm({ credential }: { credential?: Credential }) {
  const declareMutation = useDeclareCredential();
  const { register, handleSubmit, setError, setValue, formState: { errors } } = useForm<ProfessionalCredentialValues>({
    resolver: zodResolver(professionalCredentialSchema),
    mode: "onTouched",
    defaultValues: {
      copNumber: credential?.copNumber ?? "",
      licensedUntil: credential?.licensedUntil ?? "",
      sworn: false,
    },
  });

  const submit = (values: ProfessionalCredentialValues) =>
    declareMutation.mutate(values, {
      // Cada declaración exige volver a aceptar la declaración jurada.
      onSuccess: () => setValue("sworn", false),
      onError: (error) => {
        const type = getApiProblem(error)?.type;
        if (type === COP_TAKEN) setError("copNumber", { type: "server", message: getUserFriendlyError(error) });
        else if (type === DATE_IN_PAST) setError("licensedUntil", { type: "server", message: getUserFriendlyError(error) });
        else if (!applyServerFieldErrors(error, setError)) {
          setError("root", { type: "server", message: getUserFriendlyError(error) });
        }
      },
    });

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="Número de colegiatura (COP)"
          name="copNumber"
          inputMode="numeric"
          maxLength={10}
          placeholder="12345"
          register={register}
          error={errors.copNumber}
          required
        />
        <FormField
          label="Habilitado hasta"
          name="licensedUntil"
          type="date"
          hint="El último día de tu habilitación, en la hora de Perú."
          register={register}
          error={errors.licensedUntil}
          required
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="flex cursor-pointer items-start gap-3 rounded-xl border bg-muted/30 p-4 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 size-5 shrink-0 rounded border-input accent-primary"
            aria-invalid={errors.sworn ? true : undefined}
            aria-describedby={errors.sworn ? "sworn-error" : undefined}
            {...register("sworn")}
          />
          <span>
            Declaro bajo juramento que el número de colegiatura es mío y que estoy habilitado por el Colegio
            Odontológico del Perú hasta la fecha indicada. Si los datos son falsos, la responsabilidad es mía.
          </span>
        </label>
        {errors.sworn ? (
          <p id="sworn-error" role="alert" className="text-sm text-destructive">
            {errors.sworn.message}
          </p>
        ) : null}
      </div>

      {errors.root ? (
        <p role="alert" className="text-sm text-destructive">
          {errors.root.message}
        </p>
      ) : null}

      <div className="flex justify-end">
        <Button type="submit" className="h-11 rounded-xl" disabled={declareMutation.isPending}>
          {declareMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          {credential ? "Guardar cambios" : "Declarar credencial"}
        </Button>
      </div>
    </form>
  );
}
