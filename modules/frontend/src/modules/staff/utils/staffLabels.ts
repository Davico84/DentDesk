import type { StaffInvitationResponseState } from "@/modules/core/services/generated/model";

/** Nombre visible de cada estado de invitación (filtros, badges, mensajes). */
export const INVITATION_STATE_LABEL: Record<StaffInvitationResponseState, string> = {
  PENDING: "Pendiente",
  EXPIRED: "Vencida",
  ACCEPTED: "Aceptada",
  REVOKED: "Revocada",
};

/** Nombre de cada filtro de la pestaña Invitaciones. */
export const INVITATION_FILTER_LABEL = {
  ALL: "Todas",
  PENDING: "Pendientes",
  EXPIRED: "Vencidas",
  ACCEPTED: "Aceptadas",
  REVOKED: "Revocadas",
} as const;
