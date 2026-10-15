import { Filter, MailPlus } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";
import { Button } from "@/modules/core/ui/button";

/**
 * Sin invitaciones que mostrar. Distingue los dos vacíos (spec staff): la clínica **no emitió ninguna**
 * (→ invita a emitir la primera) vs **el filtro no tiene resultados** (→ ofrece ver todas).
 */
export function InvitationsEmpty({
  filtered,
  onInvite,
  onShowAll,
}: {
  filtered: boolean;
  onInvite: () => void;
  onShowAll: () => void;
}) {
  return filtered ? (
    <DataState
      icon={Filter}
      variant="muted"
      title="No hay invitaciones con este estado"
      description="Elige otro estado o mira todas."
      action={<Button variant="outline" onClick={onShowAll}>Ver todas</Button>}
    />
  ) : (
    <DataState
      icon={MailPlus}
      title="Aún no invitaste a nadie"
      description="Invita a tu personal: le llega un correo con un enlace y un código para crear su cuenta."
      action={<Button onClick={onInvite}>Invitar a alguien</Button>}
    />
  );
}
