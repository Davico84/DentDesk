import { describe, it, expect, beforeEach } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { axiosInstance } from "./httpClient";

beforeEach(() => {
  useSessionStore.setState({ accessToken: "expirado", user: null, status: "authenticated" });
});

describe("httpClient — interceptor de refresh", () => {
  it("ante 401 renueva el token y reintenta la petición de forma transparente", async () => {
    let protectedCalls = 0;
    server.use(
      http.get("*/protected", () => {
        protectedCalls += 1;
        return protectedCalls === 1
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json({ ok: true });
      }),
      http.post("*/auth/refresh", () =>
        HttpResponse.json({ accessToken: "nuevo", user: { id: 1, email: "a@b.c", fullName: "Ana Test", role: "MEMBER", organizationId: 5, organizationName: "Clínica Test", organizationSlug: "clinica-test" } }),
      ),
    );

    const { data } = await axiosInstance.get("/protected");

    expect(data).toEqual({ ok: true });
    expect(protectedCalls).toBe(2); // 401 + reintento
    expect(useSessionStore.getState().accessToken).toBe("nuevo");
  });

  it("si el refresh falla, cierra la sesión y propaga el error", async () => {
    server.use(
      http.get("*/protected", () => new HttpResponse(null, { status: 401 })),
      http.post("*/auth/refresh", () => new HttpResponse(null, { status: 401 })),
    );

    await expect(axiosInstance.get("/protected")).rejects.toBeTruthy();
    expect(useSessionStore.getState().accessToken).toBeNull();
    expect(useSessionStore.getState().status).toBe("unauthenticated");
  });
});

// El cambio de contraseña (update-authentication-my-profile) es la única ruta de /auth que exige sesión: un 401 ahí
// es un access vencido, no credenciales malas (esas son 400). Se renueva y se reintenta una vez con el mismo cuerpo.
describe("httpClient — 401 al cambiar la contraseña", () => {
  const body = { currentPassword: "actual-123", newPassword: "nueva-4567" };

  it("con el access vencido renueva y reintenta una vez con el mismo cuerpo", async () => {
    const received: unknown[] = [];
    server.use(
      http.post("*/auth/password", async ({ request }) => {
        received.push(await request.json());
        return received.length === 1
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json({ accessToken: "tras-cambio", user: null });
      }),
      http.post("*/auth/refresh", () => HttpResponse.json({ accessToken: "renovado", user: null })),
    );

    const { data } = await axiosInstance.post("/auth/password", body);

    expect(received).toEqual([body, body]);
    expect(data.accessToken).toBe("tras-cambio");
  });

  it("si la sesión ya no se puede renovar, cierra la sesión sin reintentar el cambio", async () => {
    let calls = 0;
    server.use(
      http.post("*/auth/password", () => {
        calls += 1;
        return new HttpResponse(null, { status: 401 });
      }),
      http.post("*/auth/refresh", () => new HttpResponse(null, { status: 401 })),
    );

    await expect(axiosInstance.post("/auth/password", body)).rejects.toBeTruthy();
    expect(calls).toBe(1);
    expect(useSessionStore.getState().status).toBe("unauthenticated");
  });
});
