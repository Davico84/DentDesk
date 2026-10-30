import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { PATHS } from "@/routes/paths";
import { LoginForm } from "./LoginForm";
import { PasswordReset } from "./PasswordReset";

const INVALID_LINK = { type: "/errors/invalid-reset-link", status: 400, detail: "El enlace no es válido o ya venció. Pide uno nuevo." };

/** Pantalla de restablecimiento y login reales, para seguir la navegación entre las dos. */
function renderAt(url: string) {
  const router = createMemoryRouter(
    [
      { path: PATHS.RESET_PASSWORD, element: <PasswordReset /> },
      { path: PATHS.LOGIN, element: <LoginForm /> },
      { path: PATHS.FORGOT_PASSWORD, element: <p>Pantalla de pedido</p> },
    ],
    { initialEntries: [url] },
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

function validCheck() {
  server.use(http.post("*/auth/password-reset/check", () => new HttpResponse(null, { status: 204 })));
}

async function fillAndSubmit(newPassword: string, confirmPassword = newPassword) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^contraseña nueva$/i), newPassword);
  await user.type(screen.getByLabelText(/confirmar contraseña nueva/i), confirmPassword);
  await user.click(screen.getByRole("button", { name: /guardar contraseña nueva/i }));
}

beforeEach(() => {
  useSessionStore.setState({ accessToken: null, user: null, status: "idle" });
});

describe("PasswordReset", () => {
  it("lee el token una vez y lo saca de la URL", async () => {
    let checked: unknown;
    server.use(
      http.post("*/auth/password-reset/check", async ({ request }) => {
        checked = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const router = renderAt("/auth/reset-password?token=secreto-1");

    expect(await screen.findByLabelText(/^contraseña nueva$/i)).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toBe(""));
    expect(router.state.location.pathname).toBe(PATHS.RESET_PASSWORD);
    expect(checked).toEqual({ token: "secreto-1" });
  });

  it("con un enlace inválido lo dice y ofrece pedir otro, sin formulario", async () => {
    server.use(http.post("*/auth/password-reset/check", () => HttpResponse.json(INVALID_LINK, { status: 400 })));
    renderAt("/auth/reset-password?token=muerto");

    expect(await screen.findByText("El enlace no es válido o ya venció")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /pedir un enlace nuevo/i })).toHaveAttribute("href", PATHS.FORGOT_PASSWORD);
    expect(screen.queryByLabelText(/^contraseña nueva$/i)).not.toBeInTheDocument();
  });

  it("sin token muestra el enlace inválido", () => {
    renderAt("/auth/reset-password");
    expect(screen.getByText("El enlace no es válido o ya venció")).toBeInTheDocument();
  });

  it.each([
    ["una nueva de menos de 8", "corta12", "corta12", "La contraseña debe tener al menos 8 caracteres"],
    ["una confirmación distinta", "nueva-4567", "otra-4567", "Las contraseñas no coinciden"],
  ])("con %s muestra el error y no envía la petición", async (_, next, confirm, message) => {
    validCheck();
    let called = false;
    server.use(
      http.post("*/auth/password-reset", () => {
        called = true;
        return HttpResponse.json({ outcome: "OK" });
      }),
    );
    renderAt("/auth/reset-password?token=bueno");

    await fillAndSubmit(next, confirm);

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("si el enlace dejó de valer al guardar, muestra el enlace inválido", async () => {
    validCheck();
    server.use(http.post("*/auth/password-reset", () => HttpResponse.json(INVALID_LINK, { status: 400 })));
    renderAt("/auth/reset-password?token=bueno");

    await fillAndSubmit("nueva-4567");

    expect(await screen.findByText("El enlace no es válido o ya venció")).toBeInTheDocument();
  });

  it("al guardar limpia la sesión en memoria y vuelve al login con el aviso de listo", async () => {
    useSessionStore.setState({ accessToken: "tok", status: "authenticated" });
    validCheck();
    let sent: unknown;
    server.use(
      http.post("*/auth/password-reset", async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ outcome: "OK" });
      }),
    );
    const router = renderAt("/auth/reset-password?token=bueno");

    await fillAndSubmit("nueva-4567");

    expect(await screen.findByText("Listo. Ya puedes iniciar sesión con tu contraseña nueva.")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PATHS.LOGIN);
    expect(sent).toEqual({ token: "bueno", newPassword: "nueva-4567" });
    expect(useSessionStore.getState().accessToken).toBeNull();
  });

  it("con la cuenta bloqueada el login dice a qué hora podrá entrar", async () => {
    validCheck();
    const lockedUntil = "2026-10-29T20:42:00Z";
    server.use(http.post("*/auth/password-reset", () => HttpResponse.json({ outcome: "LOCKED", lockedUntil })));
    renderAt("/auth/reset-password?token=bueno");

    await fillAndSubmit("nueva-4567");

    const time = new Date(lockedUntil).toLocaleTimeString("es-PE", { hour: "2-digit", minute: "2-digit", hour12: false });
    expect(
      await screen.findByText(`Tu contraseña se cambió. Por los intentos fallidos, podrás iniciar sesión a las ${time}.`),
    ).toBeInTheDocument();
  });

  it("con el acceso desactivado el login pide contactar al administrador", async () => {
    validCheck();
    server.use(http.post("*/auth/password-reset", () => HttpResponse.json({ outcome: "ACCESS_DISABLED" })));
    renderAt("/auth/reset-password?token=bueno");

    await fillAndSubmit("nueva-4567");

    expect(await screen.findByText(/tu acceso está desactivado. contacta al administrador de tu clínica/i))
      .toBeInTheDocument();
  });
});
