import type {
  MemberResponse,
  StaffInvitationResponse,
  StaffInvitationResponseState,
} from "@/modules/core/services/generated/model";
import { normalizeForSearch } from "@/modules/core/utils/search";

/** Miembros cuyo nombre o correo contienen la búsqueda. Búsqueda vacía: todos. */
export function filterMembers(members: MemberResponse[], query: string): MemberResponse[] {
  const needle = normalizeForSearch(query);
  if (!needle) return members;
  return members.filter((m) =>
    [m.fullName ?? "", m.email ?? ""].some((field) => normalizeForSearch(field).includes(needle)),
  );
}

export type InvitationFilter = "ALL" | StaffInvitationResponseState;

/** Orden de los filtros en pantalla: lo accionable primero. */
export const INVITATION_FILTERS: InvitationFilter[] = ["ALL", "PENDING", "EXPIRED", "ACCEPTED", "REVOKED"];

export function filterInvitations(list: StaffInvitationResponse[], filter: InvitationFilter): StaffInvitationResponse[] {
  return filter === "ALL" ? list : list.filter((i) => i.state === filter);
}

/** Cuántas hay en cada filtro, para mostrarlo junto a cada uno. */
export function countByFilter(list: StaffInvitationResponse[]): Record<InvitationFilter, number> {
  const counts: Record<InvitationFilter, number> = { ALL: list.length, PENDING: 0, EXPIRED: 0, ACCEPTED: 0, REVOKED: 0 };
  for (const invitation of list) counts[invitation.state] += 1;
  return counts;
}
