/**
 * Fechas calendario en la zona horaria de la clínica. Primitivo genérico de `core`.
 *
 * Un "hoy" sacado de `new Date().toISOString().slice(0, 10)` es el día en **UTC**: en Lima, desde
 * las 19:00 ya es "mañana", y el servidor rechaza esa fecha por futura. Toda fecha calendario que se
 * compare con el servidor sale de aquí.
 */

/**
 * Zona horaria de la clínica. Espejo de `OrganizationSettings.DEFAULT_TIMEZONE`: hoy ninguna clínica
 * puede cambiarla (no hay endpoint) y la API no la expone. Si eso cambia, debe llegar del servidor.
 */
export const CLINIC_TIMEZONE = "America/Lima";

/** Día calendario (`YYYY-MM-DD`) de un instante en la zona de la clínica. */
export function clinicDay(instant: Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: CLINIC_TIMEZONE }).format(instant);
}

/** Hoy (`YYYY-MM-DD`) en la zona de la clínica. */
export function clinicToday(): string {
  return clinicDay(new Date());
}
