import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { changePassword, myProfile, updateMyProfile } from "@/modules/core/services/generated/auth";
import type { ChangePasswordRequest, ProfileRequest } from "@/modules/core/services/generated/model";
import { useSessionStore } from "@/store/useSessionStore";

/** El perfil propio: nombre y correo (update-authentication-my-profile D1). */
export const PROFILE_KEY = ["me", "profile"] as const;

export function useMyProfileQuery() {
  return useQuery({ queryKey: PROFILE_KEY, queryFn: myProfile });
}

/**
 * Corregir el nombre. El JWT no lleva el nombre, así que no hace falta un access nuevo: se actualiza el usuario de la
 * sesión en memoria y el menú de la cuenta lo muestra sin recargar (D5). Los errores los pinta el formulario.
 */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: ProfileRequest) => updateMyProfile(data),
    onSuccess: (profile) => {
      queryClient.setQueryData(PROFILE_KEY, profile);
      const { accessToken, user, setSession } = useSessionStore.getState();
      if (accessToken && user) setSession(accessToken, { ...user, fullName: profile.fullName });
      toast.success("Nombre actualizado.");
    },
  });
}

/**
 * Cambiar la contraseña. El servidor cierra las otras sesiones y devuelve un access nuevo para esta (la cookie nueva
 * llega sola): se guarda con `setSession` para seguir trabajando sin volver a entrar (D6). Los errores los pinta el
 * formulario, junto al campo.
 */
export function useChangePassword() {
  const setSession = useSessionStore.use.setSession();
  return useMutation({
    mutationFn: (data: ChangePasswordRequest) => changePassword(data),
    onSuccess: (auth) => {
      setSession(auth.accessToken, auth.user);
      toast.success("Contraseña cambiada. Cerramos tus sesiones en otros dispositivos.");
    },
  });
}
