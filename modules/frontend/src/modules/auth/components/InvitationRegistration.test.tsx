import { describe, it, expect } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { InvitationRegistration } from "./InvitationRegistration";

const VALID = { email: "ana@clinica.test", role: "MEMBER", organizationName: "Clínica Aurora", expiresAt: "2026-12-01T00:00:00Z" };

function renderAt(url: string) {
  return renderWithProviders(<InvitationRegistration />, { initialEntries: [url] });
}

describe("InvitationRegistration", () => {
  it("sin token no ofrece ningún formulario", () => {
    renderAt("/auth/register");
    expect(screen.getByText("Necesitas una invitación")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("con un token inválido lo dice y no ofrece formulario", async () => {
    server.use(http.post("*/auth/invitations/validate", () => HttpResponse.json(
      { status: 400, type: "/errors/invalid-invitation" }, { status: 400 },
    )));
    renderAt("/auth/register?token=malo");
    expect(await screen.findByText("La invitación no es válida")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("tras validar el token muestra clínica, rol y correo, sin poder editarlos", async () => {
    server.use(http.post("*/auth/invitations/validate", () => HttpResponse.json(VALID)));
    renderAt("/auth/register?token=bueno");
    const summary = await screen.findByLabelText("Datos de la invitación");
    expect(within(summary).getByText("Clínica Aurora")).toBeInTheDocument();
    expect(within(summary).getByText("Personal")).toBeInTheDocument();
    expect(within(summary).getByText("ana@clinica.test")).toBeInTheDocument();
    expect(screen.getByLabelText(/correo electrónico/i)).toHaveAttribute("readonly");
    expect(screen.queryByLabelText(/nombre de la clínica/i)).not.toBeInTheDocument();
  });

  it("un código con formato inválido no se envía", async () => {
    server.use(http.post("*/auth/invitations/validate", () => HttpResponse.json(VALID)));
    const user = userEvent.setup();
    renderAt("/auth/register?token=bueno");
    await user.type(await screen.findByLabelText(/código de verificación/i), "12");
    await user.click(screen.getByRole("button", { name: /verificar correo/i }));
    expect(await screen.findByText("Ingresa el código de 6 dígitos")).toBeInTheDocument();
  });

  it("con el código correcto pasa al formulario de alta con el correo verificado", async () => {
    server.use(
      http.post("*/auth/invitations/validate", () => HttpResponse.json(VALID)),
      http.post("*/auth/invitations/verify", () => new HttpResponse(null, { status: 204 })),
    );
    const user = userEvent.setup();
    renderAt("/auth/register?token=bueno");
    await user.type(await screen.findByLabelText(/código de verificación/i), "123456");
    await user.click(screen.getByRole("button", { name: /verificar correo/i }));
    expect(await screen.findByLabelText(/nombre completo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/correo electrónico/i)).toHaveValue("ana@clinica.test");
  });

  it("con un código incorrecto muestra el error y no avanza", async () => {
    server.use(
      http.post("*/auth/invitations/validate", () => HttpResponse.json(VALID)),
      http.post("*/auth/invitations/verify", () => HttpResponse.json(
        { status: 400, detail: "La invitacion no es valida, expiro o ya fue utilizada." }, { status: 400 },
      )),
    );
    const user = userEvent.setup();
    renderAt("/auth/register?token=bueno");
    await user.type(await screen.findByLabelText(/código de verificación/i), "000000");
    await user.click(screen.getByRole("button", { name: /verificar correo/i }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByLabelText(/nombre completo/i)).not.toBeInTheDocument();
  });
});
