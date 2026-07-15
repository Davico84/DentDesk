import type { PatientResponseStatus } from "@/modules/core/services/generated/model";

/** Píldora de estado del paciente (activo / de baja). Verde = activo (token `success`). */
export function StatusBadge({ status }: { status?: PatientResponseStatus }) {
  const inactive = status === "INACTIVE";
  return (
    <span
      className={[
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium",
        inactive ? "bg-muted text-muted-foreground" : "bg-success/10 text-success",
      ].join(" ")}
    >
      <span className={["size-1.5 rounded-full", inactive ? "bg-muted-foreground/60" : "bg-success"].join(" ")} />
      {inactive ? "De baja" : "Activo"}
    </span>
  );
}

/** Píldora "menor" para señalar que el paciente requiere responsable. */
export function MinorBadge() {
  return (
    <span className="inline-flex items-center rounded-full bg-accent px-2 py-0.5 text-xs font-medium text-accent-foreground">
      menor
    </span>
  );
}
