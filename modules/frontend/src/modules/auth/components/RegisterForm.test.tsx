import { describe, it, expect, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Toaster } from "sonner";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { RegisterForm } from "./RegisterForm";

beforeEach(() => {
  useSessionStore.setState({ accessToken: null, user: null, status: "idle" });
});

describe("RegisterForm", () => {
  it("rechaza contraseña de menos de 8 caracteres sin enviar la petición", async () => {
    const user = userEvent.setup();
    renderWithProviders(<RegisterForm />);

    await user.type(screen.getByLabelText(/correo/i), "nuevo@clinica.test");
    await user.type(screen.getByLabelText(/contraseña/i), "1234");
    await user.click(screen.getByRole("button", { name: /crear cuenta/i }));

    expect(
      await screen.findByText("La contraseña debe tener al menos 8 caracteres"),
    ).toBeInTheDocument();
    expect(useSessionStore.getState().status).toBe("idle");
  });

  it("con datos válidos crea la cuenta e inicia sesión", async () => {
    server.use(
      http.post("*/auth/register", () =>
        HttpResponse.json(
          { accessToken: "tok", user: { id: 2, email: "nuevo@clinica.test", role: "USER" } },
          { status: 201 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<RegisterForm />);

    await user.type(screen.getByLabelText(/correo/i), "nuevo@clinica.test");
    await user.type(screen.getByLabelText(/contraseña/i), "password123");
    await user.click(screen.getByRole("button", { name: /crear cuenta/i }));

    await waitFor(() => expect(useSessionStore.getState().status).toBe("authenticated"));
    expect(useSessionStore.getState().user?.email).toBe("nuevo@clinica.test");
  });

  it("con correo ya registrado muestra el error y no inicia sesión", async () => {
    server.use(
      http.post("*/auth/register", () =>
        HttpResponse.json(
          {
            type: "/errors/email-already-exists",
            title: "Conflict",
            status: 409,
            detail: "El correo ya está registrado: dup@clinica.test",
          },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <RegisterForm />
        <Toaster />
      </>,
    );

    await user.type(screen.getByLabelText(/correo/i), "dup@clinica.test");
    await user.type(screen.getByLabelText(/contraseña/i), "password123");
    await user.click(screen.getByRole("button", { name: /crear cuenta/i }));

    expect(await screen.findByText(/ya está registrado/i)).toBeInTheDocument();
    expect(useSessionStore.getState().status).toBe("idle");
  });
});
