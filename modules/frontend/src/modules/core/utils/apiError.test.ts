import { describe, it, expect, vi } from "vitest";
import { AxiosError, AxiosHeaders } from "axios";
import type { ValidationProblem } from "@/modules/core/services/generated/model";
import { getUserFriendlyError, getFieldErrors, applyServerFieldErrors } from "./apiError";

/** Construye un AxiosError con el cuerpo RFC 9457 que devuelve el backend. */
function apiErrorWith(body: ValidationProblem): AxiosError<ValidationProblem> {
  const error = new AxiosError<ValidationProblem>("Request failed");
  error.response = {
    data: body,
    status: body.status ?? 400,
    statusText: "",
    headers: {},
    config: { headers: new AxiosHeaders() },
  };
  return error;
}

describe("apiError (tipos derivados del contrato)", () => {
  it("usa el `detail` del problema como mensaje legible", () => {
    const error = apiErrorWith({ status: 409, detail: "El correo ya está registrado." });

    expect(getUserFriendlyError(error)).toBe("El correo ya está registrado.");
  });

  it("cae en un mensaje genérico si el error no viene de la API", () => {
    expect(getUserFriendlyError(new Error("boom"))).toMatch(/inesperado/i);
  });

  it("expone los errores por campo del 400 de validación", () => {
    const error = apiErrorWith({
      status: 400,
      errors: [{ field: "email", message: "Correo inválido" }],
    });

    expect(getFieldErrors(error)).toEqual([{ field: "email", message: "Correo inválido" }]);
  });

  /** El otro 400 (JSON malformado) no trae `errors`: por eso es opcional en el contrato. */
  it("no inventa errores por campo si el 400 no los trae", () => {
    const error = apiErrorWith({ status: 400, detail: "JSON malformado" });

    expect(getFieldErrors(error)).toEqual([]);
  });

  it("vuelca los errores del servidor sobre los campos del formulario", () => {
    const setError = vi.fn();
    const error = apiErrorWith({
      status: 400,
      errors: [
        { field: "email", message: "Correo inválido" },
        { field: "password", message: "Mínimo 8 caracteres" },
      ],
    });

    const applied = applyServerFieldErrors(error, setError);

    expect(applied).toBe(true);
    expect(setError).toHaveBeenCalledWith("email", { type: "server", message: "Correo inválido" });
    expect(setError).toHaveBeenCalledWith("password", { type: "server", message: "Mínimo 8 caracteres" });
  });

  it("no marca ningún campo si el error no trae detalle por campo", () => {
    const setError = vi.fn();

    expect(applyServerFieldErrors(apiErrorWith({ status: 500 }), setError)).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });
});
