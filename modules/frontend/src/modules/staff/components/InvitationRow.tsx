import { motion } from "framer-motion";
import { Mail } from "lucide-react";
import { ROLE_LABEL } from "@/modules/core/auth/roleLabel";
import { Button } from "@/modules/core/ui/button";
import { dateLabel } from "@/modules/core/utils/dateLabel";
import type { StaffInvitationResponse } from "@/modules/core/services/generated/model";
import { InvitationStatusBadge } from "./InvitationStatusBadge";

/**
 * Una fila de invitaciones: correo, rol, fechas, estado y, si la clínica puede gestionarla (`manageable`, lo
 * decide el backend), reenviar y revocar. Revocar pide confirmación al padre; reenviar no: no destruye nada.
 */
export function InvitationRow({
  invitation,
  resending,
  onResend,
  onRevoke,
}: {
  invitation: StaffInvitationResponse;
  resending: boolean;
  onResend: (invitation: StaffInvitationResponse) => void;
  onRevoke: (invitation: StaffInvitationResponse) => void;
}) {
  const expired = invitation.state === "EXPIRED";
  return (
    <motion.li
      layout="position"
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ type: "spring", stiffness: 500, damping: 32, mass: 0.5 }}
      className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3"
    >
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-muted text-muted-foreground">
        <Mail className="size-4" aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <span className="block truncate font-medium">{invitation.email}</span>
        <span className="block text-xs text-muted-foreground">
          Enviada el {dateLabel(invitation.createdAt)} · {expired ? "venció" : "vence"} el {dateLabel(invitation.expiresAt)}
        </span>
      </div>
      <span className="hidden w-28 text-sm text-muted-foreground sm:block">{ROLE_LABEL[invitation.role]}</span>
      <span className="sm:w-24"><InvitationStatusBadge state={invitation.state} /></span>
      {/* En celular, las acciones van en su propia línea alineadas con el texto; desde sm, columna de ancho fijo
          para alinear rol y estado aunque la fila no tenga botones. */}
      {invitation.manageable ? (
        <div className="flex basis-full gap-2 pl-14 sm:basis-auto sm:w-48 sm:justify-end sm:pl-0">
          <Button
            variant="outline"
            className="h-11 rounded-xl"
            disabled={resending}
            onClick={() => onResend(invitation)}
            aria-label={`Reenviar la invitación a ${invitation.email}`}
          >
            {resending ? "Reenviando…" : "Reenviar"}
          </Button>
          <Button
            variant="ghost"
            className="h-11 rounded-xl text-destructive hover:text-destructive"
            onClick={() => onRevoke(invitation)}
            aria-label={`Revocar la invitación a ${invitation.email}`}
          >
            Revocar
          </Button>
        </div>
      ) : (
        <span className="hidden sm:block sm:w-48" aria-hidden="true" />
      )}
    </motion.li>
  );
}
