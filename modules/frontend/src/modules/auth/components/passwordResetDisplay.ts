import type { PasswordResetResponse } from "@/modules/core/services/generated/model";

/** Lo que la pantalla de restablecimiento le deja al login en el estado de navegación (D7). */
export interface PasswordResetNavigationState {
  passwordReset: PasswordResetResponse;
}

/** El resultado del restablecimiento que llegó en el estado de navegación, si llegó uno. */
export function readPasswordReset(state: unknown): PasswordResetResponse | null {
  const value = (state as Partial<PasswordResetNavigationState> | null)?.passwordReset;
  return value && typeof value.outcome === "string" ? value : null;
}

function hourMinute(iso: string): string {
  return new Date(iso).toLocaleTimeString("es-PE", { hour: "2-digit", minute: "2-digit", hour12: false });
}

/**
 * El aviso del login tras restablecer (spec authentication: Estado de la cuenta al restablecer la contraseña). Solo
 * lo ve quien tenía un enlace vigente.
 */
export function passwordResetMessage({ outcome, lockedUntil }: PasswordResetResponse): string {
  switch (outcome) {
    case "LOCKED":
      return lockedUntil
        ? `Tu contraseña se cambió. Por los intentos fallidos, podrás iniciar sesión a las ${hourMinute(lockedUntil)}.`
        : "Tu contraseña se cambió. Por los intentos fallidos, podrás iniciar sesión en unos minutos.";
    case "ACCESS_DISABLED":
      return "Tu contraseña se cambió, pero tu acceso está desactivado. Contacta al administrador de tu clínica.";
    default:
      return "Listo. Ya puedes iniciar sesión con tu contraseña nueva.";
  }
}
