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
import type { PatientRequest, PatientResponse, SearchParams } from "@/modules/core/services/generated/model";
import {
  getApiProblem,
  getUserFriendlyError,
  PATIENT_DOCUMENT_TAKEN,
} from "@/modules/core/utils/apiError";

const PATIENTS_KEY = ["patients"] as const;

/**
 * El conflicto de documento duplicado (`patient-document-taken`) lo maneja **el componente** con un
 * toast accionable ("abrir ficha existente"); el hook se **abstiene** de su toast genérico para ese
 * caso, para no mostrar dos avisos del mismo error (update-patients D5). Cualquier otro error sigue
 * cayendo en el toast genérico.
 */
function toastGenericUnlessDocumentTaken(error: unknown): void {
  if (getApiProblem(error)?.type === PATIENT_DOCUMENT_TAKEN) {
    return;
  }
  toast.error(getUserFriendlyError(error));
}

/**
 * Aviso **no bloqueante** de teléfono compartido (update-patients D1): el alta/edición ya se
 * completó cuando esto se muestra, así que nunca impide guardar. Visualmente distinto del toast de
 * error (`toast.warning`, no `toast.error`).
 */
function warnIfPhoneShared(patient: PatientResponse): void {
  if (patient.phoneSharedWithOtherPatients) {
    toast.warning("Este teléfono ya está registrado a nombre de otro paciente de la clínica.");
  }
}

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
 * vía `applyServerFieldErrors`); el aviso **genérico** (500, etc.) lo pone este hook — mismo patrón
 * que `useLogin` (`docs/frontend.md §2.6`): ambos corren siempre, no es uno u otro. **Excepción**
 * (D5): el `409` de documento duplicado lo maneja el componente con un toast accionable, así que
 * aquí se omite el genérico para ese caso.
 */
export function useCreatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: PatientRequest) => create(data),
    onSuccess: (patient) => {
      queryClient.invalidateQueries({ queryKey: PATIENTS_KEY });
      toast.success("Paciente registrado");
      warnIfPhoneShared(patient);
    },
    onError: toastGenericUnlessDocumentTaken,
  });
}

/** Edición. Mismo patrón de error que el alta (ver `useCreatePatient`). */
export function useUpdatePatient(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: PatientRequest) => update(id, data),
    onSuccess: (patient) => {
      queryClient.invalidateQueries({ queryKey: PATIENTS_KEY });
      toast.success("Ficha actualizada");
      warnIfPhoneShared(patient);
    },
    onError: toastGenericUnlessDocumentTaken,
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
