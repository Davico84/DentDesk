import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  create,
  deactivate,
  get,
  reactivate,
  search,
  update,
} from "@/modules/core/services/generated/patients";
import type { PatientRequest, SearchParams } from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

const PATIENTS_KEY = ["patients"] as const;

/** Listado con búsqueda (nombre o documento) + inactivos + paginación. */
export function usePatientsSearch(params: SearchParams) {
  return useQuery({
    queryKey: [...PATIENTS_KEY, "list", params],
    queryFn: () => search(params),
  });
}

/** Ficha completa de un paciente (para la consulta y la edición). */
export function usePatient(id: number | undefined) {
  return useQuery({
    queryKey: [...PATIENTS_KEY, "detail", id],
    queryFn: () => get(id as number),
    enabled: id != null,
  });
}

/**
 * Alta. El error de **campo** (400 de validación) lo pinta el llamador (`onError` del `.mutate()`,
 * vía `applyServerFieldErrors`); el aviso **genérico** (409 de documento duplicado, 500, etc.) lo
 * pone este hook — mismo patrón que `useLogin` (`docs/frontend.md §2.6`): ambos corren siempre, no
 * es uno u otro.
 */
export function useCreatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: PatientRequest) => create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PATIENTS_KEY });
      toast.success("Paciente registrado");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Edición. Mismo patrón de error que el alta (ver `useCreatePatient`). */
export function useUpdatePatient(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: PatientRequest) => update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PATIENTS_KEY });
      toast.success("Ficha actualizada");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Baja lógica (archiva, no borra). */
export function useDeactivatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deactivate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PATIENTS_KEY });
      toast.success("Paciente dado de baja");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Reactivación (vuelve al listado por defecto). */
export function useReactivatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => reactivate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PATIENTS_KEY });
      toast.success("Paciente reactivado");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}
