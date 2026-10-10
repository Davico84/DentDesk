import { describe, it, expect } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { useClinicalHistoryEnablement } from "./useClinicalHistoryApi";

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

/** D8: consumido por treatment-plans para decidir si un paciente puede recibir tratamiento. */
describe("useClinicalHistoryEnablement", () => {
  it("COMPLETED habilita para tratamiento", async () => {
    server.use(
      http.get("*/patients/1/clinical-history/enablement", () =>
        HttpResponse.json({ status: "COMPLETED", enabledForTreatment: true }),
      ),
    );

    const { result } = renderHook(() => useClinicalHistoryEnablement(1), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data?.enabledForTreatment).toBe(true);
  });

  it("DRAFT no habilita para tratamiento", async () => {
    server.use(
      http.get("*/patients/2/clinical-history/enablement", () =>
        HttpResponse.json({ status: "DRAFT", enabledForTreatment: false }),
      ),
    );

    const { result } = renderHook(() => useClinicalHistoryEnablement(2), { wrapper });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data?.enabledForTreatment).toBe(false);
  });

  it("sin patientId no consulta (enabled: false)", () => {
    const { result } = renderHook(() => useClinicalHistoryEnablement(undefined), { wrapper });

    expect(result.current.fetchStatus).toBe("idle");
  });
});
