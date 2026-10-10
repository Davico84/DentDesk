import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import { Routes, Route } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { PatientDetailScreen } from "./PatientDetailScreen";

const BASE_PATIENT = {
  id: 1,
  fullName: "Ada Lovelace",
  birthDate: "1990-01-01",
  minor: false,
  phone: "+51987654321",
  status: "ACTIVE" as const,
};

function renderDetail(patient: Record<string, unknown>) {
  server.use(http.get("*/patients/1", () => HttpResponse.json(patient)));
  return renderWithProviders(
    <Routes>
      <Route path="/patients/:id" element={<PatientDetailScreen />} />
    </Routes>,
    { initialEntries: ["/patients/1"] },
  );
}

/**
 * update-patients D1: el aviso de teléfono compartido es no bloqueante — se muestra como
 * información persistente en la ficha, no como un error.
 */
describe("PatientDetailScreen — aviso de teléfono compartido", () => {
  it("muestra el aviso cuando el teléfono está compartido con otros pacientes", async () => {
    renderDetail({ ...BASE_PATIENT, phoneSharedWithOtherPatients: true });

    expect(
      await screen.findByText(/este teléfono también está registrado a nombre de otro paciente/i),
    ).toBeInTheDocument();
  });

  it("no muestra el aviso cuando el teléfono no está compartido", async () => {
    renderDetail({ ...BASE_PATIENT, phoneSharedWithOtherPatients: false });

    expect(await screen.findByText("Ada Lovelace")).toBeInTheDocument();
    expect(
      screen.queryByText(/este teléfono también está registrado a nombre de otro paciente/i),
    ).not.toBeInTheDocument();
  });
});
