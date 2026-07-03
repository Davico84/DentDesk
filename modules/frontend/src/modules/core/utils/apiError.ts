import { AxiosError } from "axios";
import type { ProblemDetail } from "@/modules/core/services/generated/model";

/**
 * Extrae un mensaje legible del error. El backend responde RFC 9457
 * (ProblemDetail): usamos su `detail`. Si no aplica, un mensaje genérico.
 */
export function getUserFriendlyError(error: unknown): string {
  if (error instanceof AxiosError) {
    const problem = error.response?.data as ProblemDetail | undefined;
    if (problem?.detail) return problem.detail;
  }
  return "Ocurrió un error inesperado. Inténtalo de nuevo.";
}
