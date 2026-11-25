import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { BusinessHoursResponse } from "@/modules/core/services/generated/model";
import { useReplaceBusinessHours } from "../hooks/useAppointmentsApi";
import { businessHoursSchema, type BusinessHoursValues } from "../schemas/businessHours";
import { BusinessHoursDayRow } from "./BusinessHoursDayRow";

/** La semana completa: se guarda entera, con las mismas reglas que el backend (schema `businessHours`). */
export function BusinessHoursForm({ hours }: { hours: BusinessHoursResponse }) {
  const replaceMutation = useReplaceBusinessHours();
  const {
    control,
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<BusinessHoursValues>({
    resolver: zodResolver(businessHoursSchema),
    mode: "onTouched",
    defaultValues: {
      days: (hours.days ?? []).map((d) => ({
        day: d.day,
        segments: d.segments.map((s) => ({ opens: s.opens, closes: s.closes })),
      })),
    },
  });

  return (
    <form onSubmit={handleSubmit((values) => replaceMutation.mutate(values))} noValidate className="flex flex-col gap-3">
      <ul className="flex flex-col divide-y divide-border rounded-2xl border border-border bg-card">
        {(hours.days ?? []).map((day, index) => (
          <BusinessHoursDayRow
            key={day.day}
            dayIndex={index}
            day={day.day}
            control={control}
            register={register}
            errors={errors.days?.[index]}
          />
        ))}
      </ul>
      <div className="flex justify-end">
        <Button type="submit" disabled={replaceMutation.isPending}>
          {replaceMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          Guardar horario
        </Button>
      </div>
    </form>
  );
}
