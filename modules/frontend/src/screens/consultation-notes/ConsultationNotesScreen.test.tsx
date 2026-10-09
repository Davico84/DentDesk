import { afterEach, describe, expect, it } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Routes, Route } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { ConsultationNotesScreen } from "./ConsultationNotesScreen";

const PATIENT = { id: 1, fullName: "Ada Lovelace", status: "ACTIVE", allergies: ["Látex"] };

const OPEN_EMPTY = {
  id: 10,
  patientId: 1,
  status: "ABIERTA",
  attendedAt: "2026-10-09T14:00:00Z",
  diagnoses: [],
  backgroundEntries: [],
  corrections: [],
};

const OPEN_COMPLETE = {
  ...OPEN_EMPTY,
  reason: "Control",
  examination: "Encía sana",
  treatment: "Profilaxis",
  diagnoses: [{ id: 1, cie10Code: "K02", ubicacion: "GENERAL", tipoDiagnostico: "DEFINITIVO" }],
};

const SEALED = {
  ...OPEN_COMPLETE,
  id: 9,
  status: "SELLADA",
  attendedAt: "2026-09-01T14:00:00Z",
  sealedAt: "2026-09-01T15:00:00Z",
  sealedBy: 7,
  corrections: [
    { id: 3, field: "EXAMINATION", correctedText: "Encía sana; caries 37", reason: "Faltó la pieza", createdAt: "2026-09-02T10:00:00Z" },
  ],
};

function renderScreen({ status = "COMPLETED", notes = [] as unknown[], patient = PATIENT } = {}) {
  server.use(
    http.get("*/patients/1", () => HttpResponse.json(patient)),
    http.get("*/patients/1/clinical-history/enablement", () =>
      HttpResponse.json({ status, enabledForTreatment: status === "COMPLETED" }),
    ),
    http.get("*/patients/1/consultation-notes", () => HttpResponse.json(notes)),
    http.get("*/patients/1/clinical-background", () =>
      HttpResponse.json({
        items: [
          { category: "ALERGIA_RAM", source: "FICHA", description: "Látex" },
          { category: "MEDICACION", source: "NOTA", description: "Warfarina", recordedAt: "2026-09-01T14:00:00Z", noteId: 9, entryId: 5 },
        ],
      }),
    ),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/patients/:id/consultations" element={<ConsultationNotesScreen />} />
    </Routes>,
    { initialEntries: ["/patients/1/consultations"] },
  );
}

afterEach(() => useSessionStore.getState().clear());

describe("ConsultationNotesScreen — apertura", () => {
  it("sin la Historia sellada no ofrece abrir una nota", async () => {
    renderScreen({ status: "DRAFT" });

    expect(await screen.findByText(/empiezan cuando la Historia Clínica está completa/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /nueva nota/i })).not.toBeInTheDocument();
  });

  it("con un paciente dado de baja no ofrece abrir una nota", async () => {
    renderScreen({ patient: { ...PATIENT, status: "INACTIVE" } });

    expect(await screen.findByText(/dado de baja/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /nueva nota/i })).not.toBeInTheDocument();
  });

  it("abre una nota con la fecha y hora actuales", async () => {
    let received: { attendedAt?: string } | null = null;
    server.use(
      http.post("*/patients/1/consultation-notes", async ({ request }) => {
        received = (await request.json()) as { attendedAt?: string };
        return HttpResponse.json(OPEN_EMPTY, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: /nueva nota de consulta/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(Math.abs(Date.parse(received!.attendedAt!) - Date.now())).toBeLessThan(60_000);
  });
});

describe("ConsultationNotesScreen — nota abierta", () => {
  it("una nota vacía se puede descartar", async () => {
    let discarded = false;
    server.use(
      http.delete("*/patients/1/consultation-notes/10", () => {
        discarded = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderScreen({ notes: [OPEN_EMPTY] });

    await user.click(await screen.findByRole("button", { name: /descartar nota vacía/i }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /^descartar$/i }));

    await waitFor(() => expect(discarded).toBe(true));
  });

  it("con contenido no ofrece descartar, y el sellado espera al contenido obligatorio", async () => {
    renderScreen({ notes: [{ ...OPEN_EMPTY, reason: "Control" }] });

    expect(await screen.findByRole("button", { name: /sellar nota/i })).toBeDisabled();
    expect(screen.queryByRole("button", { name: /descartar/i })).not.toBeInTheDocument();
  });

  it("sella una nota completa tras confirmar", async () => {
    let sealed = false;
    server.use(
      http.post("*/patients/1/consultation-notes/10/seal", () => {
        sealed = true;
        return HttpResponse.json({ ...OPEN_COMPLETE, status: "SELLADA", sealedAt: "2026-10-09T15:00:00Z", sealedBy: 7 });
      }),
    );
    const user = userEvent.setup();
    renderScreen({ notes: [OPEN_COMPLETE] });

    await user.click(await screen.findByRole("button", { name: /sellar nota/i }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /^sellar$/i }));

    await waitFor(() => expect(sealed).toBe(true));
  });

  it("guarda el avance con lo escrito", async () => {
    let received: { reason?: string; attendedAt?: string } | null = null;
    server.use(
      http.put("*/patients/1/consultation-notes/10", async ({ request }) => {
        received = (await request.json()) as { reason?: string };
        return HttpResponse.json({ ...OPEN_EMPTY, reason: "Dolor" });
      }),
    );
    const user = userEvent.setup();
    renderScreen({ notes: [OPEN_EMPTY] });

    await user.type(await screen.findByLabelText(/motivo de consulta/i), "Dolor");
    await user.click(screen.getByRole("button", { name: /guardar avance/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received!.reason).toBe("Dolor");
    expect(received!.attendedAt).toBe("2026-10-09T14:00:00.000Z");
  });
});

describe("ConsultationNotesScreen — nota sellada y antecedentes", () => {
  it("muestra la corrección bajo el campo sin tapar el original", async () => {
    renderScreen({ notes: [SEALED] });

    expect(await screen.findByText("Encía sana")).toBeInTheDocument();
    expect(screen.getByText("Encía sana; caries 37")).toBeInTheDocument();
    expect(screen.getByText(/faltó la pieza/i)).toBeInTheDocument();
  });

  it("solo quien selló la nota ve la opción de corregir", async () => {
    useSessionStore.getState().setSession("token", { id: 8, email: "otro@test.local" } as never);
    const { unmount } = renderScreen({ notes: [SEALED] });
    await screen.findByText("Encía sana");
    expect(screen.queryByRole("button", { name: /corregir/i })).not.toBeInTheDocument();
    unmount();

    useSessionStore.getState().setSession("token", { id: 7, email: "autor@test.local" } as never);
    renderScreen({ notes: [SEALED] });
    expect(await screen.findByRole("button", { name: /corregir/i })).toBeInTheDocument();
  });

  it("el autor registra una corrección con campo, texto y motivo", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/consultation-notes/9/corrections", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 4 }, { status: 201 });
      }),
    );
    useSessionStore.getState().setSession("token", { id: 7, email: "autor@test.local" } as never);
    const user = userEvent.setup();
    renderScreen({ notes: [SEALED] });

    await user.click(await screen.findByRole("button", { name: /corregir/i }));
    const dialog = screen.getByRole("dialog");
    await user.selectOptions(within(dialog).getByLabelText(/campo con el error/i), "TREATMENT");
    await user.type(within(dialog).getByLabelText(/texto correcto/i), "Profilaxis y flúor");
    await user.type(within(dialog).getByLabelText(/motivo/i), "Faltó el flúor");
    await user.click(within(dialog).getByRole("button", { name: /registrar corrección/i }));

    await waitFor(() =>
      expect(received).toEqual({ field: "TREATMENT", correctedText: "Profilaxis y flúor", reason: "Faltó el flúor" }),
    );
  });

  it("los antecedentes vigentes dicen de dónde viene cada uno", async () => {
    renderScreen({ notes: [SEALED] });

    const card = await screen.findByRole("region", { name: /antecedentes vigentes/i });
    expect(await within(card).findByText("Látex")).toBeInTheDocument();
    expect(within(card).getByText(/ficha del paciente, sin autoría clínica/i)).toBeInTheDocument();
    expect(within(card).getByText("Warfarina")).toBeInTheDocument();
  });

  it("dentro de la nota abierta, un antecedente vigente de otra consulta se puede dar de baja", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/consultation-notes/10/background-entries", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 6 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderScreen({ notes: [SEALED, OPEN_EMPTY] });

    await user.click(await screen.findByRole("button", { name: /dar de baja/i }));

    await waitFor(() => expect(received).toEqual({ kind: "BAJA", closesEntryId: 5 }));
  });
});
