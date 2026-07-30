import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  addDiagnosis,
  complete,
  create,
  enablement,
  get,
  removeDiagnosis,
  search1,
  update,
  updateDiagnosis,
} from "@/modules/core/services/generated/clinical-history";
import type { ClinicalHistoryRequest, DiagnosisRequest } from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

const CLINICAL_HISTORY_KEY = ["clinical-history"] as const;

/** La Historia Clínica del paciente. `enabled` evita la petición mientras no hay `patientId`. */
export function useClinicalHistory(patientId: number | undefined) {
  return useQuery({
    queryKey: [...CLINICAL_HISTORY_KEY, patientId],
    queryFn: () => get(patientId as number),
    enabled: patientId != null,
    // 404 = el paciente aún no tiene Historia Clínica: es un estado válido, no un error de red.
    retry: false,
  });
}

/** Estado de habilitación para tratamientos (D8): NONE|DRAFT|COMPLETED + enabledForTreatment. */
export function useClinicalHistoryEnablement(patientId: number | undefined) {
  return useQuery({
    queryKey: [...CLINICAL_HISTORY_KEY, patientId, "enablement"],
    queryFn: () => enablement(patientId as number),
    enabled: patientId != null,
  });
}

/** Crea la Historia Clínica en DRAFT. El error de campo lo pinta el llamador; el genérico, este hook. */
export function useCreateClinicalHistory(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => create(patientId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...CLINICAL_HISTORY_KEY, patientId] });
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Guardado incremental en DRAFT: secciones incompletas se aceptan, sin bloqueo. */
export function useUpdateClinicalHistory(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: ClinicalHistoryRequest) => update(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...CLINICAL_HISTORY_KEY, patientId] });
      toast.success("Historia clínica guardada");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Sella a COMPLETED. El checklist incompleto (400) lo pinta el llamador (missingFields); el resto de errores, el toast genérico. */
export function useCompleteClinicalHistory(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => complete(patientId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...CLINICAL_HISTORY_KEY, patientId] });
      toast.success("Historia clínica completada");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useAddDiagnosis(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DiagnosisRequest) => addDiagnosis(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...CLINICAL_HISTORY_KEY, patientId] });
      toast.success("Diagnóstico agregado");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useUpdateDiagnosis(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ diagnosisId, data }: { diagnosisId: number; data: DiagnosisRequest }) =>
      updateDiagnosis(patientId, diagnosisId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...CLINICAL_HISTORY_KEY, patientId] });
      toast.success("Diagnóstico actualizado");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useRemoveDiagnosis(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (diagnosisId: number) => removeDiagnosis(patientId, diagnosisId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...CLINICAL_HISTORY_KEY, patientId] });
      toast.success("Diagnóstico eliminado");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Búsqueda CIE10 (jerárquica): cada resultado trae su ruta capítulo→grupo→categoría→subcategoría. */
export function useCie10Search(query: string) {
  return useQuery({
    queryKey: ["cie10", "search", query],
    queryFn: () => search1({ query }),
    enabled: query.trim().length >= 2,
  });
}
