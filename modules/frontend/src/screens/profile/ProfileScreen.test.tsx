import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { toast } from "sonner";
import { server } from "@/test/mocks/server";
import { appRoutes } from "@/routes";
import { useSessionStore } from "@/store/useSessionStore";
import { AFTER_PASSWORD_CHANGE, profileHandlers } from "@/modules/auth/test/profileApiMocks";
import { credentialHandlers, ENABLED } from "@/modules/staff/test/credentialApiMocks";

// Scenarios de authentication (update-authentication-my-profile): Ver el perfil propio · Corregir el nombre propio ·
// Cambiar la contraseña propia · Cerrar las otras sesiones al cambiar la contraseña. Rutas y shell reales.

vi.mock("sonner", async (importOriginal) => ({
  ...(await importOriginal<typeof import("sonner")>()),
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

function signIn() {
  useSessionStore.setState({ accessToken: "access-viejo", user: AFTER_PASSWORD_CHANGE.user, status: "authenticated" });
}

function renderAt(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

/** La cabecera del shell es el primer `banner`: ahí está el menú de la cuenta con el nombre. */
const shellHeader = () => screen.getAllByRole("banner")[0];
const fullName = () => screen.findByLabelText(/Nombre completo/);
const currentPassword = () => screen.getByLabelText(/Contraseña actual/);
const newPassword = () => screen.getByLabelText(/^Contraseña nueva/);
const confirmPassword = () => screen.getByLabelText(/Confirmar contraseña nueva/);

async function fillPassword(current: string, next: string, confirm = next) {
  const user = userEvent.setup();
  await fullName();
  await user.type(currentPassword(), current);
  await user.type(newPassword(), next);
  await user.type(confirmPassword(), confirm);
  await user.click(screen.getByRole("button", { name: "Cambiar contraseña" }));
}

beforeEach(() => {
  vi.mocked(toast.success).mockClear();
  signIn();
  // Lo que no es de esta pantalla (marca, etc.) responde 404; los handlers de cada test van encima.
  server.use(
    http.all("*", () => new HttpResponse(null, { status: 404 })),
    ...credentialHandlers({ current: ENABLED }).handlers,
  );
});

describe("Ver el perfil propio", () => {
  it("muestra el nombre editable y el correo solo de lectura", async () => {
    server.use(...profileHandlers().handlers);
    renderAt("/profile");
    expect(await fullName()).toHaveValue("Ana Torres");
    expect(screen.getByText("ana@clinica.test")).toBeInTheDocument();
    // El correo no es un campo: no hay forma de editarlo.
    expect(screen.queryByRole("textbox", { name: /Correo/ })).not.toBeInTheDocument();
    expect(screen.getByText("Es tu usuario para entrar: no se puede cambiar.")).toBeInTheDocument();
  });

  it("el menú de la cuenta lleva a Mi perfil", async () => {
    server.use(...profileHandlers().handlers);
    const user = userEvent.setup();
    renderAt("/");
    await user.click(await within(await waitFor(shellHeader)).findByRole("button", { name: /Tu cuenta/ }));
    expect(await screen.findByRole("menuitem", { name: "Mi perfil" })).toHaveAttribute("href", "/profile");
  });
});

describe("Corregir el nombre propio", () => {
  it("guarda el nombre sin los espacios de los extremos y el menú de la cuenta lo muestra sin recargar", async () => {
    const { handlers, calls } = profileHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt("/profile");
    const field = await fullName();
    await user.clear(field);
    await user.type(field, "  Ana Torres Ríos  ");
    await user.click(screen.getByRole("button", { name: "Guardar nombre" }));

    await waitFor(() => expect(calls.patches).toEqual([{ fullName: "Ana Torres Ríos" }]));
    await waitFor(() => expect(shellHeader()).toHaveTextContent("Ana Torres Ríos"));
    expect(useSessionStore.getState().accessToken).toBe("access-viejo");
    expect(toast.success).toHaveBeenCalledWith("Nombre actualizado.");
  });

  it.each([
    ["de menos de 2 caracteres", "A"],
    ["solo con espacios", "    "],
    ["de más de 120 caracteres", "a".repeat(121)],
  ])("un nombre %s muestra el error y no envía la petición", async (_, value) => {
    const { handlers, calls } = profileHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt("/profile");
    const field = await fullName();
    await user.clear(field);
    await user.type(field, value);
    await user.click(screen.getByRole("button", { name: "Guardar nombre" }));

    expect(await screen.findByText("El nombre debe tener entre 2 y 120 caracteres")).toBeInTheDocument();
    expect(calls.patches).toHaveLength(0);
  });
});

describe("Cambiar la contraseña propia", () => {
  it("con éxito guarda la sesión nueva, limpia los campos y avisa que se cerraron las otras sesiones", async () => {
    const { handlers, calls } = profileHandlers();
    server.use(...handlers);
    renderAt("/profile");
    await fillPassword("actual-123", "nueva-4567");

    await waitFor(() => expect(useSessionStore.getState().accessToken).toBe("access-nuevo"));
    // La confirmación no viaja: solo existe en el cliente.
    expect(calls.passwords).toEqual([{ currentPassword: "actual-123", newPassword: "nueva-4567" }]);
    expect(toast.success).toHaveBeenCalledWith("Contraseña cambiada. Cerramos tus sesiones en otros dispositivos.");
    await waitFor(() => expect(currentPassword()).toHaveValue(""));
    expect(newPassword()).toHaveValue("");
    expect(confirmPassword()).toHaveValue("");
    // Sigue en la misma sesión, sin volver a iniciar sesión.
    expect(screen.getByRole("heading", { name: "Mi perfil" })).toBeInTheDocument();
  });

  it("una contraseña actual incorrecta se muestra junto al campo", async () => {
    server.use(...profileHandlers({ passwordStatus: 400 }).handlers);
    renderAt("/profile");
    await fillPassword("no-es-esta", "nueva-4567");

    expect(await screen.findByText("La contraseña actual no es correcta.")).toBeInTheDocument();
    expect(currentPassword()).toHaveAttribute("aria-invalid", "true");
    expect(useSessionStore.getState().accessToken).toBe("access-viejo");
  });

  it("con la cuenta bloqueada lo dice junto a la contraseña actual", async () => {
    server.use(...profileHandlers({ passwordStatus: 429 }).handlers);
    renderAt("/profile");
    await fillPassword("actual-123", "nueva-4567");

    expect(await screen.findByText(/La cuenta está bloqueada temporalmente/)).toBeInTheDocument();
    expect(currentPassword()).toHaveAttribute("aria-invalid", "true");
  });

  it("una nueva demasiado corta no envía la petición", async () => {
    const { handlers, calls } = profileHandlers();
    server.use(...handlers);
    renderAt("/profile");
    await fillPassword("actual-123", "corta");

    expect(await screen.findByText("La contraseña debe tener al menos 8 caracteres")).toBeInTheDocument();
    expect(calls.passwords).toHaveLength(0);
  });

  it("una confirmación distinta no envía la petición", async () => {
    const { handlers, calls } = profileHandlers();
    server.use(...handlers);
    renderAt("/profile");
    await fillPassword("actual-123", "nueva-4567", "otra-45678");

    expect(await screen.findByText("Las contraseñas no coinciden")).toBeInTheDocument();
    expect(calls.passwords).toHaveLength(0);
  });

  it("una nueva igual a la actual no envía la petición", async () => {
    const { handlers, calls } = profileHandlers();
    server.use(...handlers);
    renderAt("/profile");
    await fillPassword("actual-123", "actual-123");

    expect(await screen.findByText("La contraseña nueva debe ser distinta de la actual")).toBeInTheDocument();
    expect(calls.passwords).toHaveLength(0);
  });
});

describe("La credencial dentro de Mi perfil", () => {
  it("la ruta anterior de la credencial lleva a su sección en Mi perfil", async () => {
    server.use(...profileHandlers().handlers);
    const router = renderAt("/profile/credential");
    expect(await screen.findByRole("heading", { name: "Credencial profesional" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/profile");
    expect(router.state.location.hash).toBe("#credencial");
  });
});
