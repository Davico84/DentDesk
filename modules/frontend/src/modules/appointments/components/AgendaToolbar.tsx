import { ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { SchedulableDentistResponse } from "@/modules/core/services/generated/model";

/**
 * Cabecera de la agenda: navegación (semana, o día en el celular), volver a hoy, filtro por odontólogo y agendar.
 */
export function AgendaToolbar({
  title,
  previousLabel,
  nextLabel,
  onPrevious,
  onNext,
  onToday,
  dentists,
  dentistFilter,
  onDentistFilter,
  onSchedule,
}: {
  title: string;
  previousLabel: string;
  nextLabel: string;
  onPrevious: () => void;
  onNext: () => void;
  onToday: () => void;
  dentists: SchedulableDentistResponse[];
  dentistFilter: string;
  onDentistFilter: (value: string) => void;
  onSchedule: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="flex items-center gap-1">
        <Button type="button" variant="outline" size="icon" onClick={onPrevious} aria-label={previousLabel}>
          <ChevronLeft className="size-4" />
        </Button>
        <Button type="button" variant="outline" size="icon" onClick={onNext} aria-label={nextLabel}>
          <ChevronRight className="size-4" />
        </Button>
        <Button type="button" variant="ghost" onClick={onToday}>
          Hoy
        </Button>
      </div>
      <h2 className="min-w-0 flex-1 truncate text-base font-semibold capitalize" aria-live="polite">
        {title}
      </h2>
      <label className="sr-only" htmlFor="agenda-dentist-filter">
        Filtrar por odontólogo
      </label>
      <select
        id="agenda-dentist-filter"
        value={dentistFilter}
        onChange={(event) => onDentistFilter(event.target.value)}
        className="h-9 rounded-xl border border-input bg-card px-3 text-sm focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring"
      >
        <option value="">Todos los odontólogos</option>
        {dentists.map((dentist) => (
          <option key={dentist.userId} value={String(dentist.userId)}>
            {dentist.fullName ?? `Odontólogo ${dentist.userId}`}
          </option>
        ))}
      </select>
      <Button type="button" onClick={onSchedule}>
        <Plus className="mr-1 size-4" />
        Agendar
      </Button>
    </div>
  );
}
