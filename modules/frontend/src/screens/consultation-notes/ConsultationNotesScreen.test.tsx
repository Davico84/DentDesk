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

/** Catálogo mínimo del odontograma: el color (rojo/azul) de cada hallazgo sale de aquí. */
const CATALOG = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO" },
  { type: "CORONA", subtype: "CM", acronym: "CM", scope: "TOOTH", colorBehavior: "SEGUN_ESTADO" },
  { type: "SELLANTE", subtype: "", acronym: "S", scope: "SURFACE", colorBehavior: "SEGUN_ESTADO" },
  { type: "DIASTEMA", subtype: "", acronym: "", scope: "BETWEEN_TEETH", colorBehavior: "SIEMPRE_AZUL" },
];

function renderScreen({
  status = "COMPLETED",
  notes = [] as unknown[],
  patient = PATIENT,
  findings = [] as unknown[],
  suggestions = [] as unknown[],
} = {}) {
  server.use(
    http.get("*/patients/1/dental-chart", () => HttpResponse.json({ id: 1, status: "SEALED", findings })),
    http.get("*/dental-chart/catalog", () => HttpResponse.json(CATALOG)),
    http.get("*/cie10/suggestions", () => HttpResponse.json(suggestions)),
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

  it("sella una nota completa tras confirmar y refresca los antecedentes vigentes", async () => {
    let sealed = false;
    let backgroundReads = 0;
    server.use(
      http.post("*/patients/1/consultation-notes/10/seal", () => {
        sealed = true;
        return HttpResponse.json({ ...OPEN_COMPLETE, status: "SELLADA", sealedAt: "2026-10-09T15:00:00Z", sealedBy: 7 });
      }),
    );
    const user = userEvent.setup();
    renderScreen({ notes: [OPEN_COMPLETE] });
    // Después de renderScreen: este handler cuenta las lecturas y gana sobre el de renderScreen.
    server.use(
      http.get("*/patients/1/clinical-background", () => {
        backgroundReads += 1;
        return HttpResponse.json({ items: [] });
      }),
    );

    await user.click(await screen.findByRole("button", { name: /sellar nota/i }));
    const readsBeforeSeal = backgroundReads;
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /^sellar$/i }));

    await waitFor(() => expect(sealed).toBe(true));
    // Los asientos de la nota pasan a ser vigentes al sellarla: la vista tiene que volver a pedirse.
    await waitFor(() => expect(backgroundReads).toBeGreaterThan(readsBeforeSeal));
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

    // Sin antecedentes en la nota, la sección arranca plegada (es opcional).
    await user.click(await screen.findByRole("button", { name: /Antecedentes nuevos \(opcional\)/ }));
    await user.click(await screen.findByRole("button", { name: /dar de baja/i }));

    await waitFor(() => expect(received).toEqual({ kind: "BAJA", closesEntryId: 5 }));
  });
});

// --- update-consultation-note-findings: hallazgos del odontograma en la nota ---

const CARIES_16 = {
  id: 101,
  type: "LESION_CARIES",
  subtype: "CD",
  location: { locationKind: "SURFACE", toothA: 16, surface: "OCLUSAL" },
};
const CORONA_26_POOR = { id: 102, type: "CORONA", subtype: "CM", condition: "POOR", location: { locationKind: "TOOTH", toothA: 26 } };
const SELLANTE_36_GOOD = {
  id: 103,
  type: "SELLANTE",
  subtype: "",
  condition: "GOOD",
  location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" },
};
const DIASTEMA_11_21 = { id: 104, type: "DIASTEMA", subtype: "", location: { locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 } };
const CARIES_16_TEXT = "Pieza 16, cara oclusal: Lesión de caries CD (Lesión de caries a nivel de la dentina)";
const K021 = { code: "K021", descripcion: "CARIES DE LA DENTINA", nivel: "SUBCATEGORIA", path: [] };

const attended = (id: number, findingId: number, description: string) => ({
  id,
  findingId,
  description,
  createdAt: "2026-10-09T14:05:00Z",
  createdBy: 7,
});

describe("ConsultationNotesScreen — hallazgos del odontograma", () => {
  it("muestra primero los pendientes rojos y deja los demás plegados", async () => {
    const user = userEvent.setup();
    renderScreen({ notes: [OPEN_EMPTY], findings: [CARIES_16, CORONA_26_POOR, SELLANTE_36_GOOD] });

    const pending = await screen.findByRole("list", { name: "Hallazgos pendientes" });
    expect(within(pending).getAllByRole("listitem")).toHaveLength(2);
    expect(within(pending).getByText(/Pieza 26: Corona .*mal estado/)).toBeInTheDocument();
    expect(screen.queryByText(/Pieza 36, cara oclusal: Sellante/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Ver los demás hallazgos (1)" }));
    const others = screen.getByRole("list", { name: "Otros hallazgos del odontograma" });
    expect(within(others).getByText(/Sellante, buen estado/)).toBeInTheDocument();
  });

  it("un pendiente ya atendido en una nota sellada muestra la fecha y se puede volver a atender", async () => {
    renderScreen({
      notes: [{ ...SEALED, attendedFindings: [attended(1, 101, CARIES_16_TEXT)] }, OPEN_EMPTY],
      findings: [CARIES_16],
    });

    const pending = await screen.findByRole("list", { name: "Hallazgos pendientes" });
    expect(within(pending).getByText(/Atendido el/)).toBeInTheDocument();
    expect(within(pending).getByRole("button", { name: "Atender" })).toBeEnabled();
  });

  it("atender un hallazgo lo envía con su descripción y agrega la línea al examen sin guardarla", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/consultation-notes/10/findings", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json(attended(5, 101, CARIES_16_TEXT), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderScreen({ notes: [{ ...OPEN_EMPTY, examination: "Encía sana" }], findings: [CARIES_16] });

    const pending = await screen.findByRole("list", { name: "Hallazgos pendientes" });
    await user.click(within(pending).getByRole("button", { name: "Atender" }));

    await waitFor(() => expect(received).toEqual({ findingId: 101, description: CARIES_16_TEXT }));
    await waitFor(() => expect(screen.getByLabelText(/^Examen/)).toHaveValue(`Encía sana\n${CARIES_16_TEXT}`));
    expect(screen.getByText(/Tienes cambios sin guardar/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sellar nota" })).toBeDisabled();
  });

  it("quitar un hallazgo atendido lo desmarca de la nota", async () => {
    let deleted = false;
    server.use(
      http.delete("*/patients/1/consultation-notes/10/findings/5", () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderScreen({ notes: [{ ...OPEN_EMPTY, attendedFindings: [attended(5, 101, CARIES_16_TEXT)] }], findings: [CARIES_16] });

    const list = await screen.findByRole("list", { name: "Hallazgos atendidos en esta consulta" });
    await user.click(within(list).getByRole("button", { name: "Quitar" }));
    await waitFor(() => expect(deleted).toBe(true));
  });

  it("propone el diagnóstico con la pieza y la sugerencia K021, y lo registra al confirmar", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/consultation-notes/10/diagnoses", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json(
          { id: 2, cie10Code: "K021", ubicacion: "PIEZA", fdiNumber: 16, tipoDiagnostico: "DEFINITIVO" },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    renderScreen({
      notes: [{ ...OPEN_EMPTY, attendedFindings: [attended(5, 101, CARIES_16_TEXT)] }],
      findings: [CARIES_16],
      suggestions: [K021],
    });

    await user.click(await screen.findByRole("button", { name: "Proponer diagnóstico" }));
    expect(screen.getByText(/Propuesto desde:/)).toHaveTextContent(CARIES_16_TEXT);
    expect(screen.getByLabelText(/^Ubicación/)).toHaveValue("PIEZA");
    expect(screen.getByLabelText(/Número de pieza/)).toHaveValue("16");

    await user.click(await screen.findByRole("button", { name: /K021.*CARIES DE LA DENTINA/ }));
    await user.selectOptions(screen.getByLabelText(/^Tipo de diagnóstico/), "DEFINITIVO");
    await user.click(screen.getByRole("button", { name: "Agregar diagnóstico" }));

    await waitFor(() =>
      expect(received).toEqual({ cie10Code: "K021", ubicacion: "PIEZA", fdiNumber: 16, tipoDiagnostico: "DEFINITIVO" }),
    );
  });

  it("un hallazgo entre piezas propone la ubicación general", async () => {
    const user = userEvent.setup();
    renderScreen({
      notes: [{ ...OPEN_EMPTY, attendedFindings: [attended(6, 104, "Entre las piezas 11 y 21: Diastema")] }],
      findings: [DIASTEMA_11_21],
    });

    await user.click(await screen.findByRole("button", { name: "Proponer diagnóstico" }));
    expect(screen.getByLabelText(/^Ubicación/)).toHaveValue("GENERAL");
    expect(screen.queryByLabelText(/Número de pieza/)).not.toBeInTheDocument();
  });

  it("sin sugerencias ofrece el buscador, y se puede elegir cualquier otro código", async () => {
    server.use(
      http.get("*/cie10", () => HttpResponse.json([{ code: "K040", descripcion: "PULPITIS", nivel: "SUBCATEGORIA", path: [] }])),
    );
    const user = userEvent.setup();
    renderScreen({
      notes: [{ ...OPEN_EMPTY, attendedFindings: [attended(7, 102, "Pieza 26: Corona CM, mal estado")] }],
      findings: [CORONA_26_POOR],
    });

    await user.click(await screen.findByRole("button", { name: "Proponer diagnóstico" }));
    expect(screen.queryByText("Sugeridos para este hallazgo")).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Diagnóstico (CIE10)"), "pulp");
    await user.click(await screen.findByRole("button", { name: /K040 — PULPITIS/ }));
    expect(screen.getByText("K040")).toBeInTheDocument();
  });

  it("descartar la propuesta no registra ningún diagnóstico", async () => {
    let posted = false;
    server.use(
      http.post("*/patients/1/consultation-notes/10/diagnoses", () => {
        posted = true;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderScreen({
      notes: [{ ...OPEN_EMPTY, attendedFindings: [attended(5, 101, CARIES_16_TEXT)] }],
      findings: [CARIES_16],
      suggestions: [K021],
    });

    await user.click(await screen.findByRole("button", { name: "Proponer diagnóstico" }));
    await user.click(screen.getByRole("button", { name: "Descartar propuesta" }));
    expect(screen.queryByText(/Propuesto desde/)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^Ubicación/)).toHaveValue("GENERAL");
    expect(posted).toBe(false);
  });

  it("la nota sellada muestra los hallazgos atendidos con su descripción guardada", async () => {
    renderScreen({ notes: [{ ...SEALED, attendedFindings: [attended(1, 101, CARIES_16_TEXT)] }] });

    const list = await screen.findByRole("list", { name: "Hallazgos atendidos" });
    expect(within(list).getByText(CARIES_16_TEXT)).toBeInTheDocument();
  });

  it("una nota con hallazgos atendidos no ofrece descartarse", async () => {
    renderScreen({ notes: [{ ...OPEN_EMPTY, attendedFindings: [attended(5, 101, CARIES_16_TEXT)] }], findings: [CARIES_16] });

    await screen.findByRole("list", { name: "Hallazgos atendidos en esta consulta" });
    expect(screen.queryByRole("button", { name: "Descartar nota vacía" })).not.toBeInTheDocument();
  });

  it("sin hallazgos en el odontograma lo dice y la nota carga igual", async () => {
    renderScreen({ notes: [OPEN_EMPTY] });

    expect(await screen.findByText("Sin hallazgos en el odontograma.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sellar nota" })).toBeInTheDocument();
  });
});

describe("ConsultationNotesScreen — notas selladas como acordeón", () => {
  const OLDER = { ...SEALED, id: 8, attendedAt: "2026-08-01T14:00:00Z", reason: "Limpieza", examination: "Sarro leve", corrections: [] };

  it("sin nota abierta, la consulta más reciente se ve desplegada y las anteriores plegadas con su resumen", async () => {
    renderScreen({ notes: [OLDER, SEALED] });

    const recent = await screen.findByRole("button", { name: /Consulta del 1 set/ });
    expect(recent).toHaveAttribute("aria-expanded", "true");
    const older = screen.getByRole("button", { name: /Consulta del 1 ago/ });
    expect(older).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText("Limpieza")).toBeInTheDocument();
    expect(screen.queryByText("Sarro leve")).not.toBeInTheDocument();
  });

  it("con una nota abierta, las selladas arrancan plegadas y se despliegan al tocarlas", async () => {
    const user = userEvent.setup();
    renderScreen({ notes: [SEALED, OPEN_EMPTY] });

    const sealed = await screen.findByRole("button", { name: /Consulta del 1 set/ });
    expect(sealed).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Encía sana; caries 37")).not.toBeInTheDocument();

    await user.click(sealed);
    expect(sealed).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Encía sana; caries 37")).toBeInTheDocument();
  });
});

describe("ConsultationNotesScreen — qué es obligatorio en la nota abierta", () => {
  it("marca con * lo que exige el sellado y deja los opcionales plegados", async () => {
    const user = userEvent.setup();
    renderScreen({ notes: [OPEN_EMPTY] });

    for (const label of [/^Motivo de consulta/, /^Examen/, /^Tratamiento \*/]) {
      const field = await screen.findByLabelText(label);
      expect(document.querySelector(`label[for="${field.id}"]`)).toHaveTextContent("*");
    }
    expect(screen.queryByLabelText("Tiempo de enfermedad")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Más detalles \(opcional\)/ }));
    expect(screen.getByLabelText("Tiempo de enfermedad")).toBeInTheDocument();
    expect(document.querySelector('label[for="illnessDuration"]')).not.toHaveTextContent("*");
  });

  it("si un opcional ya tiene algo escrito, su sección arranca desplegada", async () => {
    renderScreen({ notes: [{ ...OPEN_EMPTY, referral: "Endodoncista" }] });

    expect(await screen.findByLabelText("Referencia")).toHaveValue("Endodoncista");
  });

  it("la barra de abajo dice qué falta y ofrece guardar y sellar", async () => {
    renderScreen({ notes: [OPEN_EMPTY] });

    const checklist = await screen.findByRole("list", { name: "Lo que falta para sellar" });
    expect(within(checklist).getAllByRole("listitem")).toHaveLength(4);
    expect(screen.getByRole("button", { name: "Guardar avance" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sellar nota" })).toBeDisabled();
  });
});
