import { BadgeCheck } from "lucide-react";
import type { SignerResponse } from "@/modules/core/services/generated/model";

/**
 * Quién firmó un registro sellado, tal como era al firmar (spec clinical-history: Credencial del firmante
 * fotografiada al sellar). Lo sellado antes de que existiera la credencial lo dice así, sin inventar un firmante
 * (Firmas anteriores al registro de credenciales). Nada si el registro no está sellado.
 */
export function SignedByLine({
  signer,
  signedBeforeCredentials,
}: {
  signer?: SignerResponse | null;
  signedBeforeCredentials?: boolean;
}) {
  if (signer?.copNumber) {
    return (
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <BadgeCheck className="size-3.5 shrink-0" aria-hidden="true" />
        Firmado por {signer.fullName} · COP {signer.copNumber}
      </p>
    );
  }
  if (signedBeforeCredentials) {
    return <p className="text-xs text-muted-foreground">Firmado antes del registro de credenciales</p>;
  }
  return null;
}
