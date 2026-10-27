import type { ClinicalCapacityResponse } from "@/modules/core/services/generated/model";
import { capacityDetail, capacityTone } from "../utils/clinicalCapacityDisplay";

const TONE = {
  success: { pill: "bg-success/10 text-success", dot: "bg-success" },
  warning: { pill: "bg-warning/10 text-warning", dot: "bg-warning" },
  muted: { pill: "bg-muted text-muted-foreground", dot: "bg-muted-foreground/60" },
} as const;

/**
 * Píldora de la capacidad clínica (update-staff-professional-credential D7): habilitado hasta una fecha,
 * vencida, sin credencial, retirada o cuenta inactiva. Mismo patrón que `MemberStatusBadge`: tokens del tema.
 */
export function ClinicalCapacityBadge({ capacity }: { capacity?: ClinicalCapacityResponse }) {
  if (!capacity?.state) return null;
  const tone = TONE[capacityTone(capacity.state)];
  return (
    <span className={["inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium", tone.pill].join(" ")}>
      <span className={["size-1.5 shrink-0 rounded-full", tone.dot].join(" ")} aria-hidden="true" />
      {capacityDetail(capacity)}
    </span>
  );
}
