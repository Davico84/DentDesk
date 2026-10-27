import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  changeClinicalCapacity,
  changeStatus,
  invitations,
  invite,
  members,
  resendInvitation,
  revoke,
} from "@/modules/core/services/generated/staff";
import type { InvitationRequest, MemberResponseRole } from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

/** Claves separadas (add-staff design D6): cada mutación invalida solo la lista que cambia. */
export const STAFF_KEYS = {
  members: ["staff", "members"] as const,
  invitations: ["staff", "invitations"] as const,
};

export function useMembersQuery() {
  return useQuery({ queryKey: STAFF_KEYS.members, queryFn: members });
}

export function useInvitationsQuery() {
  return useQuery({ queryKey: STAFF_KEYS.invitations, queryFn: invitations });
}

/** Errores en toast salvo que quien llama los muestre en su propio formulario (`onError`). */
export function useInviteStaff() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: InvitationRequest) => invite(data),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEYS.invitations });
      toast.success(`Invitación enviada a ${result.email}.`);
    },
  });
}

export function useResendInvitation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => resendInvitation(id),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEYS.invitations });
      toast.success(`Invitación reenviada a ${result.email}. El enlace anterior ya no sirve.`);
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useRevokeInvitation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => revoke(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEYS.invitations });
      toast.success("Invitación revocada.");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useChangeMemberStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      changeStatus(id, { status: active ? "ACTIVE" : "INACTIVE" }),
    onSuccess: (_, { active }) => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEYS.members });
      toast.success(active ? "Miembro reactivado." : "Miembro desactivado. Ya no podrá entrar a la clínica.");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/**
 * Retirar o restituir la capacidad clínica de un miembro en esta clínica (update-staff-professional-credential D2).
 * Restituir solo levanta el retiro: si su credencial venció o no la declaró, sigue sin poder firmar.
 */
export function useChangeClinicalCapacity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, revoked }: { id: number; revoked: boolean }) => changeClinicalCapacity(id, { revoked }),
    onSuccess: (_, { revoked }) => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEYS.members });
      toast.success(revoked ? "Capacidad clínica retirada. Ya no podrá firmar en esta clínica." : "Capacidad clínica restituida.");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export type StaffRole = MemberResponseRole;
