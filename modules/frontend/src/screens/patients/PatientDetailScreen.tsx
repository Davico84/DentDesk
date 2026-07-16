import { PatientDetail } from "@/modules/patients/components/PatientDetail";

/**
 * Pantalla de consulta de una ficha: hoja de composición (docs/frontend.md §1, §4.6). Solo monta
 * la feature; la lógica y los componentes viven en `modules/patients/`.
 */
export function PatientDetailScreen() {
  return <PatientDetail />;
}
