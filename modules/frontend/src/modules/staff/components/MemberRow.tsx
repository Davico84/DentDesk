import { motion } from "framer-motion";
import { Monogram } from "@/modules/core/components/Monogram";
import { ROLE_LABEL } from "@/modules/core/auth/roleLabel";
import { Button } from "@/modules/core/ui/button";
import type { MemberResponse } from "@/modules/core/services/generated/model";
import { ClinicalCapacityBadge } from "./ClinicalCapacityBadge";
import { MemberStatusBadge } from "./MemberStatusBadge";

/**
 * Una fila del personal: monograma, nombre + correo + capacidad clínica, rol, estado y las acciones de desactivar o
 * reactivar y de retirar o restituir la capacidad clínica. Sin acciones sobre la propia fila ni sobre un
 * administrador (spec staff); las confirmaciones las abre el padre. A un miembro inactivo no se le ofrece el retiro:
 * ya no puede firmar.
 */
export function MemberRow({
  member,
  isSelf,
  onToggleStatus,
  onToggleCapacity,
}: {
  member: MemberResponse;
  isSelf: boolean;
  onToggleStatus: (member: MemberResponse) => void;
  onToggleCapacity: (member: MemberResponse) => void;
}) {
  const active = member.status === "ACTIVE";
  const manageable = !isSelf && member.role === "MEMBER";
  const revoked = member.clinicalCapacity?.state === "REVOKED";
  const name = member.fullName ?? member.email ?? "";
  return (
    <motion.li
      layout="position"
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ type: "spring", stiffness: 500, damping: 32, mass: 0.5 }}
      className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3"
    >
      <Monogram name={name} tone={active ? "primary" : "secondary"} className="size-10" />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className={["truncate font-medium", active ? "" : "text-muted-foreground"].join(" ")}>{name}</span>
          {isSelf ? <span className="text-xs text-muted-foreground">(tú)</span> : null}
        </div>
        <span className="block truncate text-xs text-muted-foreground">{member.email}</span>
        <span className="mt-1 block">
          <ClinicalCapacityBadge capacity={member.clinicalCapacity} />
          {member.clinicalCapacity?.copNumber ? (
            <span className="ml-2 text-xs text-muted-foreground">COP {member.clinicalCapacity.copNumber}</span>
          ) : null}
        </span>
      </div>
      <span className="hidden w-28 text-sm text-muted-foreground sm:block">
        {member.role ? ROLE_LABEL[member.role] : ""}
      </span>
      <span className="sm:w-20"><MemberStatusBadge status={member.status} /></span>
      {/* En celular, la acción baja a su propia línea alineada con el texto (no trunca el nombre); desde sm, columna
          de ancho fijo para alinear rol y estado aunque la fila no tenga botón. */}
      <div
        className={
          manageable
            ? "flex basis-full flex-wrap gap-2 pl-14 sm:basis-auto sm:w-64 sm:justify-end sm:pl-0"
            : "hidden sm:block sm:w-64"
        }
      >
        {manageable && active ? (
          <Button
            variant="outline"
            className="h-11 rounded-xl"
            onClick={() => onToggleCapacity(member)}
            aria-label={`${revoked ? "Restituir" : "Retirar"} la capacidad clínica de ${name}`}
          >
            {revoked ? "Restituir capacidad" : "Retirar capacidad"}
          </Button>
        ) : null}
        {manageable ? (
          <Button
            variant="outline"
            className="h-11 rounded-xl"
            onClick={() => onToggleStatus(member)}
            aria-label={`${active ? "Desactivar" : "Reactivar"} a ${name}`}
          >
            {active ? "Desactivar" : "Reactivar"}
          </Button>
        ) : null}
      </div>
    </motion.li>
  );
}
