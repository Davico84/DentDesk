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
        HttpResponse.json({ accessToken: "nuevo", user: { id: 1, email: "a@b.c", fullName: "Ana Test", role: "USER", organizationId: 5, organizationName: "Clínica Test", organizationSlug: "clinica-test" } }),
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
