import { useEffect } from "react";
import { refresh } from "@/modules/core/services/generated/auth";
import { useSessionStore, type SessionStatus } from "@/store/useSessionStore";

/**
 * Restaura la sesión al montar la app: intenta un refresh (la cookie HttpOnly
 * viaja sola). Si hay sesión vigente → autenticado; si no → no autenticado.
 * Devuelve el `status` para mostrar un splash mientras resuelve.
 */
export function useSessionBootstrap(): SessionStatus {
  const status = useSessionStore((s) => s.status);
  const setSession = useSessionStore((s) => s.setSession);
  const setStatus = useSessionStore((s) => s.setStatus);
  const clear = useSessionStore((s) => s.clear);

  useEffect(() => {
    let active = true;
    setStatus("loading");
    refresh()
      .then((auth) => {
        if (active) setSession(auth.accessToken, auth.user);
      })
      .catch(() => {
        if (active) clear();
      });
    return () => {
      active = false;
    };
  }, [setSession, setStatus, clear]);

  return status;
}
