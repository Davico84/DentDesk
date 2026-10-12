import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { useSlowRequestStore } from "@/store/useSlowRequestStore";
import { axiosInstance } from "@/modules/core/config/httpClient";
import { RootLayout } from "@/layouts/RootLayout";

// Scenarios de `project-foundation` › Aviso de espera durante el uso (update-slow-request-notice).
// La app arranca con el refresh respondido (o retenido, en el último caso); luego una petición se
// retiene con MSW y el tiempo se adelanta con timers falsos.

const releases = new Map<string, (status: number) => void>();
let releaseRefresh: (() => void) | null = null;

function holdRefresh() {
  server.use(
    http.post("*/auth/refresh", async () => {
      await new Promise<void>((resolve) => {
        releaseRefresh = resolve;
      });
      return new HttpResponse(null, { status: 401 });
    }),
  );
}

function hold(path: string) {
  server.use(
    http.get(`*${path}`, async () => {
      const status = await new Promise<number>((resolve) => releases.set(path, resolve));
      return status === 200 ? HttpResponse.json({ ok: true }) : new HttpResponse(null, { status });
    }),
  );
}

function renderApp() {
  const router = createMemoryRouter(
    [{ element: <RootLayout />, children: [{ path: "/", element: <p>App lista</p> }] }],
    { initialEntries: ["/"] },
  );
  return render(<RouterProvider router={router} />);
}

const advance = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms));

async function startedApp() {
  server.use(
    http.post("*/auth/refresh", () => new HttpResponse(null, { status: 401 })),
    http.all("*/organizations/*", () => new HttpResponse(null, { status: 404 })),
  );
  renderApp();
  await screen.findByText("App lista");
  // Los timers falsos recién ahora: findByText usa timers reales para esperar.
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] });
}

let reload: ReturnType<typeof vi.fn>;
const originalLocation = window.location;

beforeEach(() => {
  useSessionStore.setState({ accessToken: null, user: null, status: "idle" });
  useSlowRequestStore.getState().reset();
  releases.clear();
  reload = vi.fn();
  Object.defineProperty(window, "location", { configurable: true, value: { ...originalLocation, reload } });
});

afterEach(async () => {
  for (const release of releases.values()) release(200);
  releaseRefresh?.();
  releaseRefresh = null;
  vi.useRealTimers();
  Object.defineProperty(window, "location", { configurable: true, value: originalLocation });
  vi.restoreAllMocks();
});

describe("Aviso de espera durante el uso", () => {
  it("una petición que responde antes de 4 s no muestra aviso", async () => {
    await startedApp();
    hold("/rapida");
    const req = axiosInstance.get("/rapida");
    await advance(2000);
    releases.get("/rapida")!(200);
    await act(() => req);
    await advance(5000);
    expect(screen.queryByText("Despertando el servidor…")).not.toBeInTheDocument();
  });

  it("una petición lenta muestra el aviso en una región status, que se va al responder", async () => {
    await startedApp();
    hold("/lenta");
    const req = axiosInstance.get("/lenta");
    await advance(4100);
    expect(screen.getByRole("status")).toHaveTextContent("Despertando el servidor…");
    expect(screen.getByText(/No cierres esta pestaña/)).toBeInTheDocument();

    releases.get("/lenta")!(200);
    await act(() => req);
    expect(screen.queryByText("Despertando el servidor…")).not.toBeInTheDocument();
    expect(screen.getByText("App lista")).toBeInTheDocument();
  });

  it("con varias lentas, el aviso sigue hasta que responde la última", async () => {
    await startedApp();
    hold("/a");
    hold("/b");
    const a = axiosInstance.get("/a");
    const b = axiosInstance.get("/b");
    await advance(4500);
    releases.get("/a")!(200);
    await act(() => a);
    expect(screen.getByText("Despertando el servidor…")).toBeInTheDocument();
    releases.get("/b")!(200);
    await act(() => b);
    expect(screen.queryByText("Despertando el servidor…")).not.toBeInTheDocument();
  });

  it("si la petición lenta termina con error, el aviso se va", async () => {
    await startedApp();
    hold("/falla");
    const req = axiosInstance.get("/falla").catch((e: unknown) => e);
    await advance(4500);
    expect(screen.getByText("Despertando el servidor…")).toBeInTheDocument();
    releases.get("/falla")!(500);
    await act(() => req);
    expect(screen.queryByText("Despertando el servidor…")).not.toBeInTheDocument();
  });

  it("a los 90 s ofrece Reintentar, que recarga, y el contacto de soporte", async () => {
    await startedApp();
    hold("/eterna");
    void axiosInstance.get("/eterna").catch(() => undefined);
    await advance(90_000);
    expect(screen.getByRole("status")).toHaveTextContent("Está tardando más de lo normal");
    expect(screen.getByRole("link", { name: /WhatsApp/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("sin conexión dice que no hay internet, no que el servidor despierta", async () => {
    await startedApp();
    const onLine = vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
    hold("/sinred");
    void axiosInstance.get("/sinred").catch(() => undefined);
    await advance(4500);
    expect(screen.getByRole("status")).toHaveTextContent("Sin conexión a internet");
    expect(screen.queryByText("Despertando el servidor…")).not.toBeInTheDocument();
    onLine.mockRestore();
  });

  it("durante el arranque solo se ve la pantalla de arranque, no el aviso", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] });
    holdRefresh();
    renderApp();
    await advance(10_000);
    expect(screen.getByRole("heading", { name: "Preparando tu consultorio digital" })).toBeInTheDocument();
    expect(screen.queryByText("Despertando el servidor…")).not.toBeInTheDocument();
  });
});
