import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { delay, http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { appRoutes } from "@/routes";
import { useSessionStore, type SessionUser } from "@/store/useSessionStore";
import { useSidebarStore } from "@/store/useSidebarStore";

// Scenarios de `app-shell` (add-app-shell): layout común, navegación por configuración, acceso
// denegado, identidad, responsive y barra contraíble. Las rutas son las reales (`appRoutes`).

const ANA: SessionUser = {
  id: 1,
  email: "ana@clinica.test",
  fullName: "Ana Torres",
  role: "MEMBER",
  organizationId: 5,
  organizationName: "Clínica Sonrisas",
  organizationSlug: "clinica-sonrisas",
};

function signIn(overrides: Partial<SessionUser> = {}) {
  useSessionStore.setState({ accessToken: "t", user: { ...ANA, ...overrides }, status: "authenticated" });
}

function setWidth(px: number) {
  Object.defineProperty(window, "innerWidth", { configurable: true, value: px });
}

/** Peticiones que salieron a la API en este test (para afirmar que algo NO se pidió). */
let requested: string[] = [];

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

beforeEach(() => {
  requested = [];
  setWidth(1280);
  localStorage.clear();
  useSidebarStore.setState({ collapsed: false });
  useSessionStore.setState({ accessToken: null, user: null, status: "unauthenticated" });
  // Las pantallas piden sus datos; aquí solo importa el marco: todo responde 404.
  server.use(
    http.all("*", ({ request }) => {
      requested.push(new URL(request.url).pathname);
      return new HttpResponse(null, { status: 404 });
    }),
  );
});

afterEach(() => setWidth(1024));

const sidebarNav = () => screen.getAllByRole("navigation", { name: "Principal" })[0];
// La cabecera del shell es el primer `banner` (las pantallas tienen su propio `<header>` dentro del main).
const shellHeader = () => screen.getAllByRole("banner")[0];

describe("Layout privado común", () => {
  it("una ruta privada se muestra dentro del shell, con un solo <main>", async () => {
    signIn();
    renderAt("/");
    expect(await screen.findByRole("heading", { name: "Hola, Ana" })).toBeInTheDocument();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(within(screen.getByRole("main")).getByRole("heading", { name: "Hola, Ana" })).toBeInTheDocument();
    expect(sidebarNav()).toBeInTheDocument();
    expect(shellHeader()).toHaveTextContent("Clínica Sonrisas");
  });

  it("login y registro se muestran sin shell", async () => {
    renderAt("/auth/login");
    expect(await screen.findByRole("button", { name: /iniciar sesión/i })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Principal" })).not.toBeInTheDocument();
  });
});

describe("Navegación declarada por configuración", () => {
  it("Pacientes queda activo en las subrutas de un paciente, y solo él", async () => {
    signIn();
    renderAt("/patients/7/clinical-history");
    const nav = await waitFor(sidebarNav);
    expect(within(nav).getByRole("link", { name: "Pacientes" })).toHaveAttribute("aria-current", "page");
    expect(within(nav).getByRole("link", { name: "Inicio" })).not.toHaveAttribute("aria-current");
  });

  // add-appointments 4.1: la Agenda la ven MEMBER y ADMIN, y queda activa en /appointments.
  it.each(["MEMBER", "ADMIN"] as const)("un %s ve la Agenda, activa en /appointments", async (role) => {
    signIn({ role });
    renderAt("/appointments");
    const nav = await waitFor(sidebarNav);
    expect(within(nav).getByRole("link", { name: "Agenda" })).toHaveAttribute("aria-current", "page");
    expect(within(nav).getByRole("link", { name: "Pacientes" })).not.toHaveAttribute("aria-current");
  });

  it("un USER no ve el ítem de Marca; un ADMIN sí", async () => {
    signIn();
    renderAt("/");
    const nav = await waitFor(sidebarNav);
    expect(within(nav).queryByRole("link", { name: "Marca" })).not.toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Pacientes" })).toBeInTheDocument();
  });

  it("un ADMIN ve el ítem de Marca", async () => {
    signIn({ role: "ADMIN" });
    renderAt("/");
    const nav = await waitFor(sidebarNav);
    expect(within(nav).getByRole("link", { name: "Marca" })).toHaveAttribute("href", "/settings/branding");
  });
});

describe("Acceso denegado dentro del shell", () => {
  it("un USER que llega por URL a Marca ve el aviso en el shell, con foco en el título y sin pedir datos", async () => {
    signIn();
    renderAt("/settings/branding");
    const title = await screen.findByRole("heading", { name: "Acceso denegado" });
    expect(within(screen.getByRole("main")).getByRole("heading", { name: "Acceso denegado" })).toBe(title);
    expect(screen.getAllByRole("main")).toHaveLength(1);
    await waitFor(() => expect(title).toHaveFocus());
    expect(screen.getByRole("link", { name: "Volver al inicio" })).toHaveAttribute("href", "/");
    expect(requested.filter((p) => p.includes("branding"))).toEqual([]);
  });
});

describe("Identidad del usuario y de la clínica", () => {
  it("con nombre: muestra el nombre, el rol y la clínica", async () => {
    signIn({ role: "ADMIN" });
    renderAt("/");
    const header = await waitFor(shellHeader);
    expect(header).toHaveTextContent("Ana Torres");
    expect(header).toHaveTextContent("Administrador");
    expect(header).toHaveTextContent("Clínica Sonrisas");
  });

  it("sin nombre: muestra el correo en su lugar", async () => {
    signIn({ fullName: "" });
    renderAt("/");
    const header = await waitFor(shellHeader);
    expect(header).toHaveTextContent("ana@clinica.test");
    expect(header).toHaveTextContent("Personal");
  });

  it("cerrar sesión queda deshabilitado mientras la petición está en curso", async () => {
    signIn();
    server.use(
      http.post("*/auth/logout", async () => {
        await delay("infinite");
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderAt("/");
    const user = userEvent.setup();
    const nav = await waitFor(sidebarNav);
    const aside = nav.closest("aside")!;
    await user.click(within(aside).getByRole("button", { name: "Cerrar sesión" }));
    expect(await within(aside).findByRole("button", { name: "Cerrando sesión…" })).toBeDisabled();
  });
});

describe("Navegación responsive (celular)", () => {
  beforeEach(() => setWidth(375));

  it("el botón de menú abre el cajón y lo refleja con aria-expanded", async () => {
    signIn();
    renderAt("/");
    const user = userEvent.setup();
    const menu = await screen.findByRole("button", { name: "Abrir menú" });
    expect(menu).toHaveAttribute("aria-expanded", "false");
    await user.click(menu);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(menu).toHaveAttribute("aria-expanded", "true");
    expect(menu).toHaveAttribute("aria-controls", "mobile-nav");
  });

  it("elegir un ítem navega y cierra el cajón", async () => {
    signIn();
    const router = renderAt("/");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Abrir menú" }));
    const drawer = await screen.findByRole("dialog");
    await user.click(within(drawer).getByRole("link", { name: "Pacientes" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe("/patients");
  });

  it("Escape cierra el cajón sin navegar", async () => {
    signIn();
    const router = renderAt("/");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Abrir menú" }));
    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe("/");
  });

  it("clic fuera cierra el cajón sin navegar", async () => {
    signIn();
    const router = renderAt("/");
    // Con el cajón abierto Radix deja el fondo con `pointer-events: none`: el clic igual lo detecta.
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    await user.click(await screen.findByRole("button", { name: "Abrir menú" }));
    await screen.findByRole("dialog");
    await user.click(document.body);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe("/");
  });
});

describe("Navegación responsive (tablet)", () => {
  beforeEach(() => setWidth(768));

  it("la barra muestra solo íconos con nombre accesible, tooltip al enfocar y sin title", async () => {
    signIn();
    renderAt("/");
    const nav = await waitFor(sidebarNav);
    const link = within(nav).getByRole("link", { name: "Pacientes" });
    expect(link).toHaveAttribute("aria-label", "Pacientes");
    expect(link).not.toHaveAttribute("title");
    act(() => link.focus());
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Pacientes");
    // El nombre se anuncia una vez: el tooltip no se enlaza también como descripción.
    expect(link).not.toHaveAttribute("aria-describedby");
  });

  it("el botón para contraer no se muestra bajo 1024 px", async () => {
    signIn();
    renderAt("/");
    await waitFor(sidebarNav);
    expect(screen.queryByRole("button", { name: /barra lateral/ })).not.toBeInTheDocument();
  });
});

describe("Barra lateral contraíble en escritorio", () => {
  it("contraer muestra solo íconos y lo refleja con aria-expanded; expandir lo revierte", async () => {
    signIn();
    renderAt("/");
    const user = userEvent.setup();
    const collapse = await screen.findByRole("button", { name: "Contraer barra lateral" });
    expect(collapse).toHaveAttribute("aria-expanded", "true");
    expect(within(sidebarNav()).getByRole("link", { name: "Pacientes" })).not.toHaveAttribute("aria-label");

    await user.click(collapse);
    const expand = screen.getByRole("button", { name: "Expandir barra lateral" });
    expect(expand).toHaveAttribute("aria-expanded", "false");
    expect(within(sidebarNav()).getByRole("link", { name: "Pacientes" })).toHaveAttribute("aria-label", "Pacientes");

    await user.click(expand);
    expect(screen.getByRole("button", { name: "Contraer barra lateral" })).toHaveAttribute("aria-expanded", "true");
  });

  it("la preferencia queda guardada y se mantiene al cambiar de sección", async () => {
    signIn();
    renderAt("/");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Contraer barra lateral" }));
    expect(JSON.parse(localStorage.getItem("odr-sidebar") ?? "{}")).toMatchObject({ state: { collapsed: true } });

    await user.click(within(sidebarNav()).getByRole("link", { name: "Pacientes" }));
    expect(await screen.findByRole("button", { name: "Expandir barra lateral" })).toBeInTheDocument();
  });
});
