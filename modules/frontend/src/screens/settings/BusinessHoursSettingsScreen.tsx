import { BusinessHoursEditor } from "@/modules/appointments/components/BusinessHoursEditor";

/** Horario de atención de la clínica (solo ADMIN). La feature vive en el módulo `appointments`. */
export function BusinessHoursSettingsScreen() {
  return <BusinessHoursEditor />;
}
