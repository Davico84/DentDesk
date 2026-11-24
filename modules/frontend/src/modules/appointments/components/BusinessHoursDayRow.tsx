import { useFieldArray, type Control, type FieldErrors, type UseFormRegister } from "react-hook-form";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { Input } from "@/modules/core/ui/input";
import type { BusinessHoursDayDtoDay } from "@/modules/core/services/generated/model";
import type { BusinessHoursValues } from "../schemas/businessHours";

const DAY_LABEL: Record<BusinessHoursDayDtoDay, string> = {
  MONDAY: "Lunes",
  TUESDAY: "Martes",
  WEDNESDAY: "Miércoles",
  THURSDAY: "Jueves",
  FRIDAY: "Viernes",
  SATURDAY: "Sábado",
  SUNDAY: "Domingo",
};

type DayErrors = FieldErrors<BusinessHoursValues["days"][number]>;

/** Un día del horario: sus tramos (agregar, quitar) o cerrado si no tiene ninguno. Hasta 6 tramos. */
export function BusinessHoursDayRow({
  dayIndex,
  day,
  control,
  register,
  errors,
}: {
  dayIndex: number;
  day: BusinessHoursDayDtoDay;
  control: Control<BusinessHoursValues>;
  register: UseFormRegister<BusinessHoursValues>;
  errors: DayErrors | undefined;
}) {
  const { fields, append, remove } = useFieldArray({ control, name: `days.${dayIndex}.segments` });
  const label = DAY_LABEL[day];

  return (
    <li className="flex flex-col gap-2 p-3 sm:flex-row sm:items-start" aria-label={label}>
      <span className="w-24 shrink-0 pt-2 text-sm font-medium">{label}</span>
      <div className="flex flex-1 flex-col gap-2">
        {fields.length === 0 ? <p className="pt-2 text-sm text-muted-foreground">Cerrado</p> : null}
        {fields.map((field, segmentIndex) => {
          const segmentError = errors?.segments?.[segmentIndex];
          const message = segmentError?.opens?.message ?? segmentError?.closes?.message;
          const errorId = `hours-${day}-${segmentIndex}-error`;
          return (
            <div key={field.id} className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <Input
                  type="time"
                  aria-label={`${label}, apertura del tramo ${segmentIndex + 1}`}
                  aria-invalid={segmentError?.opens ? true : undefined}
                  aria-describedby={message ? errorId : undefined}
                  className="h-10 w-32 rounded-xl"
                  {...register(`days.${dayIndex}.segments.${segmentIndex}.opens`)}
                />
                <span className="text-sm text-muted-foreground" aria-hidden="true">
                  a
                </span>
                <Input
                  type="time"
                  aria-label={`${label}, cierre del tramo ${segmentIndex + 1}`}
                  aria-invalid={segmentError?.closes ? true : undefined}
                  aria-describedby={message ? errorId : undefined}
                  className="h-10 w-32 rounded-xl"
                  {...register(`days.${dayIndex}.segments.${segmentIndex}.closes`)}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => remove(segmentIndex)}
                  aria-label={`Quitar el tramo ${segmentIndex + 1} del ${label.toLowerCase()}`}
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
              {message ? (
                <p id={errorId} role="alert" className="text-xs text-destructive">
                  {message}
                </p>
              ) : null}
            </div>
          );
        })}
        {fields.length < 6 ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="self-start"
            onClick={() => append({ opens: "09:00", closes: "13:00" })}
          >
            <Plus className="mr-1 size-4" />
            Agregar tramo
          </Button>
        ) : null}
      </div>
    </li>
  );
}
