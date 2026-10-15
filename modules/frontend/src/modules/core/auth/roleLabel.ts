import type { Role } from "@/store/useSessionStore";

/** Nombre visible de cada rol de membresía. Única fuente: lo usan el shell y el registro por invitación. */
export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: "Administrador",
  MEMBER: "Personal",
};
