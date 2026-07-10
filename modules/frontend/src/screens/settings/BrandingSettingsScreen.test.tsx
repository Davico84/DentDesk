import { describe, it, expect, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { BrandingSettingsScreen } from "./BrandingSettingsScreen";

beforeEach(() => {
  useSessionStore.setState({
    accessToken: "t",
    status: "authenticated",
    user: {
      id: 1,
      email: "admin@clinica.test",
      fullName: "Ana Pérez",
      role: "ADMIN",
      organizationId: 5,
      organizationName: "Clínica Test",
      organizationSlug: "clinica-test",
    },
  });
  // Marca actual vacía (defaults del producto).
  server.use(http.get("*/organizations/me/branding", () => HttpResponse.json({})));
});

describe("BrandingSettingsScreen", () => {
  it("valida el color y no envía la petición si es inválido", async () => {
    const user = userEvent.setup();
    let putCalled = false;
    server.use(
      http.put("*/organizations/me/branding", () => {
        putCalled = true;
        return HttpResponse.json({});
      }),
    );
    renderWithProviders(<BrandingSettingsScreen />);

    await user.type(screen.getByLabelText("Color primario"), "rojo");
    await user.click(screen.getByRole("button", { name: /guardar marca/i }));

    expect(await screen.findByText(/color hex inválido/i)).toBeInTheDocument();
    expect(putCalled).toBe(false);
  });

  it("guarda una marca válida (envía el PUT con el color)", async () => {
    const user = userEvent.setup();
    let body: { brandPrimary?: string } | null = null;
    server.use(
      http.put("*/organizations/me/branding", async ({ request }) => {
        body = (await request.json()) as { brandPrimary?: string };
        return HttpResponse.json(body);
      }),
    );
    renderWithProviders(<BrandingSettingsScreen />);

    await user.type(screen.getByLabelText("Color primario"), "#123abc");
    await user.click(screen.getByRole("button", { name: /guardar marca/i }));

    await waitFor(() => expect(body?.brandPrimary).toBe("#123abc"));
  });

  it("rechaza una URL de logo que no sea https (sin enviar)", async () => {
    const user = userEvent.setup();
    let putCalled = false;
    server.use(
      http.put("*/organizations/me/branding", () => {
        putCalled = true;
        return HttpResponse.json({});
      }),
    );
    renderWithProviders(<BrandingSettingsScreen />);

    await user.type(screen.getByLabelText(/url del logo/i), "http://cdn.test/logo.png");
    await user.click(screen.getByRole("button", { name: /guardar marca/i }));

    expect(await screen.findByText(/debe usar https/i)).toBeInTheDocument();
    expect(putCalled).toBe(false);
  });
});
