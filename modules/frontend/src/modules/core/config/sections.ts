import type { Role } from "@/store/useSessionStore";
import { PATHS } from "@/routes/paths";

/** Secciones de la zona privada. Cada capacidad nueva agrega la suya al construirse. */
export type SectionId = "home" | "patients" | "branding" | "staff";

/**
 * Política de una sección: dónde vive y quién la ve (`add-app-shell` design.md D1). Sin `roles` =
 * cualquier usuario autenticado. De esta lista derivan el menú (`navItems`, que solo agrega
 * etiqueta e ícono) y el árbol de rutas (`routes/index.tsx`): roles y rutas no se repiten en otro
 * sitio.
 *
 * Ocultar o bloquear en el frontend NO autoriza: cada endpoint restringido lleva su
 * `@PreAuthorize` en el backend.
 */
export interface SectionConfig {
  id: SectionId;
  path: string;
  roles?: readonly Role[];
}

export const sections: readonly SectionConfig[] = [
  { id: "home", path: PATHS.ROOT },
  // Pacientes y todo lo que cuelga de su ficha (Historia, odontograma, consultas).
  { id: "patients", path: PATHS.PATIENTS },
  { id: "branding", path: PATHS.BRANDING, roles: ["ADMIN"] },
  { id: "staff", path: PATHS.STAFF, roles: ["ADMIN"] },
];

export function sectionById(id: SectionId): SectionConfig {
  const section = sections.find((s) => s.id === id);
  if (!section) throw new Error(`Sección no declarada: ${id}`);
  return section;
}

/** ¿Puede este rol entrar en la sección (y en todas sus subrutas)? */
export function canAccess(section: SectionConfig, role: Role): boolean {
  return !section.roles || section.roles.includes(role);
}
