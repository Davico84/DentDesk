import { AxiosError } from "axios";

/** Forma mínima de un error RFC 9457 (ProblemDetail) que consume la UI. */
interface ProblemDetailBody {
  detail?: string;
}

/**
 * Extrae un mensaje legible del error. El backend responde RFC 9457
 * (ProblemDetail): usamos su `detail`. Si no aplica, un mensaje genérico.
 */
export function getUserFriendlyError(error: unknown): string {
  if (error instanceof AxiosError) {
    const problem = error.response?.data as ProblemDetailBody | undefined;
    if (problem?.detail) return problem.detail;
  }
  return "Ocurrió un error inesperado. Inténtalo de nuevo.";
}
