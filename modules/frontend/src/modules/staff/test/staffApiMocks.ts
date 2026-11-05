import { http, HttpResponse } from "msw";
import type { MemberResponse, StaffInvitationResponse } from "@/modules/core/services/generated/model";

/**
 * Mocks de la API de `staff` para los tests del módulo. MSW corre con `onUnhandledRequest: "error"` y el repo
 * no registra handlers globales: cada test los instala con `server.use(...staffHandlers(...))`.
 */
export const ADMIN_ID = 1;

const none = { copNumber: null, licensedUntil: null, expiredOn: null };
export const MEMBERS: MemberResponse[] = [
  { id: ADMIN_ID, fullName: "Ana Admin", email: "ana@clinica.test", role: "ADMIN", status: "ACTIVE",
    clinicalCapacity: { state: "ENABLED", copNumber: "11111", licensedUntil: "2099-12-31", expiredOn: null } },
  { id: 2, fullName: "María Ruiz", email: "maria@clinica.test", role: "MEMBER", status: "ACTIVE",
    clinicalCapacity: { state: "ENABLED", copNumber: "22222", licensedUntil: "2099-06-30", expiredOn: null } },
  { id: 3, fullName: "Luis Pérez", email: "luis@clinica.test", role: "MEMBER", status: "INACTIVE",
    clinicalCapacity: { state: "INACTIVE_MEMBERSHIP", ...none } },
  { id: 4, fullName: "Otra Admin", email: "otra@clinica.test", role: "ADMIN", status: "ACTIVE",
    clinicalCapacity: { state: "NO_CREDENTIAL", ...none } },
  { id: 5, fullName: "Rosa Vega", email: "rosa@clinica.test", role: "MEMBER", status: "ACTIVE",
    clinicalCapacity: { state: "REVOKED", copNumber: "55555", licensedUntil: "2099-01-31", expiredOn: null } },
  { id: 6, fullName: "Pedro Soto", email: "pedro@clinica.test", role: "MEMBER", status: "ACTIVE",
    clinicalCapacity: { state: "EXPIRED", copNumber: "66666", licensedUntil: "2026-10-20", expiredOn: "2026-10-20" } },
];

const base = { createdAt: "2026-10-10T15:00:00Z", expiresAt: "2026-10-17T15:00:00Z" };
export const INVITATIONS: StaffInvitationResponse[] = [
  { id: 10, email: "pendiente@x.test", role: "MEMBER", state: "PENDING", manageable: true, ...base },
  { id: 11, email: "vencida@x.test", role: "MEMBER", state: "EXPIRED", manageable: true, ...base },
  { id: 12, email: "aceptada@x.test", role: "MEMBER", state: "ACCEPTED", manageable: false, ...base },
  { id: 13, email: "admin@x.test", role: "ADMIN", state: "ACCEPTED", manageable: false, ...base },
];

export type StaffCalls = {
  statusChanges: { id: string; body: unknown }[];
  capacityChanges: { id: string; body: unknown }[];
  resends: string[];
  revokes: string[];
};

export function staffHandlers({
  members = MEMBERS,
  invitations = INVITATIONS,
  inviteStatus = 200,
}: { members?: MemberResponse[]; invitations?: StaffInvitationResponse[]; inviteStatus?: number } = {}) {
  const calls: StaffCalls = { statusChanges: [], capacityChanges: [], resends: [], revokes: [] };
  const handlers = [
    http.get("*/staff/members", () => HttpResponse.json(members)),
    http.get("*/staff/invitations", () => HttpResponse.json(invitations)),
    http.post("*/staff/invitations", () =>
      inviteStatus === 200
        ? HttpResponse.json({ id: 99, email: "nuevo@x.test", role: "MEMBER", expiresAt: base.expiresAt })
        : HttpResponse.json(
            { status: 409, type: "/errors/invitation-already-pending",
              detail: "Ya hay una invitación pendiente o vencida para este correo. Puedes reenviarla desde la lista de invitaciones." },
            { status: inviteStatus },
          )),
    http.post("*/staff/invitations/:id/resend", ({ params }) => {
      calls.resends.push(String(params.id));
      return HttpResponse.json({ ...invitations.find((i) => String(i.id) === params.id), state: "PENDING" });
    }),
    http.delete("*/staff/invitations/:id", ({ params }) => {
      calls.revokes.push(String(params.id));
      return new HttpResponse(null, { status: 204 });
    }),
    http.patch("*/staff/members/:id/status", async ({ params, request }) => {
      calls.statusChanges.push({ id: String(params.id), body: await request.json() });
      return new HttpResponse(null, { status: 204 });
    }),
    http.patch("*/staff/members/:id/clinical-capacity", async ({ params, request }) => {
      calls.capacityChanges.push({ id: String(params.id), body: await request.json() });
      return new HttpResponse(null, { status: 204 });
    }),
  ];
  return { handlers, calls };
}
