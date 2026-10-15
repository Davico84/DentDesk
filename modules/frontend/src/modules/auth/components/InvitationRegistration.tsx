import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Loader2, MailX } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";
import { useInvitationValidation } from "../hooks/useAuth";
import { InvitationSummary } from "./InvitationSummary";
import { InvitationVerifyForm } from "./InvitationVerifyForm";
import { RegisterForm } from "./RegisterForm";

/**
 * Alta por invitación (update-registration-organization-linking): el token del enlace se valida
 * primero; solo entonces se muestran la clínica, el rol y el correo que concede, luego se verifica
 * el código del correo y por último se crea la cuenta. Sin token no hay registro posible.
 */
export function InvitationRegistration() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const invitation = useInvitationValidation(token);
  const [verifiedEmail, setVerifiedEmail] = useState<string | null>(null);

  if (!token) {
    return (
      <DataState icon={MailX} variant="muted" title="Necesitas una invitación"
        description="El registro solo es posible desde el enlace que te envió el administrador de tu clínica." />
    );
  }
  if (invitation.isPending) {
    return (
      <p role="status" className="flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden /> Validando invitación…
      </p>
    );
  }
  if (invitation.isError) {
    return (
      <DataState icon={MailX} variant="destructive" title="La invitación no es válida"
        description="Puede haber vencido, haber sido revocada o ya utilizada. Pide una nueva al administrador de tu clínica." />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <InvitationSummary invitation={invitation.data} />
      {verifiedEmail === null
        ? <InvitationVerifyForm token={token} email={invitation.data.email ?? ""} onVerified={setVerifiedEmail} />
        : <RegisterForm token={token} verifiedEmail={verifiedEmail} />}
    </div>
  );
}
