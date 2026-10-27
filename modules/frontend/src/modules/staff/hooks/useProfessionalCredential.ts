import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { declare, mine } from "@/modules/core/services/generated/staff";
import type { ProfessionalCredentialRequest } from "@/modules/core/services/generated/model";
import { getApiProblem } from "@/modules/core/utils/apiError";
import { STAFF_KEYS } from "./useStaffApi";

const CAPACITY_REQUIRED = "/errors/clinical-capacity-required";

/**
 * Si el servidor rechazó una firma por capacidad clínica (`403`), la capacidad cambió desde que se leyó (venció, la
 * retiraron): se vuelve a pedir para que el aviso junto al botón lo diga. El mensaje lo da el toast del llamador.
 */
export function refreshCapacityIfDenied(queryClient: QueryClient, error: unknown) {
  if (getApiProblem(error)?.type === CAPACITY_REQUIRED) {
    void queryClient.invalidateQueries({ queryKey: CREDENTIAL_KEY });
  }
}

/** La credencial propia y la capacidad en la clínica activa (update-staff-professional-credential D6). */
export const CREDENTIAL_KEY = ["me", "professional-credential"] as const;

export function useMyCredentialQuery() {
  return useQuery({ queryKey: CREDENTIAL_KEY, queryFn: mine });
}

/**
 * Capacidad clínica de la sesión en la clínica activa, para habilitar o explicar las acciones de firma. Mientras
 * carga, `capacity` es `undefined`: quien firma no se habilita hasta saberlo (el servidor decide de todos modos).
 */
export function useMyClinicalCapacity() {
  const query = useMyCredentialQuery();
  return { capacity: query.data?.clinicalCapacity, isLoading: query.isLoading };
}

/** Declarar o renovar. Los errores los muestra el formulario, junto al campo (`onError` de quien llama). */
export function useDeclareCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: ProfessionalCredentialRequest) => declare(data),
    onSuccess: (result) => {
      queryClient.setQueryData(CREDENTIAL_KEY, result);
      // El ADMIN ve la capacidad de cada miembro en Personal, incluida la suya.
      void queryClient.invalidateQueries({ queryKey: STAFF_KEYS.members });
      toast.success("Credencial guardada.");
    },
  });
}
