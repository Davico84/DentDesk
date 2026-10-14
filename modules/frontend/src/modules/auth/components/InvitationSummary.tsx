import { Building2, Mail, UserRound } from "lucide-react";
import { ROLE_LABEL } from "@/modules/core/auth/roleLabel";
import type { InvitationValidationResponse } from "@/modules/core/services/generated/model";

/**
 * Lo que concede la invitación, ya validada por el backend: clínica, rol y correo. Solo lectura — el
 * invitado no elige ni edita ninguno de los tres (vienen del token).
 */
export function InvitationSummary({ invitation }: { invitation: InvitationValidationResponse }) {
  const rows = [
    { icon: Building2, label: "Clínica", value: invitation.organizationName },
    { icon: UserRound, label: "Rol", value: invitation.role ? ROLE_LABEL[invitation.role] : undefined },
    { icon: Mail, label: "Correo", value: invitation.email },
  ];
  return (
    <dl aria-label="Datos de la invitación" className="grid gap-2 rounded-lg border bg-muted/40 p-3 text-sm">
      {rows.map(({ icon: Icon, label, value }) => (
        <div key={label} className="flex items-center gap-2">
          <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <dt className="text-muted-foreground">{label}:</dt>
          <dd className="truncate font-medium">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
