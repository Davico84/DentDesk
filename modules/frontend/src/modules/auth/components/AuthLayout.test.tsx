import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/utils";
import { AuthLayout } from "./AuthLayout";

// Scenario "Contacto en el login y el registro" (add-support-contact): el mismo marco sirve al
// login y al registro, así que el contacto aparece en los dos.
describe("AuthLayout · soporte", () => {
  it("muestra el contacto de soporte de Dentdesk bajo el formulario", () => {
    renderWithProviders(
      <AuthLayout title="Iniciar sesión" footer={<p>¿No tienes cuenta?</p>}>
        <form aria-label="Acceso" />
      </AuthLayout>,
    );

    expect(screen.getByRole("region", { name: "Soporte" })).toHaveTextContent("¿Problemas para entrar?");
    expect(screen.getByRole("link", { name: /WhatsApp/ })).toHaveAttribute(
      "href",
      expect.stringContaining("https://wa.me/51959396384?text="),
    );
    expect(screen.getByRole("link", { name: /correo/ })).toHaveAttribute("href", "mailto:davicova84@gmail.com");
  });
});
