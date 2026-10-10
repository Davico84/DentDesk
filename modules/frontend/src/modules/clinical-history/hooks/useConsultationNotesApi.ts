import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  addBackgroundEntry,
  addNoteCorrection,
  addNoteDiagnosis,
  discardConsultationNote,
  getActiveBackground,
  listConsultationNotes,
  openConsultationNote,
  removeBackgroundEntry,
  removeNoteDiagnosis,
  sealConsultationNote,
  updateConsultationNote,
} from "@/modules/core/services/generated/consultation-notes";
import type {
  BackgroundEntryRequest,
  ConsultationNoteRequest,
  CorrectionRequest,
  DiagnosisRequest,
} from "@/modules/core/services/generated/model";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

const NOTES_KEY = ["consultation-notes"] as const;
const BACKGROUND_KEY = ["clinical-background"] as const;

/** Notas del paciente en orden cronológico de atención (la abierta, si existe, es la última). */
export function useConsultationNotes(patientId: number) {
  return useQuery({
    queryKey: [...NOTES_KEY, patientId],
    queryFn: () => listConsultationNotes(patientId),
  });
}

/** Antecedentes vigentes con su origen (design.md D8). */
export function useActiveBackground(patientId: number) {
  return useQuery({
    queryKey: [...BACKGROUND_KEY, patientId],
    queryFn: () => getActiveBackground(patientId),
  });
}

/**
 * Mutación de la nota con la invalidación común: toda escritura cambia la lista; el sellado cambia
 * además los vigentes (los asientos de una nota abierta no cuentan hasta sellarla).
 */
function useNoteMutation<TVars, TResult>(
  patientId: number,
  mutationFn: (vars: TVars) => Promise<TResult>,
  success: string,
  { refreshBackground = false }: { refreshBackground?: boolean } = {},
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...NOTES_KEY, patientId] });
      if (refreshBackground) queryClient.invalidateQueries({ queryKey: [...BACKGROUND_KEY, patientId] });
      toast.success(success);
    },
    onError: (error) => toast.error(getUserFriendlyError(error)),
  });
}

export function useOpenNote(patientId: number) {
  return useNoteMutation(patientId, (attendedAt: string) => openConsultationNote(patientId, { attendedAt }), "Nota de consulta abierta");
}

export function useUpdateNote(patientId: number, noteId: number) {
  return useNoteMutation(patientId, (data: ConsultationNoteRequest) => updateConsultationNote(patientId, noteId, data), "Nota guardada");
}

export function useDiscardNote(patientId: number, noteId: number) {
  return useNoteMutation(patientId, () => discardConsultationNote(patientId, noteId), "Nota descartada");
}

/**
 * Sella la nota. La pantalla ya bloquea el sellado con contenido faltante; si el servidor igual lo
 * rechaza (otra pestaña quitó un diagnóstico), su `detail` sale en el toast genérico.
 */
export function useSealNote(patientId: number, noteId: number) {
  return useNoteMutation(patientId, () => sealConsultationNote(patientId, noteId), "Nota sellada", {
    refreshBackground: true,
  });
}

export function useAddNoteDiagnosis(patientId: number, noteId: number) {
  return useNoteMutation(patientId, (data: DiagnosisRequest) => addNoteDiagnosis(patientId, noteId, data), "Diagnóstico agregado");
}

export function useRemoveNoteDiagnosis(patientId: number, noteId: number) {
  return useNoteMutation(patientId, (diagnosisId: number) => removeNoteDiagnosis(patientId, noteId, diagnosisId), "Diagnóstico eliminado");
}

export function useAddBackgroundEntry(patientId: number, noteId: number) {
  return useNoteMutation(patientId, (data: BackgroundEntryRequest) => addBackgroundEntry(patientId, noteId, data), "Antecedente registrado");
}

export function useRemoveBackgroundEntry(patientId: number, noteId: number) {
  return useNoteMutation(patientId, (entryId: number) => removeBackgroundEntry(patientId, noteId, entryId), "Antecedente quitado");
}

export function useAddCorrection(patientId: number, noteId: number) {
  return useNoteMutation(patientId, (data: CorrectionRequest) => addNoteCorrection(patientId, noteId, data), "Corrección registrada");
}
