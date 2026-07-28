import { AxiosError } from "axios";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import type { ApiProblem, FieldError, ValidationProblem } from "@/modules/core/services/generated/model";

/**
 * Errores de la API **tipados desde el contrato**. Antes aquí vivía un `interface
 * ProblemDetailBody` escrito a mano: justo lo que la capacidad `api-type-contracts` prohíbe.
 * Ahora `ApiProblem` / `ValidationProblem` / `FieldError` los genera orval desde
 * `contracts/openapi.json`, así que si el backend cambia la forma del error, el typecheck lo
 * detecta en vez de romperse en producción.
 *
 * En axios el cuerpo del error viaja en `AxiosError<T>`, así que lo parametrizamos con el tipo
 * generado (sigue siendo derivado del contrato, que es lo que la capacidad exige).
 */
export type ApiError = AxiosError<ValidationProblem>;

/** El problema RFC 9457 que devolvió la API, si el error viene de ella. */
export function getApiProblem(error: unknown): ValidationProblem | undefined {
  if (error instanceof AxiosError) {
    return (error as ApiError).response?.data;
  }
  return undefined;
}

/**
 * Mensaje legible del error. El backend responde RFC 9457: usamos su `detail`, que está
 * redactado para mostrarse al usuario. Si no aplica, un mensaje genérico.
 */
export function getUserFriendlyError(error: unknown): string {
  return getApiProblem(error)?.detail ?? "Ocurrió un error inesperado. Inténtalo de nuevo.";
}

/**
 * Errores por campo del servidor (el `400` de validación). Vacío si el error no los trae —el
 * `400` de JSON malformado, por ejemplo, no los lleva—, de ahí que `errors` sea opcional.
 */
export function getFieldErrors(error: unknown): FieldError[] {
  return getApiProblem(error)?.errors ?? [];
}

/** El `type` del conflicto de documento duplicado: el cliente ramifica por él (update-patients D5). */
export const PATIENT_DOCUMENT_TAKEN = "/errors/patient-document-taken";

/**
 * Id de la ficha existente que provoca el `409` de documento duplicado, para ofrecer abrirla
 * (update-patients D5). Vive en `ApiProblem` (el `409` no es un `ValidationProblem`); se lee del
 * tipo generado, nunca de una forma escrita a mano (`docs/frontend.md §2.6`).
 */
export function getExistingPatientId(error: unknown): number | undefined {
  return (getApiProblem(error) as ApiProblem | undefined)?.existingPatientId;
}

/**
 * Vuelca los errores de validación del servidor **sobre los campos del formulario**, para que se
 * pinten junto a cada input igual que los de Zod (paridad cliente/servidor, `docs/coding-style.md §7`).
 *
 * @returns `true` si había errores por campo (y por tanto no hace falta un aviso genérico).
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
): boolean {
  const fieldErrors = getFieldErrors(error);
  for (const { field, message } of fieldErrors) {
    if (field && message) {
      setError(field as Path<T>, { type: "server", message });
    }
  }
  return fieldErrors.length > 0;
}

/** Tipo del problema estándar (sin `errors`), por si algún consumidor solo necesita esa forma. */
export type { ApiProblem };
