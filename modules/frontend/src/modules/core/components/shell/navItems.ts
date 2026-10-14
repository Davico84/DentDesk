import type { LucideIcon } from "lucide-react";
import { House, Palette, Users } from "lucide-react";
import type { Role } from "@/store/useSessionStore";
import { canAccess, sections, type SectionId } from "@/modules/core/config/sections";

export interface NavItemConfig {
  id: SectionId;
  label: string;
  to: string;
  icon: LucideIcon;
  /** Solo coincide la ruta exacta (Inicio); el resto se activa también en sus subrutas. */
  end?: boolean;
}

/** Lo único que el menú agrega a cada sección: cómo se ve. La ruta y los roles vienen de `sections`. */
const PRESENTATION: Record<SectionId, Pick<NavItemConfig, "label" | "icon" | "end">> = {
  home: { label: "Inicio", icon: House, end: true },
  patients: { label: "Pacientes", icon: Users },
  branding: { label: "Marca", icon: Palette },
  staff: { label: "Personal", icon: Users },
};

/** Ítems de navegación visibles para un rol, en el orden de `sections`. */
export function navItemsFor(role: Role): NavItemConfig[] {
  return sections
    .filter((section) => canAccess(section, role))
    .map((section) => ({ id: section.id, to: section.path, ...PRESENTATION[section.id] }));
}

/** Id del cajón de navegación móvil (lo referencia el botón de menú con `aria-controls`). */
export const MOBILE_NAV_ID = "mobile-nav";
