// Constantes de rutas de la aplicación.
export const PATHS = {
  ROOT: "/",
  LOGIN: "/auth/login",
  REGISTER: "/auth/register",
  BRANDING: "/settings/branding",
  PATIENTS: "/patients",
  PATIENT_NEW: "/patients/new",
  PATIENT_DETAIL: "/patients/:id",
  PATIENT_EDIT: "/patients/:id/edit",
  CLINICAL_HISTORY: "/patients/:id/clinical-history",
  DENTAL_CHART: "/patients/:id/dental-chart",
} as const;

/** Construye la ruta de detalle de un paciente. */
export const patientDetailPath = (id: number) => `/patients/${id}`;
/** Construye la ruta de edición de un paciente. */
export const patientEditPath = (id: number) => `/patients/${id}/edit`;
/** Construye la ruta de la Historia Clínica de un paciente. */
export const clinicalHistoryPath = (id: number) => `/patients/${id}/clinical-history`;
/** Construye la ruta del odontograma de un paciente. */
export const dentalChartPath = (id: number) => `/patients/${id}/dental-chart`;
