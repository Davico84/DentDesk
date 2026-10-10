import { Lock, PenLine } from "lucide-react";
import type { ClinicalHistoryResponseStatus } from "@/modules/core/services/generated/model";

/** Píldora de estado de la Historia Clínica: DRAFT (editable) o COMPLETED (sellada). */
export function ClinicalHistoryStatusBadge({ status }: { status?: ClinicalHistoryResponseStatus }) {
  const completed = status === "COMPLETED";
  return (
    <span
      className={[
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium",
        completed ? "bg-success/10 text-success" : "bg-accent text-accent-foreground",
      ].join(" ")}
    >
      {completed ? <Lock className="size-3" /> : <PenLine className="size-3" />}
      {completed ? "Completada" : "Borrador"}
    </span>
  );
}
