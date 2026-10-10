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
  it("elige un preset del catálogo y envía el PUT con esa clave", async () => {
    const user = userEvent.setup();
    let body: { brandPreset?: string } | null = null;
    server.use(
      http.put("*/organizations/me/branding", async ({ request }) => {
        body = (await request.json()) as { brandPreset?: string };
        return HttpResponse.json(body);
      }),
    );
    renderWithProviders(<BrandingSettingsScreen />);

    await user.click(await screen.findByRole("radio", { name: /teal/i }));
    await user.click(screen.getByRole("button", { name: /guardar marca/i }));

    await waitFor(() => expect(body?.brandPreset).toBe("TEAL"));
  });

  it("'Ninguno' no envía preset (equivale a quitar la personalización)", async () => {
    const user = userEvent.setup();
    let body: { brandPreset?: string } | null = null;
    // Precarga con un preset ya elegido, para volver a "Ninguno" desde ahí.
    server.use(
      http.get("*/organizations/me/branding", () => HttpResponse.json({ brandPreset: "TEAL" })),
      http.put("*/organizations/me/branding", async ({ request }) => {
        body = (await request.json()) as { brandPreset?: string };
        return HttpResponse.json(body);
      }),
    );
    renderWithProviders(<BrandingSettingsScreen />);

    await user.click(await screen.findByRole("radio", { name: /ninguno/i }));
    await user.click(screen.getByRole("button", { name: /guardar marca/i }));

    await waitFor(() => expect(body).toEqual({ brandPreset: undefined, logoUrl: undefined }));
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

    await user.type(await screen.findByLabelText(/url del logo/i), "http://cdn.test/logo.png");
    await user.click(screen.getByRole("button", { name: /guardar marca/i }));

    expect(await screen.findByText(/debe usar https/i)).toBeInTheDocument();
    expect(putCalled).toBe(false);
  });
});
