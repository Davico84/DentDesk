import { beforeEach, describe, expect, it } from "vitest";
import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { appRoutes } from "@/routes";
import { useSessionStore } from "@/store/useSessionStore";
import { useLogout } from "@/modules/auth/hooks/useAuth";
import { patientsListPath, rememberPatientsList } from "@/modules/patients/utils/listNavigation";

// Scenarios de `patients` › El listado conserva la búsqueda al volver (add-app-shell D8).

const ADA = { id: 1, fullName: "Ada Lovelace", document: "A1", birthDate: "1990-01-01", minor: false, status: "ACTIVE" };

/** Listado de 3 páginas; registra con qué parámetros se pidió. */
let listRequests: URLSearchParams[] = [];

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

function renderAt(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={newClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

beforeEach(() => {
  listRequests = [];
  sessionStorage.clear();
  useSessionStore.setState({
    accessToken: "t",
    user: {
      id: 1,
      email: "ana@clinica.test",
      fullName: "Ana Torres",
      role: "MEMBER",
      organizationId: 5,
      organizationName: "Clínica Sonrisas",
      organizationSlug: "clinica-sonrisas",
    },
    status: "authenticated",
  });
  server.use(
    http.get("*/patients", ({ request }) => {
      const params = new URL(request.url).searchParams;
      listRequests.push(params);
      const page = Number(params.get("page") ?? 0);
      return HttpResponse.json({ content: [ADA], page, size: 20, totalElements: 41, totalPages: 3, last: page >= 2 });
    }),
    http.get("*/patients/1", () => HttpResponse.json({ ...ADA, phone: "+51987654321" })),
    // Lo demás (Historia, catálogos) no importa aquí.
    http.all("*", () => new HttpResponse(null, { status: 404 })),
  );
});

const searchBox = () => screen.getByRole("searchbox", { name: "Buscar pacientes" });

describe("El listado conserva la búsqueda al volver", () => {
  it("la búsqueda y la página quedan en la URL, y al recargar se ve lo mismo", async () => {
    const user = userEvent.setup();
    const router = renderAt("/patients");
    await screen.findByText("Ada Lovelace");

    await user.type(searchBox(), "Lucía");
    await user.click(await screen.findByRole("button", { name: "Siguiente" }));
    await waitFor(() => expect(router.state.location.search).toBe("?q=Luc%C3%ADa&pagina=2"));

    // "Recargar": un router nuevo sobre la misma URL.
    listRequests = [];
    const reloaded = `${router.state.location.pathname}${router.state.location.search}`;
    document.body.innerHTML = "";
    renderAt(reloaded);
    expect(await screen.findByText("Página 2 de 3 · 41 en total")).toBeInTheDocument();
    expect(searchBox()).toHaveValue("Lucía");
    await waitFor(() => expect(listRequests.at(-1)?.get("query")).toBe("Lucía"));
    expect(listRequests.at(-1)?.get("page")).toBe("1");
  });

  it("cambiar la búsqueda vuelve a la primera página", async () => {
    const user = userEvent.setup();
    const router = renderAt("/patients?q=Ada&pagina=3");
    await screen.findByText("Página 3 de 3 · 41 en total");
    await user.type(searchBox(), "x");
    await waitFor(() => expect(router.state.location.search).toBe("?q=Adax"));
  });

  it("volver desde la ficha, tras pasar por la Historia, conserva la búsqueda y la página", async () => {
    const user = userEvent.setup();
    const router = renderAt("/patients?q=Ada&pagina=2&inactivos=1");
    await screen.findByText("Página 2 de 3 · 41 en total");

    await act(() => router.navigate("/patients/1"));
    await act(() => router.navigate("/patients/1/clinical-history"));
    await act(() => router.navigate("/patients/1"));
    await user.click(await screen.findByRole("link", { name: "Volver al listado" }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/patients"));
    expect(router.state.location.search).toBe("?q=Ada&pagina=2&inactivos=1");
    expect(await screen.findByText("Página 2 de 3 · 41 en total")).toBeInTheDocument();
    expect(searchBox()).toHaveValue("Ada");
    expect(screen.getByRole("button", { name: "Incluir dados de baja" })).toHaveAttribute("aria-pressed", "true");
  });

  it("sin listado previo en la pestaña, volver lleva al listado sin filtros", async () => {
    const user = userEvent.setup();
    const router = renderAt("/patients/1");
    await user.click(await screen.findByRole("link", { name: "Volver al listado" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients"));
    expect(router.state.location.search).toBe("");
  });

  it("el listado recordado no sobrevive al cierre de sesión", async () => {
    server.use(http.post("*/auth/logout", () => new HttpResponse(null, { status: 204 })));
    rememberPatientsList("/patients?q=Ada&pagina=2");
    expect(patientsListPath()).toBe("/patients?q=Ada&pagina=2");

    const client = newClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useLogout(), { wrapper });
    await act(() => result.current.mutateAsync());

    expect(patientsListPath()).toBe("/patients");
  });

  it("el atajo Buscar paciente del Inicio llega con el buscador enfocado", async () => {
    const user = userEvent.setup();
    renderAt("/");
    await user.click(await screen.findByRole("link", { name: /Buscar paciente/ }));
    await waitFor(() => expect(searchBox()).toHaveFocus());
  });

  it("una URL guardada que no es del listado se ignora", () => {
    rememberPatientsList("/settings/branding");
    expect(patientsListPath()).toBe("/patients");
  });
});
