import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import type { AppointmentResponse } from "@/modules/core/services/generated/model";
import { AppointmentsAgenda } from "./AppointmentsAgenda";

// Scenarios de "Vista semanal de la agenda", "Agendar desde un hueco libre", "Detalle de una cita" y "Agendar la
// próxima cita propuesta en una nota" (add-appointments 4.3–4.6). "Ahora" = martes 3/12/2030, 10:00 en Lima.
const NOW = new Date("2030-12-03T15:00:00Z");

const WEEK_HOURS = {
  timezone: "America/Lima",
  days: [
    { day: "MONDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "TUESDAY", segments: [{ opens: "09:00", closes: "13:00" }, { opens: "15:00", closes: "19:00" }] },
    { day: "WEDNESDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "THURSDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "FRIDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "SATURDAY", segments: [{ opens: "09:00", closes: "13:00" }] },
    { day: "SUNDAY", segments: [] },
  ],
};

const DENTISTS = [
  { userId: 7, fullName: "Dra. Ríos" },
  { userId: 8, fullName: "Dr. Soto" },
];

function appointment(overrides: Partial<AppointmentResponse>): AppointmentResponse {
  return {
    id: 1,
    patientId: 42,
    patientName: "Ana Gómez",
    dentistUserId: 7,
    dentistName: "Dra. Ríos",
    startsAt: "2030-12-03T14:00:00Z", // 09:00 en Lima: ya empezó
    endsAt: "2030-12-03T14:30:00Z",
    durationMinutes: 30,
    status: "AGENDADA",
    ...overrides,
  };
}

const STARTED = appointment({});
const FUTURE = appointment({
  id: 2,
  patientId: 43,
  patientName: "Bruno Díaz",
  dentistUserId: 8,
  dentistName: "Dr. Soto",
  startsAt: "2030-12-04T16:00:00Z", // miércoles 11:00 en Lima
  endsAt: "2030-12-04T16:30:00Z",
});

let weekRequests: URL[] = [];

function serve({ capacity = "ENABLED" }: { capacity?: string } = {}) {
  server.use(
    http.get("*/business-hours", () => HttpResponse.json(WEEK_HOURS)),
    http.get("*/appointments/dentists", () => HttpResponse.json(DENTISTS)),
    http.get("*/appointments", ({ request }) => {
      const url = new URL(request.url);
      weekRequests.push(url);
      const dentistId = url.searchParams.get("dentistId");
      const all = [STARTED, FUTURE];
      return HttpResponse.json(dentistId ? all.filter((a) => a.dentistUserId === Number(dentistId)) : all);
    }),
    http.get("*/me/professional-credential", () =>
      HttpResponse.json({ clinicalCapacity: { state: capacity } }),
    ),
    http.get("*/patients", () => HttpResponse.json({ content: [{ id: 42, fullName: "Ana Gómez" }] })),
    http.get("*/patients/42", () => HttpResponse.json({ id: 42, fullName: "Ana Gómez" })),
  );
}

function setWidth(px: number) {
  Object.defineProperty(window, "innerWidth", { configurable: true, value: px });
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
  weekRequests = [];
  setWidth(1280);
});

afterEach(() => {
  vi.useRealTimers();
  setWidth(1024);
});

function renderAgenda(path = "/appointments") {
  return renderWithProviders(
    <Routes>
      <Route path="/appointments" element={<AppointmentsAgenda />} />
      <Route path="/patients/:id/consultations" element={<p>Notas del paciente</p>} />
    </Routes>,
    { initialEntries: [path] },
  );
}

describe("AppointmentsAgenda", () => {
  it("muestra la semana con las citas de todos los odontólogos y las horas fuera del horario sombreadas", async () => {
    serve();
    renderAgenda();

    expect(await screen.findByRole("button", { name: /09:00 Ana Gómez, Dra. Ríos, Agendada/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /11:00 Bruno Díaz, Dr. Soto/ })).toBeInTheDocument();
    expect(weekRequests[0].searchParams.get("from")).toBe("2030-12-02");
    expect(weekRequests[0].searchParams.get("to")).toBe("2030-12-08");
    // Martes: 13:00–15:00 cerrado (turno partido); domingo, todo el día.
    const tuesday = screen.getByTestId("day-2030-12-03");
    expect(within(tuesday).queryByRole("button", { name: /a las 13:30/ })).not.toBeInTheDocument();
    expect(within(tuesday).getByRole("button", { name: /a las 15:00/ })).toBeInTheDocument();
    expect(within(screen.getByTestId("day-2030-12-08")).queryAllByRole("button", { name: /^Agendar/ })).toHaveLength(0);
  });

  it("muestra las horas de la clínica aunque el equipo esté en otra zona", async () => {
    serve();
    renderAgenda();
    // 14:00 UTC es 09:00 en Lima, sea cual sea la zona del navegador.
    expect(await screen.findByRole("button", { name: /^09:00 Ana Gómez/ })).toBeInTheDocument();
  });

  it("filtra por odontólogo", async () => {
    serve();
    const user = userEvent.setup();
    renderAgenda();
    await screen.findByRole("button", { name: /Bruno Díaz/ });

    await user.selectOptions(screen.getByLabelText("Filtrar por odontólogo"), "7");

    await waitFor(() => expect(screen.queryByRole("button", { name: /Bruno Díaz/ })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: /Ana Gómez/ })).toBeInTheDocument();
    expect(weekRequests.at(-1)?.searchParams.get("dentistId")).toBe("7");
  });

  it("en el celular muestra un solo día con controles para pasar al siguiente", async () => {
    setWidth(375);
    serve();
    const user = userEvent.setup();
    renderAgenda();

    expect(await screen.findAllByRole("group")).toHaveLength(1);
    expect(screen.getByTestId("day-2030-12-03")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Día siguiente" }));
    expect(await screen.findByTestId("day-2030-12-04")).toBeInTheDocument();
  });

  it("un hueco libre abre el formulario con ese día y esa hora", async () => {
    serve();
    const user = userEvent.setup();
    renderAgenda();

    await user.click(await screen.findByRole("button", { name: "Agendar el jueves, 5 de diciembre a las 10:00" }));

    const dialog = await screen.findByRole("dialog", { name: "Agendar cita" });
    expect(within(dialog).getByLabelText(/Fecha/)).toHaveValue("2030-12-05");
    expect(within(dialog).getByLabelText(/Hora/)).toHaveValue("10:00");
  });

  it("muestra el error de choque que devuelve el servidor", async () => {
    serve();
    server.use(
      http.post("*/appointments", () =>
        HttpResponse.json(
          { type: "/errors/appointment-conflict", status: 409, detail: "El odontólogo ya tiene otra cita en ese horario." },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAgenda();
    await user.click(await screen.findByRole("button", { name: "Agendar el jueves, 5 de diciembre a las 10:00" }));
    const dialog = await screen.findByRole("dialog", { name: "Agendar cita" });

    await user.click(await within(dialog).findByRole("button", { name: "Ana Gómez" }));
    await user.selectOptions(within(dialog).getByLabelText(/Odontólogo/), "7");
    await user.click(within(dialog).getByRole("button", { name: "Agendar" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("ya tiene otra cita");
  });

  it("no envía una hora fuera de la grilla de 5 minutos", async () => {
    serve();
    let posted = false;
    server.use(http.post("*/appointments", () => ((posted = true), HttpResponse.json({}))));
    const user = userEvent.setup();
    renderAgenda("/appointments?patientId=42&date=2030-12-05");
    const dialog = await screen.findByRole("dialog", { name: "Agendar cita" });

    await user.selectOptions(within(dialog).getByLabelText(/Odontólogo/), "7");
    await user.type(within(dialog).getByLabelText(/Hora/), "10:03");
    await user.click(within(dialog).getByRole("button", { name: "Agendar" }));

    expect(await within(dialog).findByText("La hora debe ser múltiplo de 5 minutos")).toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it("la próxima cita de una nota abre el formulario con paciente y fecha precargados", async () => {
    serve();
    renderAgenda("/appointments?patientId=42&date=2030-12-05");

    const dialog = await screen.findByRole("dialog", { name: "Agendar cita" });
    expect(within(dialog).getByLabelText(/Fecha/)).toHaveValue("2030-12-05");
    expect(await within(dialog).findByText("Ana Gómez")).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Hora/)).toHaveValue("");
  });

  it("sin capacidad clínica el detalle no ofrece atender con nota, pero sí marcar atendida", async () => {
    serve({ capacity: "NO_CREDENTIAL" });
    const user = userEvent.setup();
    renderAgenda();

    await user.click(await screen.findByRole("button", { name: /Ana Gómez, Dra. Ríos/ }));

    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByRole("button", { name: "Marcar atendida" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "No asistió" })).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Atender con nota" })).not.toBeInTheDocument();
  });

  it("una cita que no empezó solo ofrece reprogramar y cancelar", async () => {
    serve();
    const user = userEvent.setup();
    renderAgenda();

    await user.click(await screen.findByRole("button", { name: /Bruno Díaz/ }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "Reprogramar" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Cancelar cita" })).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Marcar atendida" })).not.toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Atender con nota" })).not.toBeInTheDocument();
  });

  it("las acciones de asistencia aparecen al llegar la hora, con el detalle abierto", async () => {
    vi.useFakeTimers({ toFake: ["Date", "setInterval", "clearInterval"] });
    vi.setSystemTime(new Date("2030-12-04T15:59:50Z")); // miércoles 10:59:50 en Lima; Bruno es a las 11:00
    serve();
    const user = userEvent.setup();
    renderAgenda();

    await user.click(await screen.findByRole("button", { name: /Bruno Díaz/ }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByRole("button", { name: "Marcar atendida" })).not.toBeInTheDocument();

    act(() => {
      vi.setSystemTime(new Date("2030-12-04T16:00:05Z"));
      vi.advanceTimersByTime(15_000);
    });

    expect(await within(dialog).findByRole("button", { name: "Marcar atendida" })).toBeInTheDocument();
  });

  it("atender con nota abre la nota y lleva a las notas del paciente", async () => {
    serve();
    let attended = false;
    server.use(
      http.post("*/appointments/1/consultation-note", () => {
        attended = true;
        return HttpResponse.json({ ...STARTED, status: "ATENDIDA", consultationNoteId: 99 });
      }),
    );
    const user = userEvent.setup();
    renderAgenda();

    await user.click(await screen.findByRole("button", { name: /Ana Gómez, Dra. Ríos/ }));
    await user.click(await screen.findByRole("button", { name: "Atender con nota" }));

    expect(await screen.findByText("Notas del paciente")).toBeInTheDocument();
    expect(attended).toBe(true);
  });
});
