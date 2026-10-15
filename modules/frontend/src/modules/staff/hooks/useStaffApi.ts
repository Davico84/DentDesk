import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { invite, members, revoke } from "@/modules/core/services/generated/staff-controller";
import type { InvitationRequest, MemberResponseRole } from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

const STAFF_KEY = ["staff"] as const;

export function useMembersQuery() {
  return useQuery({ queryKey: STAFF_KEY, queryFn: members });
}

export function useInviteStaff() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: InvitationRequest) => invite(data),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEY });
      toast.success(`Invitación enviada a ${result.email}.`);
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useRevokeInvitation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => revoke(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: STAFF_KEY }),
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export type StaffRole = MemberResponseRole;
