import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { ChevronRight } from "lucide-react";
import type { PatientSummaryResponse } from "@/modules/core/services/generated/model";
import { patientDetailPath } from "@/routes/paths";
import { PatientAvatar } from "./PatientAvatar";
import { PatientStatusBadge } from "./PatientStatusBadge";
import { PatientMinorBadge } from "./PatientMinorBadge";
import { ageLabel } from "./patientDisplay";

/** Una fila del listado: avatar, nombre + documento, edad, teléfono, estado. Fila entera clicable. */
export function PatientRow({ patient }: { patient: PatientSummaryResponse }) {
  const inactive = patient.status === "INACTIVE";
  return (
    <motion.li
      layout="position"
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ type: "spring", stiffness: 500, damping: 32, mass: 0.5 }}
    >
      <Link
        to={patientDetailPath(patient.id!)}
        className="group flex items-center gap-4 px-4 py-3 transition-colors hover:bg-muted/40"
      >
        <PatientAvatar fullName={patient.fullName!} muted={inactive} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate font-medium">{patient.fullName}</span>
            {patient.minor ? <PatientMinorBadge /> : null}
          </div>
          <span className="text-xs text-muted-foreground">
            {patient.document ? `Doc. ${patient.document}` : "Sin documento"}
          </span>
        </div>
        <span className="hidden w-24 text-sm text-muted-foreground sm:block">{ageLabel(patient.birthDate!)}</span>
        <span className="hidden w-36 truncate text-sm text-muted-foreground md:block">{patient.phone ?? "—"}</span>
        <PatientStatusBadge status={patient.status} />
        <ChevronRight className="size-4 shrink-0 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5" />
      </Link>
    </motion.li>
  );
}
