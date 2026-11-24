import { PageContainer } from "@/modules/core/components/PageContainer";
import { useBusinessHours } from "../hooks/useAppointmentsApi";
import { AgendaError } from "./AgendaError";
import { BusinessHoursForm } from "./BusinessHoursForm";
import { BusinessHoursSkeleton } from "./BusinessHoursSkeleton";

/** Horario de atención de la clínica (solo ADMIN): carga el vigente y lo edita por día y tramos. */
export function BusinessHoursEditor() {
  const hoursQuery = useBusinessHours();
  return (
    <PageContainer className="flex flex-col gap-4 py-6">
      <div>
        <h1 className="text-xl font-semibold">Horario de atención</h1>
        <p className="text-sm text-muted-foreground">
          Toda cita cae completa dentro de un tramo. Un día sin tramos está cerrado. Cambiarlo no mueve las citas ya
          agendadas.
        </p>
      </div>
      {hoursQuery.isLoading ? <BusinessHoursSkeleton /> : null}
      {hoursQuery.isError ? <AgendaError onRetry={() => void hoursQuery.refetch()} /> : null}
      {hoursQuery.data ? <BusinessHoursForm key={JSON.stringify(hoursQuery.data.days)} hours={hoursQuery.data} /> : null}
    </PageContainer>
  );
}
