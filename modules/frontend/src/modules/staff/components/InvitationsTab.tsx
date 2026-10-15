import { useState } from "react";
import { AnimatePresence } from "framer-motion";
import { ConfirmDialog } from "@/modules/core/ui/confirm-dialog";
import type { StaffInvitationResponse } from "@/modules/core/services/generated/model";
import { useInvitationsQuery, useResendInvitation, useRevokeInvitation } from "../hooks/useStaffApi";
import { countByFilter, filterInvitations, INVITATION_FILTERS, type InvitationFilter } from "../utils/staffFilters";
import { INVITATION_FILTER_LABEL } from "../utils/staffLabels";
import { InvitationRow } from "./InvitationRow";
import { InvitationsEmpty } from "./InvitationsEmpty";
import { StaffListError } from "./StaffListError";
import { StaffListSkeleton } from "./StaffListSkeleton";

/**
 * Pestaña Invitaciones: las emitidas por la clínica con su estado, filtro por estado con contadores (en el
 * cliente: design D5), reenviar y revocar con confirmación.
 */
export function InvitationsTab({ onInvite }: { onInvite: () => void }) {
  const invitations = useInvitationsQuery();
  const resend = useResendInvitation();
  const revoke = useRevokeInvitation();
  const [filter, setFilter] = useState<InvitationFilter>("ALL");
  const [toRevoke, setToRevoke] = useState<StaffInvitationResponse | null>(null);

  const all = invitations.data ?? [];
  const counts = countByFilter(all);
  const visible = filterInvitations(all, filter);

  return (
    <div className="flex flex-col gap-4">
      <div role="group" aria-label="Filtrar por estado" className="flex flex-wrap gap-2">
        {INVITATION_FILTERS.map((f) => (
          <button
            key={f}
            type="button"
            aria-pressed={filter === f}
            onClick={() => setFilter(f)}
            className={[
              "h-11 cursor-pointer rounded-xl border px-4 text-sm font-medium transition-colors duration-200",
              filter === f
                ? "border-primary/30 bg-primary/10 text-primary"
                : "border-input bg-card text-muted-foreground hover:bg-muted/50",
            ].join(" ")}
          >
            {INVITATION_FILTER_LABEL[f]} <span className="tabular-nums">({counts[f]})</span>
          </button>
        ))}
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card shadow-xs">
        {invitations.isLoading ? (
          <StaffListSkeleton />
        ) : invitations.isError ? (
          <StaffListError what="las invitaciones" />
        ) : visible.length === 0 ? (
          <InvitationsEmpty filtered={all.length > 0} onInvite={onInvite} onShowAll={() => setFilter("ALL")} />
        ) : (
          <ul className="divide-y" aria-label="Invitaciones de la clínica">
            <AnimatePresence initial={false}>
              {visible.map((i) => (
                <InvitationRow
                  key={i.id}
                  invitation={i}
                  resending={resend.isPending && resend.variables === i.id}
                  onResend={(inv) => resend.mutate(inv.id)}
                  onRevoke={setToRevoke}
                />
              ))}
            </AnimatePresence>
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={toRevoke !== null}
        onOpenChange={(open) => (open ? null : setToRevoke(null))}
        title="¿Revocar la invitación?"
        description={`El enlace que recibió ${toRevoke?.email ?? ""} dejará de funcionar. Si quieres invitarle de nuevo, tendrás que enviarle una invitación nueva.`}
        confirmLabel="Revocar"
        destructive
        pending={revoke.isPending}
        onConfirm={() => toRevoke && revoke.mutate(toRevoke.id, { onSettled: () => setToRevoke(null) })}
      />
    </div>
  );
}
