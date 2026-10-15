import type { StaffInvitationResponseState } from "@/modules/core/services/generated/model";
import { INVITATION_STATE_LABEL } from "../utils/staffLabels";

/** Pendiente = espera al invitado; vencida = hay que reenviarla; aceptada = ya entró; revocada = anulada. */
const TONE: Record<StaffInvitationResponseState, { pill: string; dot: string }> = {
  PENDING: { pill: "bg-warning/10 text-warning", dot: "bg-warning" },
  EXPIRED: { pill: "bg-destructive/10 text-destructive", dot: "bg-destructive" },
  ACCEPTED: { pill: "bg-success/10 text-success", dot: "bg-success" },
  REVOKED: { pill: "bg-muted text-muted-foreground", dot: "bg-muted-foreground/60" },
};

/** Píldora del estado de una invitación. Componente de dominio: tokens semánticos del tema. */
export function InvitationStatusBadge({ state }: { state: StaffInvitationResponseState }) {
  const tone = TONE[state];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${tone.pill}`}>
      <span className={`size-1.5 rounded-full ${tone.dot}`} />
      {INVITATION_STATE_LABEL[state]}
    </span>
  );
}
