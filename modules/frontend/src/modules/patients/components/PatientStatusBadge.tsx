import type { PatientResponseStatus } from "@/modules/core/services/generated/model";

/**
 * Píldora de estado del paciente (activo / de baja). Verde = activo (token `success`).
 *
 * <p>Componente de **dominio**: conoce el enum de estado del contrato. Por eso vive en el módulo y
 * no en `core/ui` (docs/frontend.md §4.1).
 */
export function PatientStatusBadge({ status }: { status?: PatientResponseStatus }) {
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
