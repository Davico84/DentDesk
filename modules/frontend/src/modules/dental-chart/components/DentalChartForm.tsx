import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Smile } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField } from "@/modules/core/ui/form-field";
import { createDefaults, dentalChartCreateSchema, toCreateRequest, type DentalChartCreateValues } from "@/modules/dental-chart/schemas/dentalChart";
import { useCreateDentalChart, useDentalChart } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { DentalChartEditor } from "./DentalChartEditor";

/** Feature del odontograma de un paciente: crear el bloque inicial, o mostrar el ya existente. */
export function DentalChartForm({ patientId }: { patientId: number }) {
  const { data: block, isLoading, isError, error } = useDentalChart(patientId);
  const createChart = useCreateDentalChart(patientId);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<DentalChartCreateValues>({
    resolver: zodResolver(dentalChartCreateSchema),
    mode: "onTouched",
    defaultValues: createDefaults(),
  });

  const notFound = isError && isAxios404(error);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (notFound || !block) {
    return (
      <div className="flex flex-col items-center gap-4 rounded-2xl border bg-card p-8 text-center">
        <Smile className="size-8 text-muted-foreground" />
        <div>
          <p className="font-medium">Este paciente aún no tiene odontograma</p>
          <p className="text-sm text-muted-foreground">
            Es requisito, junto con la Historia Clínica, para completar la admisión.
          </p>
        </div>
        <form
          onSubmit={handleSubmit((values) => createChart.mutate(toCreateRequest(values)))}
          className="flex w-full max-w-xs flex-col items-center gap-3"
          noValidate
        >
          <FormField label="Fecha del examen" name="examinedOn" type="date" register={register} error={errors.examinedOn} required />
          <Button type="submit" disabled={createChart.isPending}>
            {createChart.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            Crear odontograma
          </Button>
        </form>
      </div>
    );
  }

  return <DentalChartEditor patientId={patientId} />;
}

function isAxios404(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "response" in error &&
    (error as { response?: { status?: number } }).response?.status === 404
  );
}
