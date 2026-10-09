import { describe, it, expect } from "vitest";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Routes, Route } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { Toaster } from "sonner";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { PatientFormScreen } from "./PatientFormScreen";

function isoYearsAgo(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return d.toISOString().slice(0, 10);
}

describe("PatientFormScreen (alta)", () => {
  it("un menor sin responsable no se envía: el cliente exige el responsable", async () => {
    const user = userEvent.setup();
    renderWithProviders(<PatientFormScreen />, { initialEntries: ["/patients/new"] });

    await user.type(screen.getByLabelText(/nombre completo/i), "Niño Sin Tutor");
    fireEvent.change(screen.getByLabelText(/fecha de nacimiento/i), { target: { value: isoYearsAgo(8) } });
    await user.click(screen.getByRole("button", { name: /registrar paciente/i }));

    expect(await screen.findAllByText(/obligatorio para un paciente menor/i)).not.toHaveLength(0);
  });

  it("pinta el error del servidor (400) junto al campo", async () => {
    server.use(
      http.post("*/patients", () =>
        HttpResponse.json(
          { type: "/errors/validation-error", status: 400, errors: [{ field: "document", message: "Documento inválido" }] },
          { status: 400 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<PatientFormScreen />, { initialEntries: ["/patients/new"] });

    // El formulario pasa la validación del CLIENTE (DNI de 8 dígitos con su tipo): así la petición
    // llega al servidor y se comprueba que SU 400 se pinta junto al campo.
    await user.type(screen.getByLabelText(/nombre completo/i), "Ada Lovelace");
    fireEvent.change(screen.getByLabelText(/fecha de nacimiento/i), { target: { value: isoYearsAgo(30) } });
    await user.type(screen.getByLabelText("Teléfono", { exact: true }), "987654321");
    await user.selectOptions(screen.getByLabelText(/tipo de documento/i), "DNI");
    await user.type(screen.getByLabelText(/^documento$/i), "12345678");
    await user.click(screen.getByRole("button", { name: /registrar paciente/i }));

    expect(await screen.findByText("Documento inválido")).toBeInTheDocument();
  });

  it("avisa al salir del campo, pero el error se va SOLO al corregirlo (sin salir)", async () => {
    const user = userEvent.setup();
    renderWithProviders(<PatientFormScreen />, { initialEntries: ["/patients/new"] });

    await user.selectOptions(screen.getByLabelText(/tipo de documento/i), "DNI");
    const document = screen.getByLabelText(/^documento$/i);

    // Mientras escribes por primera vez NO molesta: el aviso llega al salir del campo.
    await user.type(document, "123");
    expect(screen.queryByText(/formato inválido/i)).not.toBeInTheDocument();
    await user.tab();
    expect(await screen.findByText(/formato inválido/i)).toBeInTheDocument();

    // Ya avisado: al completar los 8 dígitos el error desaparece sin salir del campo.
    await user.type(document, "45678");
    await waitFor(() => expect(screen.queryByText(/formato inválido/i)).not.toBeInTheDocument());
  });

  it("registra un alta válida", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 7, fullName: "Ada Lovelace", birthDate: isoYearsAgo(30), minor: false, status: "ACTIVE" }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <PatientFormScreen />
        <Toaster />
      </>,
      { initialEntries: ["/patients/new"] },
    );

    await user.type(screen.getByLabelText(/nombre completo/i), "Ada Lovelace");
    fireEvent.change(screen.getByLabelText(/fecha de nacimiento/i), { target: { value: isoYearsAgo(30) } });
    // Teléfono propio: sin él, update-patients D2 exigiría el contacto de emergencia y el submit
    // no llegaría al servidor — no es lo que este test quiere ejercitar.
    await user.type(screen.getByLabelText("Teléfono", { exact: true }), "987654321");
    await user.click(screen.getByRole("button", { name: /registrar paciente/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect((received as { fullName: string }).fullName).toBe("Ada Lovelace");
  });

  /**
   * El hueco que originó update-api-type-contracts: un 409 sin `errors[]` de campo (documento
   * duplicado) no tenía ningún aviso — `applyServerFieldErrors` no pinta nada y el hook no tenía
   * `onError`. Ahora el hook cae en el aviso genérico, mismo patrón que `useLogin`.
   */
  it("un documento duplicado (409) muestra un único toast accionable (update-patients D5)", async () => {
    server.use(
      http.post("*/patients", () =>
        HttpResponse.json(
          {
            type: "/errors/patient-document-taken",
            status: 409,
            detail: "El documento ya está registrado en esta clínica.",
            existingPatientId: 42,
          },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <Routes>
          <Route path="/patients/new" element={<PatientFormScreen />} />
          <Route path="/patients/:id" element={<div>Ficha del paciente existente</div>} />
        </Routes>
        <Toaster />
      </>,
      { initialEntries: ["/patients/new"] },
    );

    await user.type(screen.getByLabelText(/nombre completo/i), "Ada Lovelace");
    fireEvent.change(screen.getByLabelText(/fecha de nacimiento/i), { target: { value: isoYearsAgo(30) } });
    await user.type(screen.getByLabelText("Teléfono", { exact: true }), "987654321");
    await user.click(screen.getByRole("button", { name: /registrar paciente/i }));

    // Un solo toast: el hook se abstiene de su genérico para este errorType (D5), el componente
    // muestra el suyo con la acción de abrir la ficha existente.
    expect(await screen.findByText("El documento ya está registrado en esta clínica.")).toBeInTheDocument();
    const openExisting = await screen.findByRole("button", { name: /abrir ficha existente/i });

    // Y al elegirla, navega de verdad a la ficha existente (Scenario: abrir desde el aviso).
    await user.click(openExisting);
    expect(await screen.findByText("Ficha del paciente existente")).toBeInTheDocument();
  });

  /** update-patients D1: aviso no bloqueante — el alta se completa igual, solo informa. */
  it("un teléfono compartido no bloquea el alta y avisa (aviso no bloqueante)", async () => {
    server.use(
      http.post("*/patients", () =>
        HttpResponse.json(
          {
            id: 9,
            fullName: "Hijo",
            birthDate: isoYearsAgo(10),
            minor: true,
            status: "ACTIVE",
            phoneSharedWithOtherPatients: true,
          },
          { status: 201 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <PatientFormScreen />
        <Toaster />
      </>,
      { initialEntries: ["/patients/new"] },
    );

    await user.type(screen.getByLabelText(/nombre completo/i), "Hijo");
    fireEvent.change(screen.getByLabelText(/fecha de nacimiento/i), { target: { value: isoYearsAgo(10) } });
    await user.type(screen.getByLabelText("Teléfono", { exact: true }), "987654321");
    await user.type(screen.getByLabelText(/nombre del responsable/i), "Madre");
    // Hay dos selects de "Parentesco" (responsable y contacto de emergencia): se distingue por rol
    // y nombre accesible exacto, evitando que "Parentesco del contacto" haga match también.
    await user.selectOptions(screen.getByRole("combobox", { name: /^parentesco(?!\s*del)/i }), "MOTHER");
    await user.type(screen.getByLabelText(/^teléfono del responsable/i), "987654321");
    await user.click(screen.getByRole("button", { name: /registrar paciente/i }));

    // El alta se completa (éxito) Y además avisa — no es uno u otro.
    expect(await screen.findByText("Paciente registrado")).toBeInTheDocument();
    expect(
      await screen.findByText(/este teléfono ya está registrado a nombre de otro paciente/i),
    ).toBeInTheDocument();
  });
});

describe("PatientFormScreen (edición) — anamnesis congelada", () => {
  const PATIENT = {
    id: 1,
    fullName: "Ada Lovelace",
    birthDate: "1990-01-01",
    phone: "+51987654321",
    status: "ACTIVE",
    allergies: ["Látex"],
    conditions: [],
    currentMedication: [],
  };

  function renderEdit(patient: Record<string, unknown>) {
    server.use(http.get("*/patients/1", () => HttpResponse.json(patient)));
    return renderWithProviders(
      <Routes>
        <Route path="/patients/:id/edit" element={<PatientFormScreen />} />
        <Route path="/patients/:id" element={<p>detalle</p>} />
      </Routes>,
      { initialEntries: ["/patients/1/edit"] },
    );
  }

  it("con Historia Clínica la anamnesis se lee y no se edita, y el guardado la manda sin cambios", async () => {
    let received: { allergies?: string[] } | null = null;
    server.use(
      http.put("*/patients/1", async ({ request }) => {
        received = (await request.json()) as { allergies?: string[] };
        return HttpResponse.json({ ...PATIENT, anamnesisFrozen: true });
      }),
    );
    const user = userEvent.setup();
    renderEdit({ ...PATIENT, anamnesisFrozen: true });

    await user.click(await screen.findByRole("button", { name: /anamnesis/i }));
    expect(screen.getByText(/los antecedentes nuevos se registran en la consulta/i)).toBeInTheDocument();
    expect(screen.getByText("Látex")).toBeInTheDocument();
    expect(screen.queryByLabelText(/^alergias$/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));
    await waitFor(() => expect(received).not.toBeNull());
    expect(received!.allergies).toEqual(["Látex"]);
  });

  it("sin Historia Clínica la anamnesis sigue editable", async () => {
    const user = userEvent.setup();
    renderEdit({ ...PATIENT, anamnesisFrozen: false });

    await user.click(await screen.findByRole("button", { name: /anamnesis/i }));
    expect(screen.getByLabelText(/^alergias$/i)).toBeInTheDocument();
  });
});
