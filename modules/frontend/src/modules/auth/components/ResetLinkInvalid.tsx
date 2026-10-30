import { LinkIcon } from "lucide-react";
import { Link } from "react-router-dom";
import { DataState } from "@/modules/core/components/DataState";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { PATHS } from "@/routes/paths";

/** Enlace de recuperación inválido, vencido, usado o anulado: el mismo mensaje para los cuatro, y pedir otro. */
export function ResetLinkInvalid() {
  return (
    <DataState
      icon={LinkIcon}
      variant="destructive"
      title="El enlace no es válido o ya venció"
      description="Los enlaces valen 30 minutos y se usan una sola vez. Pide uno nuevo."
      action={
        <Link to={PATHS.FORGOT_PASSWORD} className={buttonVariants()}>
          Pedir un enlace nuevo
        </Link>
      }
    />
  );
}
