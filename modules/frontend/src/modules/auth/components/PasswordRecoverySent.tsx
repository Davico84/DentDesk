import { MailCheck } from "lucide-react";
import { Link } from "react-router-dom";
import { DataState } from "@/modules/core/components/DataState";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { PATHS } from "@/routes/paths";

/**
 * El único mensaje tras pedir la recuperación (update-authentication-password-recovery D7): no dice si la cuenta
 * existe. La última frase cubre los pedidos que no se atienden (límite, repetido, cola llena) sin decir cuál pasó.
 */
export function PasswordRecoverySent() {
  return (
    <div role="status">
      <DataState
        icon={MailCheck}
        title="Revisa tu correo"
        description="Si existe una cuenta con ese correo, te enviamos un enlace. Revisa tu bandeja y el spam. El enlace vale 30 minutos. Si no te llega en unos minutos, vuelve a pedirlo."
        action={
          <Link to={PATHS.LOGIN} className={buttonVariants({ variant: "outline" })}>
            Volver a iniciar sesión
          </Link>
        }
      />
    </div>
  );
}
