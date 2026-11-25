// Constantes de rutas de la aplicación.
export const PATHS = {
  ROOT: "/",
  LOGIN: "/auth/login",
  REGISTER: "/auth/register",
  /** Pedir la recuperación de la contraseña (solo sin sesión). */
  FORGOT_PASSWORD: "/auth/forgot-password",
  /** Elegir la contraseña nueva desde el enlace del correo (con o sin sesión). */
  RESET_PASSWORD: "/auth/reset-password",
  BRANDING: "/settings/branding",
  STAFF: "/settings/staff",
  BUSINESS_HOURS: "/settings/business-hours",
  /** La agenda semanal. Acepta `?patientId&date` para abrir el formulario de agendar precargado. */
  APPOINTMENTS: "/appointments",
  PATIENTS: "/patients",
  PATIENT_NEW: "/patients/new",
  PATIENT_DETAIL: "/patients/:id",
  PATIENT_EDIT: "/patients/:id/edit",
  CLINICAL_HISTORY: "/patients/:id/clinical-history",
  DENTAL_CHART: "/patients/:id/dental-chart",
  CONSULTATION_NOTES: "/patients/:id/consultations",
  /** De la persona, no de una sección: se llega desde el menú de la cuenta. */
  PROFILE: "/profile",
  /** Ruta anterior de la credencial: redirige a su sección en Mi perfil, para no romper enlaces guardados. */
  LEGACY_PROFESSIONAL_CREDENTIAL: "/profile/credential",
} as const;

/** Ancla de la sección de la credencial dentro de Mi perfil. */
export const CREDENTIAL_SECTION_ID = "credencial";
/** La credencial profesional, en su sección de Mi perfil. */
export const PROFILE_CREDENTIAL = `${PATHS.PROFILE}#${CREDENTIAL_SECTION_ID}`;

/** Construye la ruta de detalle de un paciente. */
export const patientDetailPath = (id: number) => `/patients/${id}`;
/** Construye la ruta de edición de un paciente. */
export const patientEditPath = (id: number) => `/patients/${id}/edit`;
/** Construye la ruta de la Historia Clínica de un paciente. */
export const clinicalHistoryPath = (id: number) => `/patients/${id}/clinical-history`;
/** Construye la ruta del odontograma de un paciente. */
export const dentalChartPath = (id: number) => `/patients/${id}/dental-chart`;
/** La Agenda con el formulario de agendar abierto y precargado (p. ej. la próxima cita de una nota). */
export const scheduleAppointmentPath = (patientId: number, date: string) =>
  `/appointments?patientId=${patientId}&date=${date}`;
/** Construye la ruta de las notas de consulta de un paciente. */
export const consultationNotesPath = (id: number) => `/patients/${id}/consultations`;
