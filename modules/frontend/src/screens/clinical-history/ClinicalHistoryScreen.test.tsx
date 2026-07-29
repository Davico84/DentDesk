import { describe, it, expect } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Routes, Route } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { ClinicalHistoryScreen } from "./ClinicalHistoryScreen";

const EMPTY_DRAFT = {
  id: 1,
  patientId: 1,
  status: "DRAFT" as const,
  fullName: "Ada Lovelace",
  motivoConsulta: "",
  enfermedadActual: "",
  antecedentesPersonales: "",
  examenClinico: "",
  pronostico: "",
  alergiasRevisado: false,
  alergiasDetalle: "",
  medicacionRevisado: false,
  medicacionDetalle: "",
  antecedentesFamiliaresRevisado: false,
  antecedentesFamiliaresDetalle: "",
  diagnosticos: [],
};

const COMPLETED_HISTORY = {
  ...EMPTY_DRAFT,
  status: "COMPLETED" as const,
  motivoConsulta: "Dolor en molar inferior",
  enfermedadActual: "Sensibilidad al frío hace 3 días",
  antecedentesPersonales: "Sin antecedentes relevantes",
  examenClinico: "Caries profunda en pieza 46",
  pronostico: "Reservado, requiere endodoncia",
  alergiasRevisado: true,
  alergiasDetalle: "",
  medicacionRevisado: true,
  medicacionDetalle: "",
  antecedentesFamiliaresRevisado: true,
  antecedentesFamiliaresDetalle: "",
  diagnosticos: [{ id: 1, cie10Code: "K02.1", ubicacion: "PIEZA", fdiNumber: 46, tipoDiagnostico: "D" }],
  completedAt: "2026-07-28T10:00:00Z",
};

function renderScreen(history: Record<string, unknown>) {
  server.use(http.get("*/patients/1/clinical-history", () => HttpResponse.json(history)));
  return renderWithProviders(
    <Routes>
      <Route path="/patients/:id/clinical-history" element={<ClinicalHistoryScreen />} />
    </Routes>,
    { initialEntries: ["/patients/1/clinical-history"] },
  );
}

describe("ClinicalHistoryScreen — guardado incremental", () => {
  it("guarda una sección aunque el resto quede vacío", async () => {
    let received: unknown = null;
    server.use(
      http.put("*/patients/1/clinical-history", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ ...EMPTY_DRAFT, motivoConsulta: "Dolor en molar inferior" });
      }),
    );
    const user = userEvent.setup();
    renderScreen(EMPTY_DRAFT);

    const motivo = await screen.findByLabelText(/motivo de consulta/i);
    await user.type(motivo, "Dolor en molar inferior");
    await user.click(screen.getByRole("button", { name: /guardar avance/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect((received as { motivoConsulta?: string }).motivoConsulta).toBe("Dolor en molar inferior");
    // El resto de campos, aún vacíos, viaja como `undefined` — el guardado incremental no exige nada más.
    expect((received as { examenClinico?: string }).examenClinico).toBeUndefined();
  });
});

describe("ClinicalHistoryScreen — checklist bloquea el sello", () => {
  it("el diálogo de completar muestra lo que falta y bloquea la confirmación", async () => {
    const user = userEvent.setup();
    renderScreen(EMPTY_DRAFT);

    await user.click(await screen.findByRole("button", { name: /completar historia clínica/i }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/todavía falta información/i)).toBeInTheDocument();
    expect(within(dialog).getByText("Motivo de consulta")).toBeInTheDocument();
    expect(within(dialog).getByText(/al menos un diagnóstico/i)).toBeInTheDocument();

    const confirm = within(dialog).getByRole("button", { name: "Completar" });
    expect(confirm).toBeDisabled();
  });
});

describe("ClinicalHistoryScreen — historia sellada en modo lectura", () => {
  it("una historia COMPLETED no permite editar ni ofrece guardar o completar de nuevo", async () => {
    renderScreen(COMPLETED_HISTORY);

    expect(await screen.findByText("Completada")).toBeInTheDocument();
    expect(screen.getByLabelText(/motivo de consulta/i)).toBeDisabled();
    expect(screen.queryByRole("button", { name: /guardar avance/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /completar historia clínica/i })).not.toBeInTheDocument();
  });
});
