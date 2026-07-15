import { PatientList } from "@/modules/patients/components/PatientList";

/**
 * Pantalla del listado de pacientes: hoja de composición (docs/frontend.md §1, §4.6). Solo monta
 * la feature del módulo; toda la lógica y los componentes viven en `modules/patients/`.
 */
export function PatientListScreen() {
  return <PatientList />;
}
