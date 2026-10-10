import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { login, register, logout } from "@/modules/core/services/generated/auth";
import type { LoginRequest, RegisterRequest } from "@/modules/core/services/generated/model";
import { useSessionStore } from "@/store/useSessionStore";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

/** Inicia sesión: al éxito guarda la sesión (access en memoria); errores → toast. */
export function useLogin() {
  const setSession = useSessionStore.use.setSession();
  return useMutation({
    mutationFn: (data: LoginRequest) => login(data),
    onSuccess: (auth) => setSession(auth.accessToken, auth.user),
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Registra una cuenta e inicia sesión automáticamente. */
export function useRegister() {
  const setSession = useSessionStore.use.setSession();
  return useMutation({
    mutationFn: (data: RegisterRequest) => register(data),
    onSuccess: (auth) => setSession(auth.accessToken, auth.user),
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/**
 * Cierra sesión. Best-effort: limpia el estado local aunque el backend falle. También vacía el
 * `sessionStorage` de la pestaña (p. ej. la última búsqueda del listado de pacientes), para que
 * quien entre después en el mismo equipo no herede nada de esta sesión.
 */
export function useLogout() {
  const clear = useSessionStore.use.clear();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => logout(),
    onSettled: () => {
      clear();
      queryClient.clear();
      try {
        sessionStorage.clear();
      } catch {
        /* almacenamiento no disponible: no había nada que borrar */
      }
    },
  });
}
