import type { Role } from "@/store/useSessionStore";
import { PATHS } from "@/routes/paths";

/**
 * Ruta de inicio según el rol. Por ahora ambos van al panel raíz; cuando existan
 * paneles diferenciados (admin vs. clínico) se ajusta aquí sin tocar los guards.
 */
export function roleHome(role: Role): string {
  switch (role) {
    case "ADMIN":
      return PATHS.ROOT;
    case "MEMBER":
      return PATHS.ROOT;
    default:
      return PATHS.ROOT;
  }
}
