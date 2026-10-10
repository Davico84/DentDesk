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

/**
 * `ClinicalHistoryEditor` consulta el odontograma para reflejar el requisito nuevo de
 * `add-dental-chart` (design.md D4/D5b) — 404 por defecto (sin odontograma), salvo que el test lo
 * pise explícitamente.
 */
function renderScreen(history: Record<string, unknown>, dentalChart?: Record<string, unknown>) {
  server.use(http.get("*/patients/1/clinical-history", () => HttpResponse.json(history)));
  server.use(
    http.get("*/patients/1/dental-chart", () =>
      dentalChart ? HttpResponse.json(dentalChart) : HttpResponse.json({ type: "/errors/dental-chart-block-not-found" }, { status: 404 }),
    ),
  );
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

describe("ClinicalHistoryScreen — cambios sin guardar bloquean el sello", () => {
  it("con todo el checklist en verde pero sin guardar, Completar queda deshabilitado", async () => {
    const READY_BUT_UNSAVED_DRAFT = {
      ...COMPLETED_HISTORY,
      status: "DRAFT" as const,
      completedAt: undefined,
    };
    const user = userEvent.setup();
    renderScreen(READY_BUT_UNSAVED_DRAFT);

    // El checklist en vivo ya muestra todo listo (los valores vienen precargados del servidor)...
    const motivo = await screen.findByLabelText(/motivo de consulta/i);
    // ...pero tipear sin guardar deja el formulario "dirty": el sello debe seguir bloqueado.
    await user.type(motivo, " adicional");

    expect(await screen.findByText(/guarda el avance antes de completar/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /completar historia clínica/i })).toBeDisabled();
  });
});

describe("ClinicalHistoryScreen — requisito del odontograma (add-dental-chart)", () => {
  it("sin odontograma, muestra el aviso y enlaza a crearlo", async () => {
    renderScreen(EMPTY_DRAFT);

    const link = await screen.findByRole("link", { name: /ir al odontograma/i });
    expect(link).toHaveAttribute("href", "/patients/1/dental-chart");
    expect(screen.getByText(/falta el odontograma inicial/i)).toBeInTheDocument();
  });

  it("con odontograma ya creado, no muestra el aviso", async () => {
    renderScreen(EMPTY_DRAFT, { id: 1, patientId: 1, status: "OPEN", findings: [] });

    await screen.findByLabelText(/motivo de consulta/i);
    await waitFor(() => expect(screen.queryByRole("link", { name: /ir al odontograma/i })).not.toBeInTheDocument());
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
