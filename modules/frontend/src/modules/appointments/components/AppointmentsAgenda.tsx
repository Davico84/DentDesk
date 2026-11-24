import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { PageContainer } from "@/modules/core/components/PageContainer";
import { useMediaQuery } from "@/modules/core/hooks/useMediaQuery";
import type { AppointmentResponse } from "@/modules/core/services/generated/model";
import { CLINIC_TIMEZONE } from "@/modules/core/utils/clinicDate";
import { addDays, instantToClinicLocal, mondayOf } from "@/modules/core/utils/clinicTime";
import { useBusinessHours, useSchedulableDentists, useWeekAppointments } from "../hooks/useAppointmentsApi";
import { gridBounds, placeAppointments, weekDates } from "../utils/agendaLayout";
import { AgendaError } from "./AgendaError";
import { AgendaSkeleton } from "./AgendaSkeleton";
import { AgendaToolbar } from "./AgendaToolbar";
import { AppointmentDetailDialog } from "./AppointmentDetailDialog";
import { longDayLabel, weekRangeLabel } from "./appointmentDisplay";
import { ScheduleAppointmentDialog, type ScheduleInitialValues } from "./ScheduleAppointmentDialog";
import { WeekGrid } from "./WeekGrid";

/** Desde `md` se ve la semana; debajo, un día por vez (spec "Celular"). */
const WEEK_QUERY = "(min-width: 768px)";

/**
 * La agenda de la clínica (add-appointments design.md D9): semana de lunes a domingo en la hora de la clínica, citas
 * de todos los odontólogos con un color por odontólogo, filtro por odontólogo, horas fuera del horario sombreadas.
 * Un hueco libre abre "agendar" con día y hora; una cita, su detalle. `?patientId&date` (la próxima cita de una nota)
 * abre "agendar" con paciente y fecha.
 */
export function AppointmentsAgenda() {
  const [searchParams, setSearchParams] = useSearchParams();
  const hoursQuery = useBusinessHours();
  const timeZone = hoursQuery.data?.timezone ?? CLINIC_TIMEZONE;
  const today = instantToClinicLocal(new Date(), timeZone).date;
  const linkedDate = searchParams.get("date");
  const linkedPatient = searchParams.get("patientId");

  const [day, setDay] = useState<string | null>(null);
  const anchor = day ?? linkedDate ?? today;
  const monday = mondayOf(anchor);
  const sunday = addDays(monday, 6);
  const wide = useMediaQuery(WEEK_QUERY);
  const [dentistFilter, setDentistFilter] = useState("");
  const [scheduling, setScheduling] = useState<ScheduleInitialValues | null>(
    linkedPatient || linkedDate ? { patientId: linkedPatient ?? undefined, date: linkedDate ?? undefined } : null,
  );
  const [selected, setSelected] = useState<AppointmentResponse | null>(null);

  const dentistsQuery = useSchedulableDentists();
  const weekQuery = useWeekAppointments(monday, sunday, dentistFilter ? Number(dentistFilter) : undefined);

  const dentists = useMemo(() => dentistsQuery.data ?? [], [dentistsQuery.data]);
  const dentistIds = useMemo(() => dentists.map((d) => d.userId), [dentists]);
  const dates = wide ? weekDates(monday) : [anchor];
  const placed = useMemo(() => placeAppointments(weekQuery.data ?? [], timeZone), [weekQuery.data, timeZone]);
  const bounds = gridBounds(hoursQuery.data, weekDates(monday), placed);

  const closeScheduling = () => {
    setScheduling(null);
    if (linkedPatient || linkedDate) {
      setSearchParams({}, { replace: true });
    }
  };

  const loading = hoursQuery.isLoading || dentistsQuery.isLoading || weekQuery.isLoading;
  const failed = hoursQuery.isError || dentistsQuery.isError || weekQuery.isError;
  const step = wide ? 7 : 1;

  return (
    <PageContainer className="flex flex-col gap-4 py-6">
      <h1 className="text-xl font-semibold">Agenda</h1>
      <AgendaToolbar
        title={wide ? weekRangeLabel(monday, sunday) : longDayLabel(anchor)}
        previousLabel={wide ? "Semana anterior" : "Día anterior"}
        nextLabel={wide ? "Semana siguiente" : "Día siguiente"}
        onPrevious={() => setDay(addDays(anchor, -step))}
        onNext={() => setDay(addDays(anchor, step))}
        onToday={() => setDay(today)}
        dentists={dentists}
        dentistFilter={dentistFilter}
        onDentistFilter={setDentistFilter}
        onSchedule={() => setScheduling({ date: anchor })}
      />
      {loading ? <AgendaSkeleton /> : null}
      {!loading && failed ? (
        <AgendaError
          onRetry={() => {
            void hoursQuery.refetch();
            void dentistsQuery.refetch();
            void weekQuery.refetch();
          }}
        />
      ) : null}
      {!loading && !failed ? (
        <WeekGrid
          dates={dates}
          today={today}
          hours={hoursQuery.data}
          bounds={bounds}
          placed={placed}
          dentistIds={dentistIds}
          onSlot={(date, time) => setScheduling({ date, time })}
          onAppointment={setSelected}
        />
      ) : null}
      {scheduling && !loading ? (
        <ScheduleAppointmentDialog
          open
          onOpenChange={(open) => !open && closeScheduling()}
          timeZone={timeZone}
          dentists={dentists}
          initial={scheduling}
        />
      ) : null}
      {selected ? (
        <AppointmentDetailDialog
          key={selected.id}
          appointment={selected}
          timeZone={timeZone}
          dentists={dentists}
          onClose={() => setSelected(null)}
        />
      ) : null}
    </PageContainer>
  );
}
