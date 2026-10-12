import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { useSlowRequestStore } from "@/store/useSlowRequestStore";
import { axiosInstance } from "./httpClient";

// update-slow-request-notice D1: el cliente HTTP marca las peticiones que pasan de 4 s sin responder.
// Las respuestas se retienen con MSW y se liberan a mano; el tiempo se adelanta con timers falsos.

const releases = new Map<string, (status: number) => void>();

function hold(path: string) {
  server.use(
    http.get(`*${path}`, async () => {
      const status = await new Promise<number>((resolve) => releases.set(path, resolve));
      return status === 200 ? HttpResponse.json({ ok: true }) : new HttpResponse(null, { status });
    }),
  );
}

const slow = () => useSlowRequestStore.getState().slowIds.size;
const advance = (ms: number) => vi.advanceTimersByTimeAsync(ms);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
  useSlowRequestStore.getState().reset();
  releases.clear();
});

afterEach(() => {
  for (const release of releases.values()) release(200);
  vi.useRealTimers();
});

describe("peticiones lentas", () => {
  it("una petición que responde antes de 4 s nunca se marca", async () => {
    hold("/rapida");
    const req = axiosInstance.get("/rapida");
    await advance(1000);
    releases.get("/rapida")!(200);
    await req;
    await advance(5000);
    expect(slow()).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });

  it("una petición pendiente a los 4 s se marca, y al responder se quita", async () => {
    hold("/lenta");
    const req = axiosInstance.get("/lenta");
    await advance(3900);
    expect(slow()).toBe(0);
    await advance(200);
    expect(slow()).toBe(1);
    expect(useSlowRequestStore.getState().slowSince).not.toBeNull();

    releases.get("/lenta")!(200);
    await req;
    expect(slow()).toBe(0);
    expect(useSlowRequestStore.getState().slowSince).toBeNull();
  });

  it("con dos lentas, sigue marcada hasta que responde la última", async () => {
    hold("/a");
    hold("/b");
    const a = axiosInstance.get("/a");
    const b = axiosInstance.get("/b");
    await advance(4500);
    expect(slow()).toBe(2);

    releases.get("/a")!(200);
    await a;
    expect(slow()).toBe(1);

    releases.get("/b")!(200);
    await b;
    expect(slow()).toBe(0);
  });

  it("una petición lenta que termina con error también se quita", async () => {
    hold("/falla");
    const req = axiosInstance.get("/falla").catch((e: unknown) => e);
    await advance(4500);
    expect(slow()).toBe(1);
    releases.get("/falla")!(500);
    await req;
    expect(slow()).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });
});
