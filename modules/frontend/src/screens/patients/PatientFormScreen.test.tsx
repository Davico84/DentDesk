import { describe, it, expect } from "vitest";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    await user.click(screen.getByRole("button", { name: /registrar paciente/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect((received as { fullName: string }).fullName).toBe("Ada Lovelace");
  });

  /**
   * El hueco que originó update-api-type-contracts: un 409 sin `errors[]` de campo (documento
   * duplicado) no tenía ningún aviso — `applyServerFieldErrors` no pinta nada y el hook no tenía
   * `onError`. Ahora el hook cae en el aviso genérico, mismo patrón que `useLogin`.
   */
  it("un documento duplicado (409) muestra un aviso genérico", async () => {
    server.use(
      http.post("*/patients", () =>
        HttpResponse.json(
          { type: "/errors/patient-document-taken", status: 409, detail: "El documento ya está registrado en esta clínica." },
          { status: 409 },
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

    await user.type(screen.getByLabelText(/nombre completo/i), "Ada Lovelace");
    fireEvent.change(screen.getByLabelText(/fecha de nacimiento/i), { target: { value: isoYearsAgo(30) } });
    await user.click(screen.getByRole("button", { name: /registrar paciente/i }));

    expect(await screen.findByText("El documento ya está registrado en esta clínica.")).toBeInTheDocument();
  });
});
