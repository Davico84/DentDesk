import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { ForgotPasswordForm } from "./ForgotPasswordForm";

const NEUTRAL = /si existe una cuenta con ese correo, te enviamos un enlace/i;

describe("ForgotPasswordForm", () => {
  it("con el 202 muestra el único mensaje neutro, sin decir si la cuenta existe", async () => {
    let body: unknown;
    server.use(
      http.post("*/auth/password-recovery", async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ForgotPasswordForm />);

    await user.type(screen.getByLabelText(/correo/i), "  ana@clinica.test ");
    await user.click(screen.getByRole("button", { name: /enviar enlace/i }));

    expect(await screen.findByText(NEUTRAL)).toBeInTheDocument();
    expect(screen.getByText(/si no te llega en unos minutos, vuelve a pedirlo/i)).toBeInTheDocument();
    expect(body).toEqual({ email: "ana@clinica.test" });
  });

  it.each(["", "no-es-un-correo"])("con el correo %j muestra el error y no envía la petición", async (email) => {
    let called = false;
    server.use(
      http.post("*/auth/password-recovery", () => {
        called = true;
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ForgotPasswordForm />);

    if (email) {
      await user.type(screen.getByLabelText(/correo/i), email);
    }
    await user.click(screen.getByRole("button", { name: /enviar enlace/i }));

    expect(await screen.findByText(/el correo es obligatorio|correo electrónico no válido/i)).toBeInTheDocument();
    expect(called).toBe(false);
    expect(screen.queryByText(NEUTRAL)).not.toBeInTheDocument();
  });
});
