import { MailCheck } from "lucide-react";
import { DataState } from "@/modules/core/components/DataState";

/**
 * El único mensaje tras pedir la recuperación (update-authentication-password-recovery D7): no dice si la cuenta
 * existe. La última frase cubre los pedidos que no se atienden (límite, repetido, cola llena) sin decir cuál pasó.
 * Sin acción propia: el enlace de vuelta al login ya está en el pie de la pantalla.
 */
export function PasswordRecoverySent() {
  return (
    <div role="status">
      <DataState
        icon={MailCheck}
        title="Revisa tu correo"
        description="Si existe una cuenta con ese correo, te enviamos un enlace. Revisa tu bandeja y el spam. El enlace vale 30 minutos. Si no te llega en unos minutos, vuelve a pedirlo."
      />
    </div>
  );
}
