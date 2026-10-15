import { beforeEach, describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/utils";
import { useSessionStore, type SessionUser } from "@/store/useSessionStore";
import { HomeWelcome } from "./HomeWelcome";

// Scenarios de `app-shell` › Inicio con bienvenida y atajos.

const ANA: SessionUser = {
  id: 1,
  email: "ana@clinica.test",
  fullName: "Ana Torres",
  role: "MEMBER",
  organizationId: 5,
  organizationName: "Clínica Sonrisas",
  organizationSlug: "clinica-sonrisas",
};

beforeEach(() => {
  useSessionStore.setState({ accessToken: null, user: null, status: "unauthenticated" });
});

describe("HomeWelcome", () => {
  it("un USER ve el saludo con su nombre y la clínica, y los atajos de pacientes, sin Marca", () => {
    useSessionStore.setState({ accessToken: "t", user: ANA, status: "authenticated" });
    renderWithProviders(<HomeWelcome />);

    expect(screen.getByRole("heading", { name: "Hola, Ana" })).toBeInTheDocument();
    expect(screen.getByText("Clínica Sonrisas")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Nuevo paciente/ })).toHaveAttribute("href", "/patients/new");
    expect(screen.getByRole("link", { name: /Buscar paciente/ })).toHaveAttribute("href", "/patients");
    expect(screen.queryByRole("link", { name: /Personalizar marca/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Cerrar sesión/ })).not.toBeInTheDocument();
  });

  it("un ADMIN ve además el atajo para personalizar la marca", () => {
    useSessionStore.setState({ accessToken: "t", user: { ...ANA, role: "ADMIN" }, status: "authenticated" });
    renderWithProviders(<HomeWelcome />);

    expect(screen.getByRole("link", { name: /Personalizar marca/ })).toHaveAttribute("href", "/settings/branding");
  });

  it("sin nombre completo saluda sin nombre", () => {
    useSessionStore.setState({ accessToken: "t", user: { ...ANA, fullName: "" }, status: "authenticated" });
    renderWithProviders(<HomeWelcome />);

    expect(screen.getByRole("heading", { name: "Hola" })).toBeInTheDocument();
  });
});
