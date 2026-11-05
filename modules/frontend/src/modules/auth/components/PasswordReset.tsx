import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { usePasswordResetCheck } from "../hooks/usePasswordRecovery";
import { ResetLinkInvalid } from "./ResetLinkInvalid";
import { ResetPasswordForm } from "./ResetPasswordForm";

/** El token de la query, leído una sola vez al montar. */
function tokenFrom(search: string): string {
  return new URLSearchParams(search).get("token") ?? "";
}

/**
 * Restablecer desde el enlace del correo (update-authentication-password-recovery D7). El token se lee <b>una vez</b>
 * y la URL se reemplaza sin la query, así no queda en la barra ni en el historial. Antes de pedir la contraseña se
 * comprueba que el enlace siga vigente, para no hacer escribir una contraseña a quien abrió un enlace muerto.
 */
export function PasswordReset() {
  const location = useLocation();
  const navigate = useNavigate();
  const [token] = useState(() => tokenFrom(location.search));
  const [invalidated, setInvalidated] = useState(false);
  const check = usePasswordResetCheck(token);

  useEffect(() => {
    if (location.search) {
      navigate({ pathname: location.pathname, search: "" }, { replace: true, state: location.state });
    }
  }, [location.pathname, location.search, location.state, navigate]);

  if (!token || invalidated || check.isError) {
    return <ResetLinkInvalid />;
  }
  if (check.isPending) {
    return (
      <p role="status" className="flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden /> Comprobando el enlace…
      </p>
    );
  }
  return <ResetPasswordForm token={token} onInvalidLink={() => setInvalidated(true)} />;
}
