import type { MemberResponseStatus } from "@/modules/core/services/generated/model";

/**
 * Píldora del estado de un miembro (activo / inactivo). Componente de dominio, mismo patrón que
 * `PatientStatusBadge`: tokens del tema, sin colores fijos.
 */
export function MemberStatusBadge({ status }: { status?: MemberResponseStatus }) {
  const active = status === "ACTIVE";
  return (
    <span
      className={[
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium",
        active ? "bg-success/10 text-success" : "bg-muted text-muted-foreground",
      ].join(" ")}
    >
      <span className={["size-1.5 rounded-full", active ? "bg-success" : "bg-muted-foreground/60"].join(" ")} />
      {active ? "Activo" : "Inactivo"}
    </span>
  );
}
