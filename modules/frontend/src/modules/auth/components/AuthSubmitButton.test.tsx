import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AuthSubmitButton } from "./AuthSubmitButton";

describe("AuthSubmitButton", () => {
  it("muestra la etiqueta y queda habilitado cuando no está pendiente", () => {
    render(
      <AuthSubmitButton pending={false} pendingLabel="Enviando…">
        Enviar
      </AuthSubmitButton>,
    );

    const button = screen.getByRole("button", { name: "Enviar" });
    expect(button).toBeEnabled();
    expect(button).toHaveAttribute("type", "submit");
  });

  it("muestra la etiqueta de carga y queda deshabilitado cuando está pendiente", () => {
    render(
      <AuthSubmitButton pending pendingLabel="Enviando…">
        Enviar
      </AuthSubmitButton>,
    );

    expect(screen.getByText("Enviando…")).toBeInTheDocument();
    expect(screen.queryByText("Enviar")).not.toBeInTheDocument();
    expect(screen.getByRole("button")).toBeDisabled();
  });
});
