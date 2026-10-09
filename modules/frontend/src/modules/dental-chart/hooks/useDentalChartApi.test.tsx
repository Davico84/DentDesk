import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { toast } from "sonner";
import { server } from "@/test/mocks/server";
import type { DentalChartFindingRequest } from "@/modules/core/services/generated/model";
import { useAddFinding } from "./useDentalChartApi";

// Solo en este archivo: el aviso por edad ES un toast, y es lo que se verifica.
vi.mock("sonner", () => ({ toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const TEMPORARY_TOOTH_ON_ADULT: DentalChartFindingRequest = {
  type: "GIROVERSION",
  location: { locationKind: "TOOTH", toothA: 55 },
};

function respondWith(denticionWarning: boolean) {
  server.use(
    http.post("*/patients/1/dental-chart/findings", () =>
      HttpResponse.json({ finding: { id: 1 }, denticionWarning }, { status: 201 }),
    ),
  );
}

describe("useAddFinding — aviso no bloqueante por edad (spec: Avisar cuando la pieza no corresponde a la edad)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("registra el hallazgo y además avisa de la incoherencia con la edad, sin bloquear", async () => {
    respondWith(true);
    const { result } = renderHook(() => useAddFinding(1), { wrapper });

    result.current.mutate(TEMPORARY_TOOTH_ON_ADULT);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(toast.success).toHaveBeenCalledWith("Hallazgo agregado");
    expect(toast.warning).toHaveBeenCalledWith(expect.stringMatching(/dentición temporal en un paciente adulto/));
    expect(toast.error).not.toHaveBeenCalled();
  });

  it("sin incoherencia, no muestra el aviso", async () => {
    respondWith(false);
    const { result } = renderHook(() => useAddFinding(1), { wrapper });

    result.current.mutate(TEMPORARY_TOOTH_ON_ADULT);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(toast.warning).not.toHaveBeenCalled();
  });
});
