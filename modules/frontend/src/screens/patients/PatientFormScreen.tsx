import { PatientForm } from "@/modules/patients/components/PatientForm";

/**
 * Pantalla de alta/edición de paciente: hoja de composición (docs/frontend.md §1, §4.6). Solo
 * monta la feature; la lógica y los componentes viven en `modules/patients/`.
 */
export function PatientFormScreen() {
  return <PatientForm />;
}
