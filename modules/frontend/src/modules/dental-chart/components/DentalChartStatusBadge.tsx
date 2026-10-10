import { Lock, PenLine } from "lucide-react";
import type { DentalChartResponseStatus } from "@/modules/core/services/generated/model";

/** Píldora de estado del odontograma: OPEN (editable) o SEALED (sellado, solo lectura). */
export function DentalChartStatusBadge({ status }: { status?: DentalChartResponseStatus }) {
  const sealed = status === "SEALED";
  return (
    <span
      className={[
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium",
        sealed ? "bg-success/10 text-success" : "bg-accent text-accent-foreground",
      ].join(" ")}
    >
      {sealed ? <Lock className="size-3" /> : <PenLine className="size-3" />}
      {sealed ? "Sellado" : "Abierto"}
    </span>
  );
}
