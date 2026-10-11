import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { RootLayout } from "./RootLayout";

// Scenarios de `project-foundation` › Pantalla de arranque en frío y Fin de la espera de arranque
// (add-cold-start-warmup). El refresh de sesión, primera petición de toda carga, se RETIENE con MSW
// hasta que el test lo suelta; el tiempo se adelanta con timers falsos (solo intervalos y Date:
// los setTimeout de MSW y axios siguen reales).

const USER = {
  id: 1, email: "ana@clinica.test", fullName: "Ana Test", role: "USER",
  organizationId: 5, organizationName: "Clínica Test", organizationSlug: "clinica-test",
};

let refreshCalls = 0;
let release: (ok: boolean) => void = () => {};

function holdRefresh() {
  refreshCalls = 0;
  server.use(
    http.post("*/auth/refresh", async () => {
      refreshCalls++;
      const ok = await new Promise<boolean>((resolve) => {
        release = resolve;
      });
      return ok
        ? HttpResponse.json({ accessToken: "t", user: USER })
        : new HttpResponse(null, { status: 401 });
    }),
    // Lo que pide la app ya cargada (marca de la clínica): no importa aquí.
    http.all("*", () => new HttpResponse(null, { status: 404 })),
  );
}

function renderApp({ strict = false } = {}) {
  const router = createMemoryRouter(
    [{ element: <RootLayout />, children: [{ path: "/", element: <p>App lista</p> }] }],
    { initialEntries: ["/"] },
  );
  const app = <RouterProvider router={router} />;
  return render(strict ? <StrictMode>{app}</StrictMode> : app);
}

const advance = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms));
const finish = async (ok: boolean) => {
  await act(async () => release(ok));
  await screen.findByText("App lista");
};

let reload: ReturnType<typeof vi.fn>;
const originalLocation = window.location;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
  useSessionStore.setState({ accessToken: null, user: null, status: "idle" });
  reload = vi.fn();
  Object.defineProperty(window, "location", { configurable: true, value: { ...originalLocation, reload } });
  holdRefresh();
});

afterEach(async () => {
  await act(async () => release(false));
  vi.useRealTimers();
  Object.defineProperty(window, "location", { configurable: true, value: originalLocation });
  vi.restoreAllMocks();
});

describe("Pantalla de arranque en frío", () => {
  it("si el servidor responde antes de 4 s, solo se ve Cargando… y luego la app", async () => {
    renderApp();
    expect(screen.getByText("Cargando…", { selector: "span" })).toBeInTheDocument();
    await advance(3000);
    expect(screen.queryByText("Preparando tu consultorio digital")).not.toBeInTheDocument();
    await finish(false);
  });

  it("a los 4 s explica que el servidor se está preparando, con una barra que no llega a 100", async () => {
    renderApp();
    await advance(4000);
    expect(screen.getByRole("heading", { name: "Preparando tu consultorio digital" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Preparando tu consultorio digital");
    const bar = screen.getByRole("progressbar");
    expect(Number(bar.getAttribute("aria-valuenow"))).toBeLessThan(100);
    expect(screen.getByText(/No cierres esta pestaña/)).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Soporte" })).not.toBeInTheDocument();
  });

  it("desde los 45 s la ayuda dice que ya casi está", async () => {
    renderApp();
    await advance(45_000);
    expect(screen.getByText("Ya casi está…")).toBeInTheDocument();
    expect(Number(screen.getByRole("progressbar").getAttribute("aria-valuenow"))).toBeLessThan(100);
  });

  it("a los 90 s ofrece Reintentar con el foco, y al pulsarlo recarga", async () => {
    renderApp();
    await advance(90_000);
    const retry = screen.getByRole("button", { name: "Reintentar" });
    expect(screen.getByRole("heading", { name: "Está tardando más de lo normal" })).toBeInTheDocument();
    expect(retry).toHaveFocus();
    // add-support-contact: el contacto de soporte va abierto, en vez de remitir al administrador.
    const support = screen.getByRole("region", { name: "Soporte" });
    expect(support).toHaveTextContent("¿Sigue sin entrar? Escríbeme");
    expect(support).toHaveTextContent("David Velarde · Soporte Dentdesk");
    expect(screen.queryByText(/avisa al administrador/)).not.toBeInTheDocument();
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(retry);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("sin conexión muestra su propio mensaje y recarga al volver la red", async () => {
    const onLine = vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
    renderApp();
    expect(screen.getByRole("heading", { name: "Sin conexión a internet" })).toBeInTheDocument();
    expect(screen.queryByText("Preparando tu consultorio digital")).not.toBeInTheDocument();
    // Sin red, WhatsApp y el correo no se abren: no se ofrece el contacto.
    expect(screen.queryByRole("region", { name: "Soporte" })).not.toBeInTheDocument();

    onLine.mockReturnValue(true);
    await act(async () => {
      window.dispatchEvent(new Event("online"));
    });
    expect(reload).toHaveBeenCalledTimes(1);
  });
});

describe("Fin de la espera de arranque", () => {
  it("con la sesión restaurada tras la espera, la pantalla desaparece y sigue la sesión", async () => {
    renderApp();
    await advance(10_000);
    await finish(true);
    expect(screen.queryByText("Preparando tu consultorio digital")).not.toBeInTheDocument();
    expect(useSessionStore.getState().status).toBe("authenticated");
  });

  it("con error tras la espera, la pantalla desaparece y la sesión queda limpia", async () => {
    renderApp();
    await advance(10_000);
    await finish(false);
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(useSessionStore.getState().status).toBe("unauthenticated");
    expect(useSessionStore.getState().user).toBeNull();
  });

  it("si la respuesta llega después de los 90 s sin pulsar Reintentar, la app continúa", async () => {
    renderApp();
    await advance(95_000);
    await finish(true);
    expect(screen.queryByRole("button", { name: "Reintentar" })).not.toBeInTheDocument();
    expect(reload).not.toHaveBeenCalled();
  });

  it("en StrictMode: una sola petición, la pantalla una vez y sin intervalos pendientes al terminar", async () => {
    const created = vi.spyOn(globalThis, "setInterval");
    const cleared = vi.spyOn(globalThis, "clearInterval");
    const { unmount } = renderApp({ strict: true });
    await advance(4000);
    expect(screen.getAllByRole("heading", { name: "Preparando tu consultorio digital" })).toHaveLength(1);
    await finish(true);
    unmount();

    expect(refreshCalls).toBe(1);
    const ids = created.mock.results.map((r) => r.value);
    const clearedIds = new Set(cleared.mock.calls.map((c) => c[0]));
    expect(ids.length).toBeGreaterThan(0);
    expect(ids.filter((id) => !clearedIds.has(id))).toEqual([]);
  });
});
