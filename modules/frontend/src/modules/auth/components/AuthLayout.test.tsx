import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/utils";
import { AuthLayout } from "./AuthLayout";

// Scenario "Desplegar el contacto en el login" (add-support-contact): el mismo marco sirve al
// login y al registro, así que el contacto aparece en los dos.
describe("AuthLayout · soporte", () => {
  it("ofrece el contacto de soporte cerrado bajo el formulario y lo despliega a pedido", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <AuthLayout title="Iniciar sesión" footer={<p>¿No tienes cuenta?</p>}>
        <form aria-label="Acceso" />
      </AuthLayout>,
    );

    const toggle = screen.getByRole("button", { name: "¿Problemas para entrar? Habla con soporte" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("region", { name: "Soporte" })).not.toBeInTheDocument();

    await user.click(toggle);
    const card = screen.getByRole("region", { name: "Soporte" });
    expect(card).toHaveTextContent("David Velarde · Soporte Dentdesk");
    expect(card).toHaveTextContent("Respondo de 9:00 a 20:00");
    expect(screen.getByRole("link", { name: /WhatsApp/ })).toHaveAttribute(
      "href",
      expect.stringContaining("https://wa.me/51959396384?text="),
    );
    expect(screen.getByRole("link", { name: /Correo/ })).toHaveAttribute("href", "mailto:davicova84@gmail.com");
  });
});
