import { ShieldAlert, ShieldCheck } from "lucide-react";
import type { ClinicalCapacityResponse } from "@/modules/core/services/generated/model";
import { signingBlockedReason } from "../utils/clinicalCapacityDisplay";
import { ClinicalCapacityBadge } from "./ClinicalCapacityBadge";

/**
 * El estado de la capacidad clínica en la clínica activa, con su motivo cuando no se puede firmar (spec staff:
 * Capacidad clínica efectiva en la clínica activa). Sin clínica activa no hay capacidad que mostrar.
 */
export function ClinicalCapacitySummary({ capacity }: { capacity?: ClinicalCapacityResponse }) {
  if (!capacity) return null;
  const enabled = capacity.state === "ENABLED";
  const Icon = enabled ? ShieldCheck : ShieldAlert;
  return (
    <section
      aria-label="Tu capacidad clínica en esta clínica"
      className="flex items-start gap-4 rounded-2xl border bg-card p-5 shadow-xs"
    >
      <Icon className={["mt-0.5 size-6 shrink-0", enabled ? "text-success" : "text-warning"].join(" ")} aria-hidden="true" />
      <div className="flex min-w-0 flex-col gap-1.5">
        <ClinicalCapacityBadge capacity={capacity} />
        <p className="text-sm text-muted-foreground">
          {enabled
            ? "Puedes cerrar admisiones y sellar notas de consulta en esta clínica."
            : signingBlockedReason(capacity)}
        </p>
      </div>
    </section>
  );
}
