import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  addFinding,
  catalog,
  create2,
  get2,
  removeFinding,
  setObservations,
  setSpecifications,
  updateFinding,
} from "@/modules/core/services/generated/dental-chart";
import type { DentalChartCreateRequest, DentalChartFindingRequest, DentalChartTextRequest } from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

const DENTAL_CHART_KEY = ["dental-chart"] as const;

/** El odontograma del paciente. `enabled` evita la petición mientras no hay `patientId`. */
export function useDentalChart(patientId: number | undefined) {
  return useQuery({
    queryKey: [...DENTAL_CHART_KEY, patientId],
    queryFn: () => get2(patientId as number),
    enabled: patientId != null,
    // 404 = el paciente aún no tiene odontograma: es un estado válido, no un error de red.
    retry: false,
  });
}

/**
 * El catálogo de 38 hallazgos (design.md D6): global, no depende del paciente — se cachea sin
 * expirar (`staleTime: Infinity`), es dato de catálogo, no de negocio mutable.
 */
export function useDentalFindingCatalog() {
  return useQuery({
    queryKey: ["dental-chart-catalog"],
    queryFn: () => catalog(),
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export function useCreateDentalChart(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DentalChartCreateRequest) => create2(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...DENTAL_CHART_KEY, patientId] });
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useSetSpecifications(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DentalChartTextRequest) => setSpecifications(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...DENTAL_CHART_KEY, patientId] });
      toast.success("Especificaciones registradas");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useSetObservations(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DentalChartTextRequest) => setObservations(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...DENTAL_CHART_KEY, patientId] });
      toast.success("Observaciones registradas");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

/** Agrega un hallazgo. El aviso de dentición↔edad no bloquea (design.md D9): solo informa por toast. */
export function useAddFinding(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: DentalChartFindingRequest) => addFinding(patientId, data),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: [...DENTAL_CHART_KEY, patientId] });
      toast.success("Hallazgo agregado");
      if (result.denticionWarning) {
        toast.warning("Esta pieza es de dentición temporal en un paciente adulto. Se registró igual.");
      }
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useUpdateFinding(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ findingId, data }: { findingId: number; data: DentalChartFindingRequest }) =>
      updateFinding(patientId, findingId, data),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: [...DENTAL_CHART_KEY, patientId] });
      toast.success("Hallazgo actualizado");
      if (result.denticionWarning) {
        toast.warning("Esta pieza es de dentición temporal en un paciente adulto. Se registró igual.");
      }
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useRemoveFinding(patientId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (findingId: number) => removeFinding(patientId, findingId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...DENTAL_CHART_KEY, patientId] });
      toast.success("Hallazgo eliminado");
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}
