import { Link } from "react-router-dom";
import { ShieldAlert } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import type { ClinicalCapacityResponse } from "@/modules/core/services/generated/model";
import { PROFILE_CREDENTIAL } from "@/routes/paths";
import { signingBlockedReason } from "../utils/clinicalCapacityDisplay";

/**
 * Por qué no se puede firmar y adónde ir a resolverlo (spec clinical-history: La interfaz explica por qué no se
 * puede firmar). Junto a una acción de firma deshabilitada; nada si quien firma tiene capacidad clínica o si todavía
 * no se sabe. Con la capacidad retirada o la cuenta inactiva no hay nada que declarar: no ofrece el enlace.
 */
export function SigningCapacityNotice({ capacity }: { capacity?: ClinicalCapacityResponse }) {
  const reason = signingBlockedReason(capacity);
  if (!reason) return null;
  const canFixItself = capacity?.state === "NO_CREDENTIAL" || capacity?.state === "EXPIRED";
  return (
    <div
      role="status"
      className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-warning/40 bg-warning/10 px-4 py-3 text-sm"
    >
      <span className="flex items-center gap-2">
        <ShieldAlert className="size-4 shrink-0 text-warning" aria-hidden="true" />
        {reason}
      </span>
      {canFixItself ? (
        <Link to={PROFILE_CREDENTIAL} className={buttonVariants({ variant: "outline", size: "sm" })}>
          {capacity?.state === "EXPIRED" ? "Renovar credencial" : "Declarar credencial"}
        </Link>
      ) : null}
    </div>
  );
}
