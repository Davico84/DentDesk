import { describe, it, expect } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { PatientListScreen } from "./PatientListScreen";

const ADA = { id: 1, fullName: "Ada Lovelace", document: "A1", birthDate: "1990-01-01", minor: false, status: "ACTIVE" };
const BRUNO = { id: 2, fullName: "Bruno Díaz", document: "B1", birthDate: "1985-01-01", minor: false, status: "ACTIVE" };

/** Responde el listado filtrando por el query param, como el backend. */
function listHandler() {
  return http.get("*/patients", ({ request }) => {
    const query = new URL(request.url).searchParams.get("query")?.toLowerCase() ?? "";
    const all = [ADA, BRUNO];
    const content = query ? all.filter((p) => p.fullName.toLowerCase().includes(query)) : all;
    return HttpResponse.json({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true });
  });
}

describe("PatientListScreen", () => {
  it("lista los pacientes de la clínica", async () => {
    server.use(listHandler());
    renderWithProviders(<PatientListScreen />);

    expect(await screen.findByText("Ada Lovelace")).toBeInTheDocument();
    expect(screen.getByText("Bruno Díaz")).toBeInTheDocument();
  });

  it("filtra al buscar por nombre", async () => {
    server.use(listHandler());
    const user = userEvent.setup();
    renderWithProviders(<PatientListScreen />);

    await screen.findByText("Bruno Díaz");
    await user.type(screen.getByLabelText(/buscar pacientes/i), "Ada");

    // Se espera al RESULTADO ya filtrado: la búsqueda va con debounce y entre medias se pinta el
    // skeleton (queryKey nueva, sin caché). Esperar solo a que Bruno desaparezca pasaría con el
    // skeleton en pantalla, sin haber comprobado nada.
    expect(await screen.findByText("Ada Lovelace")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText("Bruno Díaz")).not.toBeInTheDocument());
  });
});
