import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { checkPasswordReset, requestPasswordRecovery, resetPassword } from "@/modules/core/services/generated/auth";
import type { PasswordRecoveryRequest } from "@/modules/core/services/generated/model";
import { useSessionStore } from "@/store/useSessionStore";

/**
 * Pide la recuperación. El servidor responde siempre lo mismo (202), exista o no la cuenta: la pantalla muestra un
 * único mensaje neutro (update-authentication-password-recovery D7).
 */
export function useRequestPasswordRecovery() {
  return useMutation({
    mutationFn: (data: PasswordRecoveryRequest) => requestPasswordRecovery(data),
  });
}

/**
 * ¿El enlace sigue vigente? Sin reintentos: un enlace inválido no se arregla reintentando, y el mismo error para
 * cualquier causa (inválido, vencido, usado, anulado) es a propósito.
 */
export function usePasswordResetCheck(token: string) {
  return useQuery({
    queryKey: ["auth", "password-reset", token],
    queryFn: () => checkPasswordReset({ token }).then(() => true),
    enabled: token.length > 0,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
  });
}

/**
 * Restablece la contraseña. Cierra todas las sesiones, también la de este navegador, así que al éxito se limpia la
 * sesión en memoria si la hubiera, y el caché de datos de servidor (D7).
 */
export function useResetPassword() {
  const clear = useSessionStore.use.clear();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      resetPassword({ token, newPassword }),
    onSuccess: () => {
      clear();
      // Todo menos el check de esta pantalla: quitarlo lo volvería a pedir (y daría "usado") antes de navegar.
      queryClient.removeQueries({ predicate: (query) => query.queryKey[1] !== "password-reset" });
    },
  });
}
