import { describe, expect, it } from "vitest";
import type { MemberResponse, StaffInvitationResponse } from "@/modules/core/services/generated/model";
import { countByFilter, filterInvitations, filterMembers, normalizeForSearch } from "./staffFilters";

const MEMBERS: MemberResponse[] = [
  { id: 1, fullName: "María Ruiz", email: "maria@clinica.test", role: "MEMBER", status: "ACTIVE" },
  { id: 2, fullName: "Luis Pérez", email: "lperez@clinica.test", role: "MEMBER", status: "INACTIVE" },
  { id: 3, fullName: "Ana Admin", email: "ana@clinica.test", role: "ADMIN", status: "ACTIVE" },
];

const invitation = (id: number, state: StaffInvitationResponse["state"]): StaffInvitationResponse => ({
  id, email: `i${id}@x.test`, role: "MEMBER", state, createdAt: "2026-10-01T00:00:00Z",
  expiresAt: "2026-10-08T00:00:00Z", manageable: state === "PENDING" || state === "EXPIRED",
});

describe("staffFilters", () => {
  it("normaliza tildes y mayúsculas", () => {
    expect(normalizeForSearch("  María PÉREZ ")).toBe("maria perez");
  });

  it("busca por nombre sin distinguir tildes ni mayúsculas", () => {
    expect(filterMembers(MEMBERS, "MARIA").map((m) => m.id)).toEqual([1]);
    expect(filterMembers(MEMBERS, "perez").map((m) => m.id)).toEqual([2]);
  });

  it("busca por correo", () => {
    expect(filterMembers(MEMBERS, "lperez@").map((m) => m.id)).toEqual([2]);
  });

  it("una búsqueda vacía devuelve a todos", () => {
    expect(filterMembers(MEMBERS, "  ")).toHaveLength(3);
  });

  it("filtra invitaciones por estado y cuenta cada uno", () => {
    const list = [invitation(1, "PENDING"), invitation(2, "PENDING"), invitation(3, "EXPIRED"), invitation(4, "ACCEPTED")];

    expect(filterInvitations(list, "PENDING").map((i) => i.id)).toEqual([1, 2]);
    expect(filterInvitations(list, "ALL")).toHaveLength(4);
    expect(countByFilter(list)).toEqual({ ALL: 4, PENDING: 2, EXPIRED: 1, ACCEPTED: 1, REVOKED: 0 });
  });
});
